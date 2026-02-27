package com.piratereader.epub

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.piratereader.data.library.LibraryBookEntity
import com.piratereader.data.library.LibraryImportProgress
import com.piratereader.data.library.LibraryImportProgressStage
import java.io.File
import java.util.zip.ZipFile
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubImporter(
    private val appContext: Context,
) {
    suspend fun import(
        uri: Uri,
        onProgress: ((LibraryImportProgress) -> Unit)? = null,
    ): LibraryBookEntity = withContext(Dispatchers.IO) {
        val contentResolver = appContext.contentResolver
        val openable = queryOpenableMetadata(contentResolver, uri)
        val originalName = openable.displayName ?: fallbackFileName(uri)
        var localFile: File? = null
        var coverPath: String? = null

        try {
            onProgress?.invoke(LibraryImportProgress(LibraryImportProgressStage.COPYING_SOURCE, 25))
            localFile = copyToManagedStorage(
                contentResolver = contentResolver,
                sourceUri = uri,
                preferredBaseName = originalName,
            )

            onProgress?.invoke(LibraryImportProgress(LibraryImportProgressStage.PARSING_METADATA, 60))
            val metadata = when (val extraction = EpubMetadataExtractor.extractDetailed(localFile)) {
                is EpubMetadataExtractionResult.Success -> extraction.metadata
                is EpubMetadataExtractionResult.Failure ->
                    throw IllegalArgumentException(extraction.userFacingMessage())
            }

            coverPath = metadata.coverZipPath?.let { coverZipPath ->
                onProgress?.invoke(LibraryImportProgress(LibraryImportProgressStage.EXTRACTING_COVER, 80))
                extractCoverToManagedStorage(
                    epubFile = localFile,
                    coverZipPath = coverZipPath,
                    bookTitleHint = metadata.title ?: originalName,
                )
            }
            val tocEntries = metadata.tocEntries
            val now = System.currentTimeMillis()

            LibraryBookEntity(
                title = metadata.title ?: originalName.removeSuffixIgnoreCase(".epub"),
                authors = metadata.authors.joinToString(", "),
                coverPath = coverPath,
                tocEntryCount = tocEntries.size,
                tocEntriesSerialized = EpubTocCodec.encode(tocEntries),
                fileName = originalName,
                localPath = localFile.absolutePath,
                sourceUri = uri.toString(),
                fileSizeBytes = openable.sizeBytes ?: localFile.length(),
                lastReadChapterZipPath = null,
                lastReadAnchorFragment = null,
                lastReadScrollY = null,
                lastReadScrollX = null,
                lastReadPageMode = null,
                lastReadLocatorSerialized = null,
                format = "EPUB",
                addedAt = now,
                lastOpenedAt = now,
            )
        } catch (t: Throwable) {
            if (t is CancellationException) {
                cleanupFailedImport(localFile, coverPath)
                throw t
            }
            cleanupFailedImport(localFile, coverPath)
            throw t
        }
    }

    private fun copyToManagedStorage(
        contentResolver: ContentResolver,
        sourceUri: Uri,
        preferredBaseName: String,
    ): File {
        val booksDir = File(appContext.filesDir, "library/epub").apply { mkdirs() }
        val fileName = buildManagedFileName(preferredBaseName)
        val target = File(booksDir, fileName)

        contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open input stream for $sourceUri")

        return target
    }

    private fun extractCoverToManagedStorage(
        epubFile: File,
        coverZipPath: String,
        bookTitleHint: String,
    ): String? {
        val coversDir = File(appContext.filesDir, "library/covers").apply { mkdirs() }
        val extension = coverZipPath.substringAfterLast('.', missingDelimiterValue = "img")
            .lowercase(Locale.US)
            .takeIf { it.length in 2..5 } ?: "img"
        val baseName = bookTitleHint
            .removeSuffixIgnoreCase(".epub")
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "cover" }
            .take(48)
        val outFile = File(coversDir, "${baseName}_${UUID.randomUUID()}.$extension")

        return runCatching {
            ZipFile(epubFile).use { zip ->
                val entry = zip.getEntry(coverZipPath) ?: return null
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outFile.absolutePath
        }.getOrNull()
    }

    private fun buildManagedFileName(preferredBaseName: String): String {
        val ext = if (preferredBaseName.lowercase(Locale.US).endsWith(".epub")) {
            ".epub"
        } else {
            ".epub"
        }
        val safeStem = preferredBaseName
            .removeSuffixIgnoreCase(".epub")
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "book" }
            .take(48)
        return "${safeStem}_${UUID.randomUUID()}$ext"
    }

    private fun fallbackFileName(uri: Uri): String {
        val segment = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
        return when {
            segment == null -> "imported.epub"
            segment.lowercase(Locale.US).endsWith(".epub") -> segment
            else -> "$segment.epub"
        }
    }

    private fun queryOpenableMetadata(contentResolver: ContentResolver, uri: Uri): OpenableMetadata =
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                OpenableMetadata(displayName = displayName, sizeBytes = size)
            }
        }.getOrNull() ?: OpenableMetadata(displayName = null, sizeBytes = null)

    data class OpenableMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )

    private fun cleanupFailedImport(localFile: File?, coverPath: String?) {
        coverPath?.let { path ->
            runCatching { File(path).delete() }
        }
        localFile?.let { file ->
            runCatching { file.delete() }
        }
    }
}

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

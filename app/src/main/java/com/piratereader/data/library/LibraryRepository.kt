package com.piratereader.data.library

import android.net.Uri
import com.piratereader.epub.EpubImporter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val dao: LibraryBookDao,
    private val epubImporter: EpubImporter,
) {
    fun observeBooks(): Flow<List<LibraryBook>> =
        dao.observeAll().map { entities -> entities.map(LibraryBookEntity::toDomain) }

    suspend fun importEpub(
        uri: Uri,
        onProgress: ((LibraryImportProgress) -> Unit)? = null,
    ): LibraryImportResult {
        onProgress?.invoke(
            LibraryImportProgress(
                stage = LibraryImportProgressStage.PREPARING,
                percent = 5,
            ),
        )
        withContext(Dispatchers.IO) {
            dao.findBySourceUri(uri.toString())
        }?.let { existing ->
            return LibraryImportResult.AlreadyImported(existing.toDomain())
        }

        val entity = withContext(Dispatchers.IO) {
            epubImporter.import(uri, onProgress = onProgress)
        }
        onProgress?.invoke(
            LibraryImportProgress(
                stage = LibraryImportProgressStage.SAVING_LIBRARY,
                percent = 95,
            ),
        )
        val rowId = withContext(Dispatchers.IO) {
            dao.insert(entity)
        }
        val imported = entity.copy(id = if (entity.id == 0L) rowId else entity.id).toDomain()
        return LibraryImportResult.Imported(imported)
    }

    suspend fun markBookOpened(bookId: Long, openedAt: Long = System.currentTimeMillis()): LibraryBook? {
        return withContext(Dispatchers.IO) {
            dao.updateLastOpenedAt(bookId = bookId, openedAt = openedAt)
            dao.findById(bookId)?.toDomain()
        }
    }

    suspend fun saveReaderPosition(
        bookId: Long,
        chapterZipPath: String?,
        anchorFragment: String?,
        scrollX: Int?,
        scrollY: Int?,
        pageMode: String?,
        locatorSerialized: String?,
    ): LibraryBook? {
        val normalizedPageMode = pageMode
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf { it == "SCROLL" || it == "PAGINATED" }
        return withContext(Dispatchers.IO) {
            dao.updateReaderPosition(
                bookId = bookId,
                chapterZipPath = chapterZipPath,
                anchorFragment = anchorFragment?.trim()?.ifBlank { null },
                scrollX = scrollX?.coerceAtLeast(0),
                scrollY = scrollY?.coerceAtLeast(0),
                pageMode = normalizedPageMode,
                locatorSerialized = locatorSerialized?.trim()?.ifBlank { null },
            )
            dao.findById(bookId)?.toDomain()
        }
    }
}

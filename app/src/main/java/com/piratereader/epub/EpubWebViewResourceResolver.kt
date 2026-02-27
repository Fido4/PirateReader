package com.piratereader.epub

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

data class EpubWebResource(
    val zipPath: String,
    val mimeType: String,
    val encoding: String?,
    val data: ByteArray,
)

object EpubWebViewResourceResolver {
    const val READER_HOST: String = "reader.piratereader.local"

    fun chapterBaseUrl(chapterZipPath: String): String =
        "https://$READER_HOST/${chapterZipPath.trimStart('/')}"

    fun resolveRequestZipPath(
        chapterZipPath: String,
        requestUrl: String,
    ): String? {
        val uri = runCatching { URI(requestUrl) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (!host.equals(READER_HOST, ignoreCase = true)) return null

        val rawPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
        val baseDir = chapterZipPath.substringBeforeLast('/', missingDelimiterValue = "")
        val candidate = if (rawPath.startsWith("/")) {
            rawPath.removePrefix("/")
        } else {
            if (baseDir.isBlank()) rawPath else "$baseDir/$rawPath"
        }

        return normalizeZipPath(candidate)
    }

    fun loadResource(
        epubFile: File,
        chapterZipPath: String,
        requestUrl: String,
    ): EpubWebResource? {
        val zipPath = resolveRequestZipPath(chapterZipPath = chapterZipPath, requestUrl = requestUrl) ?: return null
        if (!epubFile.exists() || !epubFile.isFile) return null

        return runCatching {
            ZipFile(epubFile).use { zip ->
                val entry = zip.getEntry(zipPath) ?: return null
                val bytes = zip.getInputStream(entry).use(InputStream::readBytes)
                val mimeType = guessMimeType(zipPath)
                EpubWebResource(
                    zipPath = zipPath,
                    mimeType = mimeType,
                    encoding = textEncodingForMimeType(mimeType),
                    data = bytes,
                )
            }
        }.getOrNull()
    }

    fun toInputStream(resource: EpubWebResource): InputStream = ByteArrayInputStream(resource.data)

    private fun normalizeZipPath(path: String): String? {
        val segments = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> {
                    if (segments.isEmpty()) return null
                    segments.removeAt(segments.lastIndex)
                }

                else -> segments += segment
            }
        }
        return segments.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    private fun textEncodingForMimeType(mimeType: String): String? =
        when {
            mimeType.startsWith("text/") -> StandardCharsets.UTF_8.name()
            mimeType == "application/xhtml+xml" -> StandardCharsets.UTF_8.name()
            mimeType == "application/xml" -> StandardCharsets.UTF_8.name()
            mimeType == "image/svg+xml" -> StandardCharsets.UTF_8.name()
            else -> null
        }

    private fun guessMimeType(zipPath: String): String {
        val ext = zipPath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "xhtml", "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "xml", "opf", "ncx" -> "application/xml"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }
}

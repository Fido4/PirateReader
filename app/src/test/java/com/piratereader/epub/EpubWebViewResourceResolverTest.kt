package com.piratereader.epub

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class EpubWebViewResourceResolverTest {
    @Test
    fun resolveRequestZipPath_resolvesRelativeAndRootRelativePaths() {
        val chapter = "OEBPS/chapters/one.xhtml"
        val relative = "${EpubWebViewResourceResolver.chapterBaseUrl(chapter.substringBeforeLast('/') + "/style.css")}"
        val rootRelative = "https://${EpubWebViewResourceResolver.READER_HOST}/OEBPS/images/cover.jpg"

        val relativeResolved = EpubWebViewResourceResolver.resolveRequestZipPath(chapter, relative)
        val rootResolved = EpubWebViewResourceResolver.resolveRequestZipPath(chapter, rootRelative)

        assertThat(relativeResolved).isEqualTo("OEBPS/chapters/style.css")
        assertThat(rootResolved).isEqualTo("OEBPS/images/cover.jpg")
    }

    @Test
    fun resolveRequestZipPath_blocksHostMismatchAndTraversalOutsideArchive() {
        val chapter = "OEBPS/chapters/one.xhtml"

        val wrongHost = EpubWebViewResourceResolver.resolveRequestZipPath(
            chapter,
            "https://example.com/OEBPS/chapters/style.css",
        )
        val traversal = EpubWebViewResourceResolver.resolveRequestZipPath(
            chapter,
            "https://${EpubWebViewResourceResolver.READER_HOST}/../../../secret.css",
        )

        assertThat(wrongHost).isNull()
        assertThat(traversal).isNull()
    }

    @Test
    fun loadResource_readsZipEntryAndInfersMimeType() {
        val epub = createTempZip(
            mapOf(
                "OEBPS/chapters/one.xhtml" to "<html><body><img src=\"../images/pic.png\" /></body></html>".toByteArray(),
                "OEBPS/images/pic.png" to byteArrayOf(1, 2, 3),
                "OEBPS/styles/book.css" to "body { color: black; }".toByteArray(),
            ),
        )

        val image = EpubWebViewResourceResolver.loadResource(
            epubFile = epub,
            chapterZipPath = "OEBPS/chapters/one.xhtml",
            requestUrl = "https://${EpubWebViewResourceResolver.READER_HOST}/OEBPS/images/pic.png",
        )
        val css = EpubWebViewResourceResolver.loadResource(
            epubFile = epub,
            chapterZipPath = "OEBPS/chapters/one.xhtml",
            requestUrl = "https://${EpubWebViewResourceResolver.READER_HOST}/OEBPS/styles/book.css",
        )

        assertThat(image).isNotNull()
        assertThat(image?.mimeType).isEqualTo("image/png")
        assertThat(image?.encoding).isNull()
        assertThat(image?.data).isEqualTo(byteArrayOf(1, 2, 3))

        assertThat(css).isNotNull()
        assertThat(css?.mimeType).isEqualTo("text/css")
        assertThat(css?.encoding).isEqualTo("UTF-8")
        assertThat(css?.data?.decodeToString()).contains("color: black")
    }

    private fun createTempZip(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("epub-web-resource", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}

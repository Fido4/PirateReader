package com.piratereader.epub

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class EpubSpineDocumentLoaderTest {
    @Test
    fun loadFirstSpineDocument_readsFirstSpineMarkup() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Reader Test</dc:title>
                  </metadata>
                  <manifest>
                    <item id="c1" href="chapters/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapters/two.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            extraTextEntries = mapOf(
                "OEBPS/chapters/one.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><h1>Chapter One</h1><p>Hello EPUB.</p></body>
                    </html>
                """.trimIndent(),
                "OEBPS/chapters/two.xhtml" to "<html><body>Second</body></html>",
            ),
        )

        val result = EpubSpineDocumentLoader.loadFirstSpineDocument(epubFile)

        assertThat(result).isInstanceOf(EpubSpineDocumentLoadResult.Success::class.java)
        val success = result as EpubSpineDocumentLoadResult.Success
        assertThat(success.document.packageTitle).isEqualTo("Reader Test")
        assertThat(success.document.chapterZipPath).isEqualTo("OEBPS/chapters/one.xhtml")
        assertThat(success.document.spineItemCount).isEqualTo(2)
        assertThat(success.document.previousChapterZipPath).isNull()
        assertThat(success.document.nextChapterZipPath).isEqualTo("OEBPS/chapters/two.xhtml")
        assertThat(success.document.chapterMarkup).contains("Chapter One")
    }

    @Test
    fun loadFirstSpineDocument_reportsEmptySpine() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Empty Spine</dc:title>
                  </metadata>
                  <manifest />
                  <spine />
                </package>
            """.trimIndent(),
        )

        val result = EpubSpineDocumentLoader.loadFirstSpineDocument(epubFile)

        assertThat(result).isInstanceOf(EpubSpineDocumentLoadResult.Failure::class.java)
        val failure = result as EpubSpineDocumentLoadResult.Failure
        assertThat(failure.reason).isEqualTo(EpubSpineDocumentFailureReason.EMPTY_SPINE)
    }

    @Test
    fun loadFirstSpineDocument_reportsMissingSpineDocumentEntry() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Missing Chapter File</dc:title>
                  </metadata>
                  <manifest>
                    <item id="c1" href="chapters/missing.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                  </spine>
                </package>
            """.trimIndent(),
        )

        val result = EpubSpineDocumentLoader.loadFirstSpineDocument(epubFile)

        assertThat(result).isInstanceOf(EpubSpineDocumentLoadResult.Failure::class.java)
        val failure = result as EpubSpineDocumentLoadResult.Failure
        assertThat(failure.reason).isEqualTo(EpubSpineDocumentFailureReason.MISSING_SPINE_DOCUMENT_ENTRY)
    }

    @Test
    fun loadSpineDocumentForReading_usesPreferredChapterWhenPresent() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Resume Test</dc:title>
                  </metadata>
                  <manifest>
                    <item id="c1" href="./chapters/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapters/two.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            extraTextEntries = mapOf(
                "OEBPS/chapters/one.xhtml" to "<html><body>One</body></html>",
                "OEBPS/chapters/two.xhtml" to "<html><body>Two</body></html>",
            ),
        )

        val result = EpubSpineDocumentLoader.loadSpineDocumentForReading(
            epubFile = epubFile,
            preferredChapterZipPath = "OEBPS/chapters/two.xhtml",
        )

        assertThat(result).isInstanceOf(EpubSpineDocumentLoadResult.Success::class.java)
        val success = result as EpubSpineDocumentLoadResult.Success
        assertThat(success.document.chapterZipPath).isEqualTo("OEBPS/chapters/two.xhtml")
        assertThat(success.document.spineIndex).isEqualTo(1)
        assertThat(success.document.previousChapterZipPath).isEqualTo("OEBPS/chapters/one.xhtml")
        assertThat(success.document.nextChapterZipPath).isNull()
        assertThat(success.document.chapterMarkup).contains("Two")
    }

    @Test
    fun loadSpineDocumentForReading_skipsUnsupportedAdjacentItemsWhenComputingChapterNavigation() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <manifest>
                    <item id="c1" href="chapters/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img1" href="images/cover.png" media-type="image/png"/>
                    <item id="c2" href="chapters/two.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="img1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            extraTextEntries = mapOf(
                "OEBPS/chapters/one.xhtml" to "<html><body>One</body></html>",
                "OEBPS/chapters/two.xhtml" to "<html><body>Two</body></html>",
                "OEBPS/images/cover.png" to "not-a-real-png",
            ),
        )

        val result = EpubSpineDocumentLoader.loadSpineDocumentForReading(
            epubFile = epubFile,
            preferredChapterZipPath = "OEBPS/chapters/one.xhtml",
        )

        assertThat(result).isInstanceOf(EpubSpineDocumentLoadResult.Success::class.java)
        val success = result as EpubSpineDocumentLoadResult.Success
        assertThat(success.document.chapterZipPath).isEqualTo("OEBPS/chapters/one.xhtml")
        assertThat(success.document.nextChapterZipPath).isEqualTo("OEBPS/chapters/two.xhtml")
    }

    private fun createTempEpub(
        containerXml: String = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent(),
        opfXml: String,
        extraTextEntries: Map<String, String> = emptyMap(),
    ): File {
        val file = File.createTempFile("spine-loader-test", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(containerXml.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opfXml.toByteArray())
            zip.closeEntry()

            extraTextEntries.forEach { (path, contents) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}

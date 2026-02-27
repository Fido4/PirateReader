package com.piratereader.epub

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class EpubMetadataExtractorTest {
    @Test
    fun extract_readsTitleAndAuthorFromOpf() {
        val epubFile = createTempEpub(
            containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent(),
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Treasure Island</dc:title>
                    <dc:creator>Robert Louis Stevenson</dc:creator>
                  </metadata>
                  <manifest />
                  <spine />
                </package>
            """.trimIndent(),
        )

        val metadata = EpubMetadataExtractor.extract(epubFile)

        assertThat(metadata).isNotNull()
        assertThat(metadata?.title).isEqualTo("Treasure Island")
        assertThat(metadata?.authors).containsExactly("Robert Louis Stevenson")
        assertThat(metadata?.coverZipPath).isNull()
        assertThat(metadata?.tocEntries).isEmpty()
    }

    @Test
    fun extract_findsCoverImageUsingManifestCoverImageProperty() {
        val epubFile = createTempEpub(
            containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent(),
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Cover Test</dc:title>
                  </metadata>
                  <manifest>
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                  </manifest>
                  <spine />
                </package>
            """.trimIndent(),
        )

        val metadata = EpubMetadataExtractor.extract(epubFile)

        assertThat(metadata).isNotNull()
        assertThat(metadata?.coverZipPath).isEqualTo("OEBPS/images/cover.jpg")
    }

    @Test
    fun extract_parsesNavTocEntriesFromEpub3NavDocument() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Nav TOC Test</dc:title>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
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
                "OEBPS/nav.xhtml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                      <body>
                        <nav epub:type="toc">
                          <ol>
                            <li>
                              <a href="chapters/one.xhtml">Chapter 1</a>
                              <ol>
                                <li><a href="chapters/one.xhtml#s1">Section 1</a></li>
                              </ol>
                            </li>
                            <li><a href="chapters/two.xhtml">Chapter 2</a></li>
                          </ol>
                        </nav>
                      </body>
                    </html>
                """.trimIndent(),
            ),
        )

        val metadata = EpubMetadataExtractor.extract(epubFile)

        assertThat(metadata).isNotNull()
        assertThat(metadata?.tocEntries?.map { it.label })
            .containsExactly("Chapter 1", "Section 1", "Chapter 2")
            .inOrder()
        assertThat(metadata?.tocEntries?.map { it.depth })
            .containsExactly(0, 1, 0)
            .inOrder()
        assertThat(metadata?.tocEntries?.map { it.hrefZipPath })
            .containsExactly(
                "OEBPS/chapters/one.xhtml",
                "OEBPS/chapters/one.xhtml#s1",
                "OEBPS/chapters/two.xhtml",
            )
            .inOrder()
    }

    @Test
    fun extract_parsesNcxTocEntriesWhenNavIsAbsent() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="2.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>NCX TOC Test</dc:title>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="c1"/>
                  </spine>
                </package>
            """.trimIndent(),
            extraTextEntries = mapOf(
                "OEBPS/toc.ncx" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                      <navMap>
                        <navPoint id="n1">
                          <navLabel><text>Start</text></navLabel>
                          <content src="one.xhtml"/>
                          <navPoint id="n1_1">
                            <navLabel><text>Subsection</text></navLabel>
                            <content src="one.xhtml#sub"/>
                          </navPoint>
                        </navPoint>
                      </navMap>
                    </ncx>
                """.trimIndent(),
            ),
        )

        val metadata = EpubMetadataExtractor.extract(epubFile)

        assertThat(metadata).isNotNull()
        assertThat(metadata?.tocEntries?.map { it.label })
            .containsExactly("Start", "Subsection")
            .inOrder()
        assertThat(metadata?.tocEntries?.map { it.depth })
            .containsExactly(0, 1)
            .inOrder()
        assertThat(metadata?.tocEntries?.map { it.hrefZipPath })
            .containsExactly("OEBPS/one.xhtml", "OEBPS/one.xhtml#sub")
            .inOrder()
    }

    @Test
    fun extract_returnsNullForNonEpubZip() {
        val file = File.createTempFile("not-epub", ".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("random.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }

        val metadata = EpubMetadataExtractor.extract(file)

        assertThat(metadata).isNull()
    }

    @Test
    fun extractDetailed_reportsMissingContainerXml() {
        val file = File.createTempFile("missing-container", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
        }

        val result = EpubMetadataExtractor.extractDetailed(file)

        assertThat(result).isInstanceOf(EpubMetadataExtractionResult.Failure::class.java)
        val failure = result as EpubMetadataExtractionResult.Failure
        assertThat(failure.reason).isEqualTo(EpubMetadataFailureReason.MISSING_CONTAINER_XML)
    }

    @Test
    fun extractDetailed_reportsMissingMimetypeEntry() {
        val epubFile = createTempEpub(
            includeMimetypeEntry = false,
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>No Mimetype</dc:title>
                  </metadata>
                  <manifest />
                  <spine />
                </package>
            """.trimIndent(),
        )

        val result = EpubMetadataExtractor.extractDetailed(epubFile)

        assertThat(result).isInstanceOf(EpubMetadataExtractionResult.Failure::class.java)
        val failure = result as EpubMetadataExtractionResult.Failure
        assertThat(failure.reason).isEqualTo(EpubMetadataFailureReason.MISSING_MIMETYPE_ENTRY)
    }

    @Test
    fun extractDetailed_reportsInvalidMimetypeEntry() {
        val epubFile = createTempEpub(
            mimetypeContents = "application/zip",
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Bad Mimetype</dc:title>
                  </metadata>
                  <manifest />
                  <spine />
                </package>
            """.trimIndent(),
        )

        val result = EpubMetadataExtractor.extractDetailed(epubFile)

        assertThat(result).isInstanceOf(EpubMetadataExtractionResult.Failure::class.java)
        val failure = result as EpubMetadataExtractionResult.Failure
        assertThat(failure.reason).isEqualTo(EpubMetadataFailureReason.INVALID_MIMETYPE_ENTRY)
    }

    @Test
    fun extractDetailed_reportsMalformedContainerXml() {
        val epubFile = createTempEpub(
            containerXml = "<container><rootfiles>",
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Broken Container</dc:title>
                  </metadata>
                  <manifest />
                  <spine />
                </package>
            """.trimIndent(),
        )

        val result = EpubMetadataExtractor.extractDetailed(epubFile)

        assertThat(result).isInstanceOf(EpubMetadataExtractionResult.Failure::class.java)
        val failure = result as EpubMetadataExtractionResult.Failure
        assertThat(failure.reason).isEqualTo(EpubMetadataFailureReason.INVALID_CONTAINER_XML)
    }

    @Test
    fun extractDetailed_reportsMissingPackageDocumentFromContainerPath() {
        val epubFile = createTempEpub(
            containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/missing.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent(),
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Missing OPF</dc:title>
                  </metadata>
                  <manifest />
                  <spine />
                </package>
            """.trimIndent(),
        )

        val result = EpubMetadataExtractor.extractDetailed(epubFile)

        assertThat(result).isInstanceOf(EpubMetadataExtractionResult.Failure::class.java)
        val failure = result as EpubMetadataExtractionResult.Failure
        assertThat(failure.reason).isEqualTo(EpubMetadataFailureReason.MISSING_PACKAGE_DOCUMENT)
    }

    @Test
    fun extract_ignoresMalformedTocDocumentAndStillReturnsMetadata() {
        val epubFile = createTempEpub(
            opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>TOC Fallback</dc:title>
                    <dc:creator>Example Author</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                  </manifest>
                  <spine />
                </package>
            """.trimIndent(),
            extraTextEntries = mapOf(
                "OEBPS/nav.xhtml" to "<html><body><nav epub:type=\"toc\"><ol><li>",
            ),
        )

        val metadata = EpubMetadataExtractor.extract(epubFile)

        assertThat(metadata).isNotNull()
        assertThat(metadata?.title).isEqualTo("TOC Fallback")
        assertThat(metadata?.authors).containsExactly("Example Author")
        assertThat(metadata?.tocEntries).isEmpty()
    }

    private fun createTempEpub(
        includeMimetypeEntry: Boolean = true,
        mimetypeContents: String = "application/epub+zip",
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
        val file = File.createTempFile("test-book", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            if (includeMimetypeEntry) {
                zip.putNextEntry(ZipEntry("mimetype"))
                zip.write(mimetypeContents.toByteArray())
                zip.closeEntry()
            }

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

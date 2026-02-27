package com.piratereader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.piratereader.data.library.LibraryBook
import com.piratereader.data.library.LibraryBookEntity
import com.piratereader.data.library.PirateReaderDatabase
import com.piratereader.ui.reader.ReaderPageMode
import com.piratereader.ui.reader.ReaderPositionLocator
import com.piratereader.ui.reader.ReaderPositionLocatorCodec
import com.piratereader.ui.library.LibraryViewModel
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        val context = composeRule.activity.applicationContext
        runBlocking {
            withContext(Dispatchers.IO) {
                PirateReaderDatabase.getInstance(context).clearAllTables()
                File(context.filesDir, "library").deleteRecursively()
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun homeScreen_showsEpubFirstScopeAndFontTargets() {
        composeRule.onNodeWithText("PirateReader").assertIsDisplayed()
        composeRule.onNodeWithText("EPUB-first Android reader").assertIsDisplayed()
        composeRule.onNodeWithText("Recently Opened", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("All EPUBs", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No EPUBs imported yet").assertIsDisplayed()
        composeRule.onNodeWithText("Import EPUB").assertIsDisplayed()
    }

    @Test
    fun readerResumeFlow_savesPositionAndReopensWithResumeTarget() {
        val seeded = seedSingleChapterEpubBook(title = "Instrumentation Reader")
        seedOfflineDictionary()

        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Instrumentation Reader").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Instrumentation Reader").assertIsDisplayed()

        composeRule.onNodeWithText("Resume").performClick()
        waitForActiveReaderBook(timeoutMillis = 12_000)
        composeRule.onNodeWithText("EPUB Reader (Phase 3 Polish)").assertIsDisplayed()
        composeRule.onNodeWithText("TOC").performClick()
        composeRule.onNodeWithText("Table of Contents").assertIsDisplayed()
        composeRule.onNodeWithText("Chapter 1", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hide TOC").assertIsDisplayed()
        composeRule.onNodeWithText("Offline dictionary: dictionary.tsv", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("E-Ink").assertIsDisplayed()
        composeRule.onNodeWithText("Reset Type").assertIsDisplayed()
        composeRule.onNodeWithText("Text: 18px").performClick()
        composeRule.onNodeWithText("Text: 21px").assertIsDisplayed()
        composeRule.onNodeWithText("Reset Type").performClick()
        composeRule.onNodeWithText("Text: 18px").assertIsDisplayed()
        composeRule.onNodeWithText("E-Ink").performClick()
        composeRule.onNodeWithText("Text: 21px").assertIsDisplayed()
        composeRule.onNodeWithText("Line: 1.8").assertIsDisplayed()
        composeRule.onNodeWithText("Margins: 32").assertIsDisplayed()
        composeRule.onNodeWithText("Align: Justify").assertIsDisplayed()
        composeRule.onNodeWithText("Contrast: Balanced").assertIsDisplayed()
        composeRule.onNodeWithText("Contrast: Balanced").performClick()
        composeRule.onNodeWithText("Contrast: High").assertIsDisplayed()

        val savedLocator = ReaderPositionLocatorCodec.encode(
            ReaderPositionLocator(
                chapterZipPath = "OEBPS/ch1.xhtml",
                anchorFragment = "anchor-a",
                pageMode = ReaderPageMode.PAGINATED,
                scrollX = 1080,
                scrollY = 0,
                maxScrollX = 3240,
                maxScrollY = 1400,
                pageIndex = 2,
                pageCount = 4,
            ),
        )
        composeRule.activity.runOnUiThread {
            currentLibraryViewModel().saveReaderPositionAndClose(
                bookId = seeded.bookId,
                chapterZipPath = "OEBPS/ch1.xhtml",
                anchorFragment = "anchor-a",
                scrollX = 1080,
                scrollY = 0,
                pageMode = "PAGINATED",
                locatorSerialized = savedLocator,
            )
        }

        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Saved reader position:", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Saved reader position: Instrumentation Reader").assertIsDisplayed()

        val savedEntity = loadBookEntity(seeded.bookId)
        assertNotNull(savedEntity)
        val savedEntityNonNull = savedEntity!!
        assertEquals("OEBPS/ch1.xhtml", savedEntityNonNull.lastReadChapterZipPath)
        assertEquals("anchor-a", savedEntityNonNull.lastReadAnchorFragment)
        assertEquals(1080, savedEntityNonNull.lastReadScrollX)
        assertNotNull(savedEntityNonNull.lastReadScrollY)
        assertTrue(savedEntityNonNull.lastReadScrollY!! >= 0)
        assertEquals("PAGINATED", savedEntityNonNull.lastReadPageMode)
        assertEquals(savedLocator, savedEntityNonNull.lastReadLocatorSerialized)

        composeRule.onNodeWithText("Resume").performClick()
        val reopenedBook = waitForActiveReaderBook(timeoutMillis = 12_000)
        assertEquals("OEBPS/ch1.xhtml", reopenedBook.lastReadChapterZipPath)
        assertEquals("anchor-a", reopenedBook.lastReadAnchorFragment)
        assertEquals(1080, reopenedBook.lastReadScrollX)
        assertNotNull(reopenedBook.lastReadScrollY)
        assertTrue(reopenedBook.lastReadScrollY!! >= 0)
        assertEquals("PAGINATED", reopenedBook.lastReadPageMode)
        assertEquals(savedLocator, reopenedBook.lastReadLocatorSerialized)

        composeRule.activity.runOnUiThread {
            currentLibraryViewModel().closeReader()
        }
    }

    private fun seedSingleChapterEpubBook(title: String): SeededBook {
        val context = composeRule.activity.applicationContext
        val now = System.currentTimeMillis()
        val epubFile = File(context.filesDir, "library/epub/instrumentation_resume_test.epub").apply {
            parentFile?.mkdirs()
        }
        writeSingleChapterEpub(epubFile, title = title)

        val rowId = runBlocking {
            withContext(Dispatchers.IO) {
                PirateReaderDatabase.getInstance(context)
                    .libraryBookDao()
                    .insert(
                        LibraryBookEntity(
                            title = title,
                            authors = "Test Author",
                            coverPath = null,
                            tocEntryCount = 1,
                            tocEntriesSerialized = com.piratereader.epub.EpubTocCodec.encode(
                                listOf(
                                    com.piratereader.epub.EpubTocEntry(
                                        label = "Chapter 1",
                                        hrefZipPath = "OEBPS/ch1.xhtml",
                                        depth = 0,
                                    ),
                                ),
                            ),
                            fileName = epubFile.name,
                            localPath = epubFile.absolutePath,
                            sourceUri = null,
                            fileSizeBytes = epubFile.length(),
                            lastReadChapterZipPath = null,
                            lastReadAnchorFragment = null,
                            lastReadScrollY = null,
                            lastReadScrollX = null,
                            lastReadPageMode = null,
                            lastReadLocatorSerialized = null,
                            format = "EPUB",
                            addedAt = now,
                            lastOpenedAt = now,
                        ),
                    )
            }
        }
        composeRule.waitForIdle()
        return SeededBook(bookId = rowId)
    }

    private fun seedOfflineDictionary() {
        val context = composeRule.activity.applicationContext
        val dictionaryFile = File(context.filesDir, "library/dictionary.tsv").apply {
            parentFile?.mkdirs()
        }
        dictionaryFile.writeText(
            """
            chapter	A main division of a book.
            anchor	A marker for navigation.
            """.trimIndent(),
        )
    }

    private fun loadBookEntity(bookId: Long): LibraryBookEntity? {
        val context = composeRule.activity.applicationContext
        return runBlocking {
            withContext(Dispatchers.IO) {
                PirateReaderDatabase.getInstance(context).libraryBookDao().findById(bookId)
            }
        }
    }

    private fun waitForActiveReaderBook(timeoutMillis: Long): LibraryBook {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val book = currentLibraryViewModel().uiState.value.activeReaderBook
            if (book != null) return book
            Thread.sleep(100)
        }
        error("Timed out waiting for activeReaderBook")
    }

    private fun currentLibraryViewModel(): LibraryViewModel {
        val activity = composeRule.activity
        val delegateField = activity.javaClass.declaredFields
            .firstOrNull { it.name.contains("libraryViewModel") }
            ?: error("libraryViewModel delegate field not found")
        delegateField.isAccessible = true
        val delegate = delegateField.get(activity) as? Lazy<*>
            ?: error("libraryViewModel delegate was not Lazy")
        return delegate.value as? LibraryViewModel
            ?: error("Failed to resolve LibraryViewModel from delegate")
    }

    private fun writeSingleChapterEpub(
        file: File,
        title: String,
    ) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>${escapeXml(title)}</dc:title>
                    <dc:creator>Test Author</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                  </spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zip.write(
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <h1>${escapeXml(title)}</h1>
                    <p>Instrumentation test chapter.</p>
                    <p id="anchor-a">Anchor A</p>
                  </body>
                </html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class SeededBook(
        val bookId: Long,
    )
}

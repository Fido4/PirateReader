package com.piratereader.ui.library

import com.google.common.truth.Truth.assertThat
import com.piratereader.data.library.LibraryBook
import com.piratereader.data.library.LibraryImportProgress
import com.piratereader.data.library.LibraryImportProgressStage
import com.piratereader.data.library.LibraryImportResult
import org.junit.Test

class LibraryStatusMessageFactoryTest {
    @Test
    fun importCompleted_formatsImportedAndDuplicateMessages() {
        val book = sampleBook(title = "Sample Book")

        val imported = LibraryStatusMessageFactory.importCompleted(LibraryImportResult.Imported(book))
        val duplicate = LibraryStatusMessageFactory.importCompleted(LibraryImportResult.AlreadyImported(book))

        assertThat(imported.kind).isEqualTo(LibraryStatusKind.SUCCESS)
        assertThat(imported.message).isEqualTo("Imported: Sample Book")
        assertThat(imported.action).isNull()

        assertThat(duplicate.kind).isEqualTo(LibraryStatusKind.WARNING)
        assertThat(duplicate.message).isEqualTo("Already in library: Sample Book")
        assertThat(duplicate.action).isEqualTo(
            LibraryStatusAction(
                label = "Show Existing",
                bookId = book.id,
            ),
        )
    }

    @Test
    fun importFailed_formatsInvalidEpubAsUserFacingError() {
        val status = LibraryStatusMessageFactory.importFailed(
            IllegalArgumentException("Invalid or corrupt EPUB file"),
        )

        assertThat(status.kind).isEqualTo(LibraryStatusKind.ERROR)
        assertThat(status.message).isEqualTo("Import failed: Invalid or corrupt EPUB file")
        assertThat(status.action).isNull()
    }

    @Test
    fun resumeMarkedOpened_formatsSuccessAndMissingCases() {
        val success = LibraryStatusMessageFactory.resumeMarkedOpened(sampleBook(title = "Treasure"))
        val missing = LibraryStatusMessageFactory.resumeMarkedOpened(null)

        assertThat(success.kind).isEqualTo(LibraryStatusKind.INFO)
        assertThat(success.message).isEqualTo("Opened reader: Treasure")
        assertThat(success.action).isNull()
        assertThat(missing.kind).isEqualTo(LibraryStatusKind.ERROR)
        assertThat(missing.message).isEqualTo("Resume failed: book not found")
        assertThat(missing.action).isNull()
    }

    @Test
    fun importing_formatsStagedProgressMessage() {
        val status = LibraryStatusMessageFactory.importing(
            LibraryImportProgress(
                stage = LibraryImportProgressStage.PARSING_METADATA,
                percent = 61,
            ),
        )

        assertThat(status.kind).isEqualTo(LibraryStatusKind.INFO)
        assertThat(status.message).isEqualTo("Parsing EPUB metadata (61%)")
        assertThat(status.action).isNull()
    }

    private fun sampleBook(title: String): LibraryBook =
        LibraryBook(
            id = 1L,
            title = title,
            authors = "Author",
            coverPath = null,
            tocEntryCount = 0,
            tocEntriesSerialized = null,
            fileName = "sample.epub",
            localPath = "/tmp/sample.epub",
            sourceUri = "content://sample",
            fileSizeBytes = 1234L,
            lastReadChapterZipPath = null,
            lastReadScrollY = null,
            addedAt = 1L,
            lastOpenedAt = 1L,
            format = "EPUB",
        )
}

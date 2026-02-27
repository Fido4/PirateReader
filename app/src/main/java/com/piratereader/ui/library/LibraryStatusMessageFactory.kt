package com.piratereader.ui.library

import com.piratereader.data.library.LibraryBook
import com.piratereader.data.library.LibraryImportProgress
import com.piratereader.data.library.LibraryImportResult
import com.piratereader.data.library.LibraryImportProgressStage

enum class LibraryStatusKind {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class LibraryStatusAction(
    val label: String,
    val bookId: Long,
)

data class LibraryStatusPresentation(
    val kind: LibraryStatusKind,
    val message: String,
    val action: LibraryStatusAction? = null,
)

object LibraryStatusMessageFactory {
    fun importing(progress: LibraryImportProgress? = null): LibraryStatusPresentation {
        val message = if (progress == null) {
            "Importing EPUB..."
        } else {
            "${progress.stage.userFacingLabel()} (${progress.boundedPercent}%)"
        }
        return LibraryStatusPresentation(
            kind = LibraryStatusKind.INFO,
            message = message,
        )
    }

    fun importCompleted(result: LibraryImportResult): LibraryStatusPresentation =
        when (result) {
            is LibraryImportResult.Imported -> LibraryStatusPresentation(
                kind = LibraryStatusKind.SUCCESS,
                message = "Imported: ${result.book.title}",
            )

            is LibraryImportResult.AlreadyImported -> LibraryStatusPresentation(
                kind = LibraryStatusKind.WARNING,
                message = "Already in library: ${result.book.title}",
                action = LibraryStatusAction(
                    label = "Show Existing",
                    bookId = result.book.id,
                ),
            )
        }

    fun importFailed(error: Throwable): LibraryStatusPresentation =
        LibraryStatusPresentation(
            kind = LibraryStatusKind.ERROR,
            message = "Import failed: ${error.toUserFacingImportMessage()}",
        )

    fun resumeMarkedOpened(book: LibraryBook?): LibraryStatusPresentation =
        if (book != null) {
            LibraryStatusPresentation(
                kind = LibraryStatusKind.INFO,
                message = "Opened reader: ${book.title}",
            )
        } else {
            LibraryStatusPresentation(
                kind = LibraryStatusKind.ERROR,
                message = "Resume failed: book not found",
            )
        }

    private fun Throwable.toUserFacingImportMessage(): String =
        when (this) {
            is IllegalArgumentException -> message ?: "invalid EPUB file"
            else -> message ?: "unknown error"
        }

    private fun LibraryImportProgressStage.userFacingLabel(): String =
        when (this) {
            LibraryImportProgressStage.PREPARING -> "Preparing import"
            LibraryImportProgressStage.COPYING_SOURCE -> "Copying EPUB file"
            LibraryImportProgressStage.PARSING_METADATA -> "Parsing EPUB metadata"
            LibraryImportProgressStage.EXTRACTING_COVER -> "Extracting cover image"
            LibraryImportProgressStage.SAVING_LIBRARY -> "Saving to library"
        }
}

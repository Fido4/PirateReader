package com.piratereader.data.library

sealed interface LibraryImportResult {
    val book: LibraryBook

    data class Imported(override val book: LibraryBook) : LibraryImportResult

    data class AlreadyImported(override val book: LibraryBook) : LibraryImportResult
}

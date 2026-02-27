package com.piratereader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.piratereader.data.library.LibraryBook
import com.piratereader.data.library.LibraryImportProgress
import com.piratereader.data.library.LibraryRepository
import com.piratereader.data.library.PirateReaderDatabase
import com.piratereader.epub.EpubImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val isImporting: Boolean = false,
    val importProgressPercent: Int? = null,
    val importProgressLabel: String? = null,
    val statusMessage: String? = null,
    val statusKind: LibraryStatusKind = LibraryStatusKind.INFO,
    val statusAction: LibraryStatusAction? = null,
    val highlightedBookId: Long? = null,
    val activeReaderBook: LibraryBook? = null,
)

class LibraryViewModel(
    application: Application,
    private val repository: LibraryRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
    }

    fun importEpub(uri: Uri) {
        if (_uiState.value.isImporting) return

        viewModelScope.launch {
            val status = LibraryStatusMessageFactory.importing()
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importProgressPercent = 0,
                    importProgressLabel = status.message,
                    statusMessage = status.message,
                    statusKind = status.kind,
                    statusAction = status.action,
                )
            }

            val result = runCatching {
                repository.importEpub(uri) { progress ->
                    onImportProgress(progress)
                }
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { importResult ->
                        val completionStatus = LibraryStatusMessageFactory.importCompleted(importResult)
                        current.copy(
                            isImporting = false,
                            importProgressPercent = null,
                            importProgressLabel = null,
                            statusMessage = completionStatus.message,
                            statusKind = completionStatus.kind,
                            statusAction = completionStatus.action,
                            highlightedBookId = null,
                        )
                    },
                    onFailure = { error ->
                        val failureStatus = LibraryStatusMessageFactory.importFailed(error)
                        current.copy(
                            isImporting = false,
                            importProgressPercent = null,
                            importProgressLabel = null,
                            statusMessage = failureStatus.message,
                            statusKind = failureStatus.kind,
                            statusAction = failureStatus.action,
                        )
                    },
                )
            }
        }
    }

    fun resumeBook(bookId: Long) {
        viewModelScope.launch {
            val updated = repository.markBookOpened(bookId)
            val status = LibraryStatusMessageFactory.resumeMarkedOpened(updated)
            _uiState.update { current ->
                current.copy(
                    statusMessage = status.message,
                    statusKind = status.kind,
                    statusAction = status.action,
                    highlightedBookId = updated?.id,
                    activeReaderBook = updated,
                )
            }
        }
    }

    fun highlightBook(bookId: Long) {
        _uiState.update { current ->
            val exists = current.books.any { it.id == bookId }
            current.copy(
                highlightedBookId = if (exists) bookId else current.highlightedBookId,
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null, statusAction = null) }
    }

    fun closeReader() {
        _uiState.update { it.copy(activeReaderBook = null) }
    }

    fun saveReaderPositionAndClose(
        bookId: Long,
        chapterZipPath: String?,
        anchorFragment: String?,
        scrollX: Int?,
        scrollY: Int?,
        pageMode: String?,
        locatorSerialized: String?,
    ) {
        viewModelScope.launch {
            val updated = repository.saveReaderPosition(
                bookId = bookId,
                chapterZipPath = chapterZipPath,
                anchorFragment = anchorFragment,
                scrollX = scrollX,
                scrollY = scrollY,
                pageMode = pageMode,
                locatorSerialized = locatorSerialized,
            )
            _uiState.update { current ->
                current.copy(
                    activeReaderBook = null,
                    highlightedBookId = updated?.id ?: current.highlightedBookId,
                    statusMessage = updated?.let {
                        "Saved reader position: ${it.title}"
                    } ?: "Reader position save failed: book not found",
                    statusKind = if (updated != null) LibraryStatusKind.INFO else LibraryStatusKind.ERROR,
                    statusAction = null,
                )
            }
        }
    }

    private fun onImportProgress(progress: LibraryImportProgress) {
        val status = LibraryStatusMessageFactory.importing(progress)
        _uiState.update { current ->
            if (!current.isImporting) return@update current
            current.copy(
                importProgressPercent = progress.boundedPercent,
                importProgressLabel = status.message,
                statusMessage = status.message,
                statusKind = status.kind,
                statusAction = status.action,
            )
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                val db = PirateReaderDatabase.getInstance(application)
                val repository = LibraryRepository(
                    dao = db.libraryBookDao(),
                    epubImporter = EpubImporter(application.applicationContext),
                )
                return LibraryViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

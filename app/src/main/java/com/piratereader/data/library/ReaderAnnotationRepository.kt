package com.piratereader.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReaderAnnotationRepository(
    private val dao: ReaderAnnotationDao,
) {
    fun observeForBook(bookId: Long): Flow<List<ReaderAnnotation>> =
        dao.observeForBook(bookId).map { entities -> entities.map(ReaderAnnotationEntity::toDomain) }

    suspend fun addBookmark(
        bookId: Long,
        chapterZipPath: String,
        anchorFragment: String?,
        locatorSerialized: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): ReaderAnnotation =
        addAnnotation(
            bookId = bookId,
            type = ReaderAnnotationType.BOOKMARK,
            chapterZipPath = chapterZipPath,
            anchorFragment = anchorFragment,
            selectedText = null,
            locatorSerialized = locatorSerialized,
            createdAt = createdAt,
        )

    suspend fun addHighlight(
        bookId: Long,
        chapterZipPath: String,
        anchorFragment: String?,
        selectedText: String,
        locatorSerialized: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): ReaderAnnotation =
        addAnnotation(
            bookId = bookId,
            type = ReaderAnnotationType.HIGHLIGHT,
            chapterZipPath = chapterZipPath,
            anchorFragment = anchorFragment,
            selectedText = selectedText,
            locatorSerialized = locatorSerialized,
            createdAt = createdAt,
        )

    suspend fun remove(annotationId: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteById(annotationId)
        }
    }

    private suspend fun addAnnotation(
        bookId: Long,
        type: ReaderAnnotationType,
        chapterZipPath: String,
        anchorFragment: String?,
        selectedText: String?,
        locatorSerialized: String?,
        createdAt: Long,
    ): ReaderAnnotation {
        val normalizedChapter = chapterZipPath.trim().ifBlank { error("chapterZipPath is required") }
        val normalizedAnchor = anchorFragment?.trim()?.ifBlank { null }
        val normalizedText = selectedText?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null }
        val normalizedLocator = locatorSerialized?.trim()?.ifBlank { null }

        val insertedId = withContext(Dispatchers.IO) {
            dao.insert(
                ReaderAnnotationEntity(
                    bookId = bookId,
                    type = type.name,
                    chapterZipPath = normalizedChapter,
                    anchorFragment = normalizedAnchor,
                    selectedText = normalizedText,
                    locatorSerialized = normalizedLocator,
                    createdAt = createdAt,
                ),
            )
        }

        return ReaderAnnotation(
            id = insertedId,
            bookId = bookId,
            type = type,
            chapterZipPath = normalizedChapter,
            anchorFragment = normalizedAnchor,
            selectedText = normalizedText,
            locatorSerialized = normalizedLocator,
            createdAt = createdAt,
        )
    }
}

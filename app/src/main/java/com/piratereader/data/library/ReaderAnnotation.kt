package com.piratereader.data.library

enum class ReaderAnnotationType {
    BOOKMARK,
    HIGHLIGHT,
}

data class ReaderAnnotation(
    val id: Long,
    val bookId: Long,
    val type: ReaderAnnotationType,
    val chapterZipPath: String,
    val anchorFragment: String?,
    val selectedText: String?,
    val locatorSerialized: String?,
    val createdAt: Long,
)

internal fun parseReaderAnnotationType(value: String?): ReaderAnnotationType {
    val normalized = value?.trim()?.uppercase()
    return when (normalized) {
        ReaderAnnotationType.HIGHLIGHT.name -> ReaderAnnotationType.HIGHLIGHT
        else -> ReaderAnnotationType.BOOKMARK
    }
}

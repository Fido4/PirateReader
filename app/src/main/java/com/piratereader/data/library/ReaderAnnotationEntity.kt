package com.piratereader.data.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reader_annotations",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["bookId", "createdAt"]),
    ],
)
data class ReaderAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val type: String,
    val chapterZipPath: String,
    val anchorFragment: String?,
    val selectedText: String?,
    val locatorSerialized: String?,
    val createdAt: Long,
)

fun ReaderAnnotationEntity.toDomain(): ReaderAnnotation =
    ReaderAnnotation(
        id = id,
        bookId = bookId,
        type = parseReaderAnnotationType(type),
        chapterZipPath = chapterZipPath,
        anchorFragment = anchorFragment,
        selectedText = selectedText,
        locatorSerialized = locatorSerialized,
        createdAt = createdAt,
    )

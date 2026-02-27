package com.piratereader.data.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_books",
    indices = [
        Index(value = ["localPath"], unique = true),
        Index(value = ["sourceUri"], unique = true),
    ],
)
data class LibraryBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val authors: String,
    val coverPath: String?,
    val tocEntryCount: Int,
    val tocEntriesSerialized: String?,
    val fileName: String,
    val localPath: String,
    val sourceUri: String?,
    val fileSizeBytes: Long?,
    val lastReadChapterZipPath: String?,
    val lastReadAnchorFragment: String? = null,
    val lastReadScrollY: Int?,
    val lastReadScrollX: Int? = null,
    val lastReadPageMode: String? = null,
    val lastReadLocatorSerialized: String? = null,
    val format: String,
    val addedAt: Long,
    val lastOpenedAt: Long,
)

fun LibraryBookEntity.toDomain(): LibraryBook =
    LibraryBook(
        id = id,
        title = title,
        authors = authors,
        coverPath = coverPath,
        tocEntryCount = tocEntryCount,
        tocEntriesSerialized = tocEntriesSerialized,
        fileName = fileName,
        localPath = localPath,
        sourceUri = sourceUri,
        fileSizeBytes = fileSizeBytes,
        lastReadChapterZipPath = lastReadChapterZipPath,
        lastReadAnchorFragment = lastReadAnchorFragment,
        lastReadScrollY = lastReadScrollY,
        lastReadScrollX = lastReadScrollX,
        lastReadPageMode = lastReadPageMode,
        lastReadLocatorSerialized = lastReadLocatorSerialized,
        format = format,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
    )

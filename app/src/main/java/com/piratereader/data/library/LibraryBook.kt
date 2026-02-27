package com.piratereader.data.library

data class LibraryBook(
    val id: Long,
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
    val addedAt: Long,
    val lastOpenedAt: Long,
    val format: String = "EPUB",
)

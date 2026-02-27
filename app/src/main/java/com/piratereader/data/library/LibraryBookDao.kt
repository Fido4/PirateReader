package com.piratereader.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryBookDao {
    @Query(
        """
        SELECT * FROM library_books
        ORDER BY lastOpenedAt DESC, addedAt DESC, id DESC
        """,
    )
    fun observeAll(): Flow<List<LibraryBookEntity>>

    @Query("SELECT * FROM library_books WHERE id = :id LIMIT 1")
    // Keep DAO methods non-suspend for the current AGP/KSP/Room toolchain combo; repository calls run on Dispatchers.IO.
    fun findById(id: Long): LibraryBookEntity?

    @Query("SELECT * FROM library_books WHERE sourceUri = :sourceUri LIMIT 1")
    fun findBySourceUri(sourceUri: String): LibraryBookEntity?

    @Query("UPDATE library_books SET lastOpenedAt = :openedAt WHERE id = :bookId")
    fun updateLastOpenedAt(bookId: Long, openedAt: Long): Int

    @Query(
        """
        UPDATE library_books
        SET lastReadChapterZipPath = :chapterZipPath,
            lastReadAnchorFragment = :anchorFragment,
            lastReadScrollY = :scrollY,
            lastReadScrollX = :scrollX,
            lastReadPageMode = :pageMode,
            lastReadLocatorSerialized = :locatorSerialized
        WHERE id = :bookId
        """,
    )
    fun updateReaderPosition(
        bookId: Long,
        chapterZipPath: String?,
        anchorFragment: String?,
        scrollX: Int?,
        scrollY: Int?,
        pageMode: String?,
        locatorSerialized: String?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(book: LibraryBookEntity): Long
}

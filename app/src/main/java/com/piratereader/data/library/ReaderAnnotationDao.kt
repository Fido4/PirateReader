package com.piratereader.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderAnnotationDao {
    @Query(
        """
        SELECT * FROM reader_annotations
        WHERE bookId = :bookId
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observeForBook(bookId: Long): Flow<List<ReaderAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(annotation: ReaderAnnotationEntity): Long

    @Query("DELETE FROM reader_annotations WHERE id = :annotationId")
    fun deleteById(annotationId: Long): Int
}

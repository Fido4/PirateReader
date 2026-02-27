package com.piratereader.data.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LibraryBookEntity::class, ReaderAnnotationEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class PirateReaderDatabase : RoomDatabase() {
    abstract fun libraryBookDao(): LibraryBookDao
    abstract fun readerAnnotationDao(): ReaderAnnotationDao

    companion object {
        @Volatile
        private var instance: PirateReaderDatabase? = null

        fun getInstance(context: Context): PirateReaderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PirateReaderDatabase::class.java,
                    "pirate_reader.db",
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    .build()
                    .also { db ->
                    instance = db
                }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN coverPath TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN tocEntryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library_books ADD COLUMN tocEntriesSerialized TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN lastReadChapterZipPath TEXT")
                db.execSQL("ALTER TABLE library_books ADD COLUMN lastReadScrollY INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN lastReadAnchorFragment TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN lastReadScrollX INTEGER")
                db.execSQL("ALTER TABLE library_books ADD COLUMN lastReadPageMode TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN lastReadLocatorSerialized TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reader_annotations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        chapterZipPath TEXT NOT NULL,
                        anchorFragment TEXT,
                        selectedText TEXT,
                        locatorSerialized TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_reader_annotations_bookId
                    ON reader_annotations(bookId)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_reader_annotations_bookId_createdAt
                    ON reader_annotations(bookId, createdAt)
                    """.trimIndent(),
                )
            }
        }
    }
}

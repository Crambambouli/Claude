package com.puzzle.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Puzzle Android app.
 *
 * Version history:
 *   1 – initial schema: [ExampleEntity]
 */
@Database(
    entities = [ExampleEntity::class],
    version  = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exampleDao(): ExampleDao

    companion object {
        private const val DATABASE_NAME = "puzzle_android.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

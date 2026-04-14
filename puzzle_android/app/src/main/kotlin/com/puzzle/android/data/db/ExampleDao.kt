package com.puzzle.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExampleDao {

    /** Observe all examples ordered by insertion time (newest first). */
    @Query("SELECT * FROM examples ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ExampleEntity>>

    /** Insert or replace a single example. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(example: ExampleEntity)

    /** Insert or replace multiple examples in one transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(examples: List<ExampleEntity>)

    /** Delete a single example by its id. */
    @Query("DELETE FROM examples WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Delete all rows from the table. */
    @Query("DELETE FROM examples")
    suspend fun deleteAll()

    /** Return the total number of stored examples. */
    @Query("SELECT COUNT(*) FROM examples")
    suspend fun count(): Int
}

package com.puzzle.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity matching the "Example" schema entity.
 *
 * Table: examples
 * Columns:
 *   id          – STRING, primary key (not auto-generated; supplied by the server / caller)
 *   created_at  – Unix epoch millis, set on insert
 */
@Entity(tableName = "examples")
data class ExampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

package com.puzzle.android.data.repository

import android.util.Log
import com.puzzle.android.data.api.ApiService
import com.puzzle.android.data.db.ExampleDao
import com.puzzle.android.data.db.ExampleEntity
import com.puzzle.android.data.model.HealthResponse
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the app.
 *
 * - Remote calls go through [ApiService].
 * - Local persistence goes through [ExampleDao].
 * - Returns [Result] so callers can react to success / failure without
 *   catching exceptions themselves.
 */
class ExampleRepository(
    private val apiService: ApiService,
    private val exampleDao: ExampleDao
) {

    /** Emit the live list of locally stored examples (newest first). */
    val examples: Flow<List<ExampleEntity>> = exampleDao.observeAll()

    /**
     * Call GET /api/health and return the parsed body.
     * Network or HTTP errors are wrapped in [Result.failure].
     */
    suspend fun fetchHealth(): Result<HealthResponse> = runCatching {
        val response = apiService.getHealth()
        if (response.isSuccessful) {
            response.body() ?: error("Empty body from /api/health")
        } else {
            error("HTTP ${response.code()} – ${response.message()}")
        }
    }.onFailure { e ->
        Log.e(TAG, "fetchHealth failed", e)
    }

    /** Persist a new example to the local database. */
    suspend fun saveExample(id: String) {
        exampleDao.insert(ExampleEntity(id = id))
        Log.d(TAG, "Saved example id=$id")
    }

    /** Remove all locally stored examples. */
    suspend fun clearExamples() {
        exampleDao.deleteAll()
        Log.d(TAG, "Cleared all examples")
    }

    companion object {
        private const val TAG = "ExampleRepository"
    }
}

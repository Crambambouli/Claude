package com.puzzle.android.data.api

import com.puzzle.android.data.model.HealthResponse
import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit service interface.
 * Mirrors the API endpoints defined in the manifest:
 *   GET /api/health
 */
interface ApiService {

    @GET("api/health")
    suspend fun getHealth(): Response<HealthResponse>
}

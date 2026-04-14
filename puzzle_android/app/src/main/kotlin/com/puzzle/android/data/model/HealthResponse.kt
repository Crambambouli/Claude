package com.puzzle.android.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response body for GET /api/health
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("version")
    val version: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long? = null
)

package com.puzzle.android.data.image

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.puzzle.android.BuildConfig
import com.puzzle.android.data.model.PuzzleCategory
import com.puzzle.android.data.model.PuzzleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ImageGenerator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun buildPrompt(category: PuzzleCategory, style: PuzzleStyle): String =
        "${style.englishPrompt} ${category.englishPrompt}, " +
                "highly detailed, beautiful, square composition, no text"

    fun buildPollinationsUrl(category: PuzzleCategory, style: PuzzleStyle): String {
        val encoded = URLEncoder.encode(buildPrompt(category, style), "UTF-8")
        val seed = Random.nextInt(1, 100_000)
        return "https://image.pollinations.ai/prompt/$encoded" +
                "?width=512&height=512&nologo=true&seed=$seed"
    }

    suspend fun downloadFromHuggingFace(prompt: String): Bitmap? {
        val token = BuildConfig.HF_API_TOKEN
        if (token.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val escaped = prompt.replace("\\", "\\\\").replace("\"", "\\\"")
                val body = """{"inputs":"$escaped"}"""
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-schnell")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Exception) { null }
        }
    }

    suspend fun downloadFromPicsum(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val seed = Random.nextInt(1, 9_999)
            val request = Request.Builder()
                .url("https://picsum.photos/seed/$seed/512/512")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) { null }
    }

    suspend fun downloadFromPollinations(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) { null }
    }

    fun loadFromAssets(assets: AssetManager, filename: String = "puzzle_image.jpg"): Bitmap? =
        try {
            assets.open(filename).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
}

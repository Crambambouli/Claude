package com.puzzle.android.data.image

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.puzzle.android.BuildConfig
import com.puzzle.android.data.model.PuzzleCategory
import com.puzzle.android.data.model.PuzzleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
        val escaped  = prompt.replace("\\", "\\\\").replace("\"", "\\\"")
        val bodyStr  = """{"inputs":"$escaped"}"""
        for (attempt in 0 until 3) {
            try {
                var retryMs = 0L
                val bmp = withContext(Dispatchers.IO) {
                    val body    = bodyStr.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-schnell")
                        .header("Authorization", "Bearer $token")
                        .post(body)
                        .build()
                    client.newCall(request).execute().use { response ->
                        when {
                            response.isSuccessful ->
                                response.body?.bytes()
                                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            response.code == 503 -> {
                                retryMs = try {
                                    val json = JSONObject(response.body?.string() ?: "{}")
                                    (json.optDouble("estimated_time", 20.0) * 1000)
                                        .toLong().coerceIn(5_000, 30_000)
                                } catch (_: Exception) { 20_000L }
                                null
                            }
                            response.code == 401 || response.code == 403 -> {
                                Log.w("ImageGen", "HF auth error ${response.code}")
                                retryMs = -1L
                                null
                            }
                            else -> {
                                Log.w("ImageGen", "HF error ${response.code}: ${response.body?.string()?.take(200)}")
                                null
                            }
                        }
                    }
                }
                if (bmp != null) return bmp
                if (retryMs == -1L) return null  // auth failure, no retry
                if (retryMs > 0) delay(retryMs) else if (attempt < 2) delay(3_000)
            } catch (_: Exception) {
                if (attempt < 2) delay(3_000)
            }
        }
        return null
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

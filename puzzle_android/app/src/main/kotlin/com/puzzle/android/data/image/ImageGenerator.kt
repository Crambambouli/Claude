package com.puzzle.android.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.puzzle.android.data.model.PuzzleCategory
import com.puzzle.android.data.model.PuzzleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ImageGenerator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun buildUrl(category: PuzzleCategory, style: PuzzleStyle): String {
        val prompt = "${style.englishPrompt} ${category.englishPrompt}, " +
                "highly detailed, beautiful, square composition, no text"
        val encoded = URLEncoder.encode(prompt, "UTF-8")
        val seed = Random.nextInt(1, 100_000)
        return "https://image.pollinations.ai/prompt/$encoded" +
                "?width=512&height=512&nologo=true&seed=$seed"
    }

    suspend fun download(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }
}

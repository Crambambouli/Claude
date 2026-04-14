package com.puzzle.android.data.update

import android.content.Context
import com.puzzle.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionCode: Int, val versionName: String)

class UpdateChecker(private val context: Context) {

    private val client = OkHttpClient()

    companion object {
        private const val VERSION_URL =
            "https://downloads.demoport.de/puzzle_android_version.json"
        const val APK_URL = "https://downloads.demoport.de/puzzle_android.apk"
    }

    /** Returns [UpdateInfo] if a newer version is available, null otherwise. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = client.newCall(Request.Builder().url(VERSION_URL).build())
                .execute().use { it.body?.string() } ?: return@runCatching null
            val json = JSONObject(body)
            val remoteCode = json.getInt("versionCode")
            if (remoteCode > BuildConfig.VERSION_CODE) {
                UpdateInfo(remoteCode, json.getString("versionName"))
            } else null
        }.getOrNull()
    }

    /** Downloads the APK to the app cache dir and returns the [File]. */
    suspend fun downloadApk(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(Request.Builder().url(APK_URL).build()).execute()
            val bytes = response.body?.bytes() ?: return@runCatching null
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            File(dir, "puzzle_update.apk").also { it.writeBytes(bytes) }
        }.getOrNull()
    }
}

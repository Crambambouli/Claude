package com.puzzle.android

import android.app.Application
import android.util.Log
import com.puzzle.android.data.api.ApiClient
import com.puzzle.android.data.db.AppDatabase
import com.puzzle.android.data.repository.ExampleRepository
import com.puzzle.android.data.update.UpdateChecker

/**
 * Application entry point.
 *
 * Provides a manual service-locator for dependencies so that ViewModels can
 * retrieve them without a full DI framework.  For larger apps, replace this
 * with Hilt / Koin.
 */
class PuzzleApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val repository: ExampleRepository by lazy {
        ExampleRepository(
            apiService  = ApiClient.apiService,
            exampleDao  = database.exampleDao()
        )
    }

    val updateChecker: UpdateChecker by lazy {
        UpdateChecker(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PuzzleApplication started – version ${BuildConfig.VERSION_NAME}")
    }

    companion object {
        private const val TAG = "PuzzleApplication"
    }
}

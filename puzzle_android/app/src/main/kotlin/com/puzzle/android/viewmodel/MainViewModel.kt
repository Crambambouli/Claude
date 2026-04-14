package com.puzzle.android.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.puzzle.android.data.db.ExampleEntity
import com.puzzle.android.data.repository.ExampleRepository
import com.puzzle.android.data.update.UpdateChecker
import com.puzzle.android.data.update.UpdateInfo
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────────────────────────
// UI State
// ──────────────────────────────────────────────────────────────────────────────

/** Represents the status of the last health-check call. */
sealed interface HealthStatus {
    data object Idle    : HealthStatus
    data object Loading : HealthStatus
    data class  Success(val statusText: String, val version: String?) : HealthStatus
    data class  Error(val message: String) : HealthStatus
}

data class MainUiState(
    val healthStatus: HealthStatus = HealthStatus.Idle,
    val updateInfo: UpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val pendingInstallFile: File? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

class MainViewModel(
    private val repository: ExampleRepository,
    private val updateChecker: UpdateChecker? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        if (updateChecker != null) checkForUpdate()
    }

    /** Live list of locally stored examples (emitted by Room). */
    val examples: StateFlow<List<ExampleEntity>> = repository.examples
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(5_000),
            initialValue     = emptyList()
        )

    /** Trigger GET /api/health and update [uiState] accordingly. */
    fun checkHealth() {
        if (_uiState.value.healthStatus is HealthStatus.Loading) return
        Log.d(TAG, "checkHealth invoked")

        _uiState.update { it.copy(healthStatus = HealthStatus.Loading) }

        viewModelScope.launch {
            repository.fetchHealth().fold(
                onSuccess = { response ->
                    Log.i(TAG, "Health OK – status=${response.status}")
                    _uiState.update {
                        it.copy(
                            healthStatus = HealthStatus.Success(
                                statusText = response.status,
                                version    = response.version
                            )
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Health check failed: ${error.message}")
                    _uiState.update {
                        it.copy(
                            healthStatus = HealthStatus.Error(
                                message = error.message ?: "Unknown error"
                            )
                        )
                    }
                }
            )
        }
    }

    /** Save a new Example with the given [id] to the local database. */
    fun saveExample(id: String) {
        viewModelScope.launch {
            repository.saveExample(id)
        }
    }

    /** Delete all locally stored examples. */
    fun clearExamples() {
        viewModelScope.launch {
            repository.clearExamples()
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = updateChecker?.checkForUpdate()
            if (info != null) _uiState.update { it.copy(updateInfo = info) }
        }
    }

    /** Downloads the update APK; sets [MainUiState.pendingInstallFile] when ready. */
    fun downloadUpdate() {
        if (_uiState.value.isDownloadingUpdate) return
        _uiState.update { it.copy(isDownloadingUpdate = true) }
        viewModelScope.launch {
            val file = updateChecker?.downloadApk()
            _uiState.update { it.copy(isDownloadingUpdate = false, pendingInstallFile = file) }
        }
    }

    /** Called after the install intent has been launched to reset install state. */
    fun onInstallConsumed() {
        _uiState.update { it.copy(pendingInstallFile = null, updateInfo = null) }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Factory
// ──────────────────────────────────────────────────────────────────────────────

class MainViewModelFactory(
    private val repository: ExampleRepository,
    private val updateChecker: UpdateChecker? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MainViewModel(repository, updateChecker) as T
    }
}

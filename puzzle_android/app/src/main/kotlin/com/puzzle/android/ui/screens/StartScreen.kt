package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.puzzle.android.BuildConfig
import com.puzzle.android.R
import com.puzzle.android.data.db.ExampleEntity
import com.puzzle.android.ui.theme.PuzzleAndroidTheme
import com.puzzle.android.viewmodel.HealthStatus
import com.puzzle.android.viewmodel.MainUiState
import com.puzzle.android.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Test tags for UI tests
// ─────────────────────────────────────────────────────────────────────────────
object StartScreenTags {
    const val HEALTH_BUTTON   = "health_button"
    const val LOADING_SPINNER = "loading_spinner"
    const val STATUS_CARD     = "status_card"
    const val ERROR_TEXT      = "error_text"
    const val EXAMPLES_LIST   = "examples_list"
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StartScreen(viewModel: MainViewModel) {
    val uiState  by viewModel.uiState.collectAsState()
    val examples by viewModel.examples.collectAsState()
    val context  = LocalContext.current

    // Trigger system package-installer when APK download is complete
    LaunchedEffect(uiState.pendingInstallFile) {
        val file = uiState.pendingInstallFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        viewModel.onInstallConsumed()
    }

    StartScreenContent(
        uiState         = uiState,
        examples        = examples,
        onCheckHealth   = viewModel::checkHealth,
        onAddExample    = { viewModel.saveExample(UUID.randomUUID().toString()) },
        onClearExamples = viewModel::clearExamples,
        onDownloadUpdate = viewModel::downloadUpdate
    )
}

@Composable
internal fun StartScreenContent(
    uiState          : MainUiState,
    examples         : List<ExampleEntity>,
    onCheckHealth    : () -> Unit,
    onAddExample     : () -> Unit,
    onClearExamples  : () -> Unit,
    onDownloadUpdate : () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    // Show a Snackbar when health check succeeds
    LaunchedEffect(uiState.healthStatus) {
        if (uiState.healthStatus is HealthStatus.Success) {
            scope.launch {
                snackbarHostState.showSnackbar("Health OK – ${(uiState.healthStatus as HealthStatus.Success).statusText}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "Puzzle Android",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    Text(
                        text  = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.updateInfo != null) {
                item { UpdateBanner(uiState, onDownloadUpdate) }
            }
            item { HealthCheckSection(uiState, onCheckHealth) }
            item { ExamplesHeader(examples.size, onAddExample, onClearExamples) }

            if (examples.isEmpty()) {
                item {
                    Text(
                        text  = stringResource(R.string.no_examples),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(
                    items = examples,
                    key   = { it.id }
                ) { example ->
                    ExampleRow(example)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UpdateBanner(uiState: MainUiState, onDownloadUpdate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "Update verfügbar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text  = "Version ${uiState.updateInfo?.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (uiState.isDownloadingUpdate) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                FilledTonalButton(
                    onClick  = onDownloadUpdate,
                    colors   = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor   = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Aktualisieren")
                }
            }
        }
    }
}

@Composable
private fun HealthCheckSection(
    uiState      : MainUiState,
    onCheckHealth: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text  = "API Health",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Button(
                onClick  = onCheckHealth,
                enabled  = uiState.healthStatus !is HealthStatus.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StartScreenTags.HEALTH_BUTTON)
            ) {
                if (uiState.healthStatus is HealthStatus.Loading) {
                    CircularProgressIndicator(
                        modifier  = Modifier
                            .size(18.dp)
                            .testTag(StartScreenTags.LOADING_SPINNER),
                        strokeWidth = 2.dp,
                        color     = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.health_check_button))
                }
            }

            AnimatedVisibility(
                visible = uiState.healthStatus !is HealthStatus.Idle,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                HealthStatusDisplay(uiState.healthStatus)
            }
        }
    }
}

@Composable
private fun HealthStatusDisplay(status: HealthStatus) {
    when (status) {
        is HealthStatus.Success -> {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StartScreenTags.STATUS_CARD)
            ) {
                Row(
                    modifier            = Modifier.padding(12.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = "Healthy",
                        tint               = MaterialTheme.colorScheme.tertiary
                    )
                    Column {
                        Text(
                            text  = status.statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        status.version?.let {
                            Text(
                                text  = "Version: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        is HealthStatus.Error -> {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StartScreenTags.ERROR_TEXT),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Error,
                        contentDescription = "Error",
                        tint               = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text  = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun ExamplesHeader(
    count           : Int,
    onAddExample    : () -> Unit,
    onClearExamples : () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text  = stringResource(R.string.examples_section) + " ($count)",
            style = MaterialTheme.typography.titleLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAddExample) {
                Text("Add")
            }
            IconButton(
                onClick  = onClearExamples,
                enabled  = count > 0
            ) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Clear examples",
                    tint               = if (count > 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
private fun ExampleRow(example: ExampleEntity) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StartScreenTags.EXAMPLES_LIST)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text  = "ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text       = example.id,
                style      = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Idle – Light", showBackground = true)
@Composable
private fun PreviewIdle() {
    PuzzleAndroidTheme(darkTheme = false) {
        StartScreenContent(
            uiState         = MainUiState(HealthStatus.Idle),
            examples        = emptyList(),
            onCheckHealth   = {},
            onAddExample    = {},
            onClearExamples = {}
        )
    }
}

@Preview(name = "Success – Dark", showBackground = true)
@Composable
private fun PreviewSuccessDark() {
    PuzzleAndroidTheme(darkTheme = true, dynamicColor = false) {
        StartScreenContent(
            uiState = MainUiState(HealthStatus.Success("ok", "0.1.0")),
            examples = listOf(
                ExampleEntity(id = "abc-123"),
                ExampleEntity(id = "def-456")
            ),
            onCheckHealth   = {},
            onAddExample    = {},
            onClearExamples = {}
        )
    }
}

@Preview(name = "Error – Light", showBackground = true)
@Composable
private fun PreviewError() {
    PuzzleAndroidTheme(darkTheme = false) {
        StartScreenContent(
            uiState         = MainUiState(HealthStatus.Error("Unable to reach server")),
            examples        = emptyList(),
            onCheckHealth   = {},
            onAddExample    = {},
            onClearExamples = {}
        )
    }
}

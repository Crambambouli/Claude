package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.puzzle.android.R
import com.puzzle.android.data.model.PuzzleCategory
import com.puzzle.android.data.model.PuzzleStyle
import com.puzzle.android.viewmodel.PuzzleViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    navController: NavController,
    vm: PuzzleViewModel = viewModel()
) {
    val category      by vm.category.collectAsState()
    val style         by vm.style.collectAsState()
    val pieceCount    by vm.pieceCount.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()
    val hasSavedGame  by vm.hasSavedGame.collectAsState()

    LaunchedEffect(Unit) {
        vm.goToPuzzle.collect { navController.navigate("puzzle") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_rose),
                            contentDescription = null,
                            tint               = androidx.compose.ui.graphics.Color.Unspecified,
                            modifier           = Modifier.size(28.dp)
                        )
                        Text("Puzzle Rose", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors  = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = { OrientationToggleButton() }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Kategorie ────────────────────────────────────────────────
                SectionTitle("Kategorie")
                CategoryGrid(selected = category, onSelect = vm::selectCategory)

                // ── Stil ─────────────────────────────────────────────────────
                SectionTitle("Stil")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PuzzleStyle.entries.forEach { s ->
                        FilterChip(
                            selected = s == style,
                            onClick  = { vm.selectStyle(s) },
                            label    = { Text(s.label) }
                        )
                    }
                }

                // ── Teile ─────────────────────────────────────────────────────
                SectionTitle("Anzahl Teile")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(50, 100, 200, 400, 850).forEach { n ->
                        if (n == pieceCount) {
                            Button(onClick = {}) { Text("$n Teile") }
                        } else {
                            OutlinedButton(onClick = { vm.selectPieceCount(n) }) { Text("$n Teile") }
                        }
                    }
                }

                // ── Error hint ───────────────────────────────────────────────
                AnimatedVisibility(visible = error != null) {
                    error?.let {
                        Text(
                            text  = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Generate button ──────────────────────────────────────────
                Button(
                    onClick  = vm::generatePuzzle,
                    enabled  = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        text       = "Puzzle generieren",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (hasSavedGame) {
                    OutlinedButton(
                        onClick  = vm::loadSavedGame,
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text  = "Gespeichertes Spiel laden",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            // ── Loading overlay ───────────────────────────────────────────────
            if (isLoading) {
                Box(
                    modifier          = Modifier.fillMaxSize(),
                    contentAlignment  = Alignment.Center
                ) {
                    Card(
                        elevation = CardDefaults.cardElevation(8.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier            = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(48.dp))
                            Text("KI generiert dein Bild…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun CategoryGrid(selected: PuzzleCategory, onSelect: (PuzzleCategory) -> Unit) {
    val cats = PuzzleCategory.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 3) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 2) {
                    val idx = row * 2 + col
                    if (idx < cats.size) {
                        val cat = cats[idx]
                        val isSelected = cat == selected
                        Card(
                            onClick  = { onSelect(cat) },
                            modifier = Modifier.weight(1f).height(72.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isSelected)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else
                                null
                        ) {
                            Column(
                                modifier            = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(cat.emoji, style = MaterialTheme.typography.headlineSmall)
                                Text(cat.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

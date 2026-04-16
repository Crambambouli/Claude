package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puzzle.android.game.PuzzleBoard
import com.puzzle.android.viewmodel.PuzzleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel = viewModel()) {
    val board by viewModel.board.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "Puzzle Rose",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    Text(
                        text     = "${board.moveCount} Züge",
                        style    = MaterialTheme.typography.labelLarge,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Win banner ───────────────────────────────────────────────────
            AnimatedVisibility(
                visible = board.isSolved,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text       = "Gelöst in ${board.moveCount} Zügen!",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier   = Modifier
                            .padding(20.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            // ── Puzzle grid ──────────────────────────────────────────────────
            BoxWithConstraints(
                modifier        = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val gridSize = minOf(maxWidth, maxHeight) - 8.dp
                PuzzleGrid(
                    board        = board,
                    modifier     = Modifier.size(gridSize),
                    onTileTapped = viewModel::onTileTapped
                )
            }

            // ── New game button ──────────────────────────────────────────────
            Button(
                onClick  = viewModel::newGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text  = "Neues Spiel",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PuzzleGrid(
    board       : PuzzleBoard,
    modifier    : Modifier,
    onTileTapped: (Int) -> Unit
) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(board.size) { row ->
            Row(
                modifier              = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(board.size) { col ->
                    val index   = row * board.size + col
                    val value   = board.tiles[index]
                    val canMove = !board.isSolved && board.canMove(index)

                    PuzzleTile(
                        value    = value,
                        canMove  = canMove,
                        isSolved = board.isSolved,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick  = { onTileTapped(index) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PuzzleTile(
    value   : Int,
    canMove : Boolean,
    isSolved: Boolean,
    modifier: Modifier,
    onClick : () -> Unit
) {
    if (value == 0) {
        // Empty space – invisible placeholder
        Surface(
            modifier = modifier,
            color    = MaterialTheme.colorScheme.surface
        ) {}
        return
    }

    val containerColor = when {
        isSolved -> MaterialTheme.colorScheme.tertiary
        canMove  -> MaterialTheme.colorScheme.primary
        else     -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isSolved -> MaterialTheme.colorScheme.onTertiary
        canMove  -> MaterialTheme.colorScheme.onPrimary
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick  = onClick,
        modifier = modifier,
        enabled  = canMove,
        shape    = RoundedCornerShape(8.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = containerColor,
            contentColor           = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor   = contentColor
        )
    ) {
        Text(
            text       = value.toString(),
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

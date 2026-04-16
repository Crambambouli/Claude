package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puzzle.android.game.PuzzleBoard
import com.puzzle.android.viewmodel.PuzzleViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel = viewModel()) {
    val board by viewModel.board.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Puzzle Rose", style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Win banner ───────────────────────────────────────────────────
            AnimatedVisibility(visible = board.isSolved, enter = fadeIn(), exit = fadeOut()) {
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
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val gridSize = minOf(maxWidth, maxHeight) - 8.dp
                PuzzleGrid(
                    board        = board,
                    gridSize     = gridSize,
                    onTileTapped = viewModel::onTileTapped
                )
            }

            // ── Controls ─────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Mini preview of the complete image
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    RoseImage(modifier = Modifier.fillMaxSize())
                }

                Text(
                    text  = "Vorlage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick  = viewModel::newGame,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Neues Spiel", style = MaterialTheme.typography.titleSmall)
                }
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
    gridSize    : Dp,
    onTileTapped: (Int) -> Unit
) {
    val gap      = 3.dp
    val tileSize = (gridSize - gap * (board.size - 1)) / board.size

    Column(
        modifier            = Modifier.size(gridSize),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        repeat(board.size) { row ->
            Row(
                modifier              = Modifier.height(tileSize).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                repeat(board.size) { col ->
                    val index   = row * board.size + col
                    val value   = board.tiles[index]
                    val canMove = !board.isSolved && board.canMove(index)

                    ImageTile(
                        value     = value,
                        boardSize = board.size,
                        tileSize  = tileSize,
                        canMove   = canMove,
                        isSolved  = board.isSolved,
                        modifier  = Modifier.width(tileSize).fillMaxHeight(),
                        onClick   = { onTileTapped(index) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single image tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImageTile(
    value    : Int,
    boardSize: Int,
    tileSize : Dp,
    canMove  : Boolean,
    isSolved : Boolean,
    modifier : Modifier,
    onClick  : () -> Unit
) {
    if (value == 0) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {}
        return
    }

    val srcRow   = (value - 1) / boardSize
    val srcCol   = (value - 1) % boardSize
    val fullSize = tileSize * boardSize

    val borderColor = when {
        isSolved -> MaterialTheme.colorScheme.tertiary
        canMove  -> MaterialTheme.colorScheme.primary
        else     -> Color.Transparent
    }
    val borderWidth = if (isSolved || canMove) 2.dp else 0.dp
    val shape       = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .clipToBounds()
            .then(
                if (canMove || isSolved)
                    Modifier  // tappable via Surface below
                else
                    Modifier
            )
    ) {
        // Offset full image so only the correct portion is visible
        RoseImage(
            modifier = Modifier
                .size(fullSize)
                .offset(x = -tileSize * srcCol, y = -tileSize * srcRow)
        )

        // Invisible tap target covering the whole tile
        Surface(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
            color    = if (canMove) Color(0x22000000) else Color.Transparent,
            enabled  = canMove || !isSolved
        ) {}
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rose image drawn with Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RoseImage(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // ── Background gradient ───────────────────────────────────────────
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF0F5), Color(0xFFFFE4EC), Color(0xFFFFF0F5)),
                startY = 0f, endY = h
            )
        )

        // ── Stem ─────────────────────────────────────────────────────────
        val stemPath = Path().apply {
            moveTo(w * 0.50f, h * 0.96f)
            cubicTo(
                w * 0.49f, h * 0.85f,
                w * 0.51f, h * 0.75f,
                w * 0.50f, h * 0.62f
            )
        }
        drawPath(stemPath, Color(0xFF2E7D32), style = Stroke(width = w * 0.030f, cap = StrokeCap.Round))

        // ── Left leaf ────────────────────────────────────────────────────
        val leftLeaf = Path().apply {
            moveTo(w * 0.50f, h * 0.78f)
            cubicTo(w * 0.28f, h * 0.66f, w * 0.18f, h * 0.82f, w * 0.32f, h * 0.86f)
            cubicTo(w * 0.40f, h * 0.83f, w * 0.46f, h * 0.81f, w * 0.50f, h * 0.78f)
        }
        drawPath(leftLeaf, Color(0xFF388E3C))
        drawPath(leftLeaf, Color(0xFF2E7D32), style = Stroke(width = w * 0.008f))

        // ── Right leaf ───────────────────────────────────────────────────
        val rightLeaf = Path().apply {
            moveTo(w * 0.50f, h * 0.70f)
            cubicTo(w * 0.72f, h * 0.58f, w * 0.82f, h * 0.74f, w * 0.68f, h * 0.78f)
            cubicTo(w * 0.60f, h * 0.76f, w * 0.54f, h * 0.73f, w * 0.50f, h * 0.70f)
        }
        drawPath(rightLeaf, Color(0xFF43A047))
        drawPath(rightLeaf, Color(0xFF2E7D32), style = Stroke(width = w * 0.008f))

        // ── Rose petals ───────────────────────────────────────────────────
        val cx = w * 0.50f
        val cy = h * 0.36f
        val r  = minOf(w, h) * 0.295f

        // Outermost petal ring (5 petals, dark rose)
        repeat(5) { i ->
            val angle = (i * 72.0 - 90.0) * PI / 180.0
            drawCircle(
                color  = Color(0xFFBF1650),
                radius = r * 0.60f,
                center = Offset(
                    cx + (r * 0.85f * cos(angle)).toFloat(),
                    cy + (r * 0.85f * sin(angle)).toFloat()
                )
            )
        }

        // Middle petal ring (5 petals, medium rose)
        repeat(5) { i ->
            val angle = (i * 72.0 - 54.0) * PI / 180.0
            drawCircle(
                color  = Color(0xFFE91E63),
                radius = r * 0.52f,
                center = Offset(
                    cx + (r * 0.52f * cos(angle)).toFloat(),
                    cy + (r * 0.52f * sin(angle)).toFloat()
                )
            )
        }

        // Inner petal ring (5 petals, light rose)
        repeat(5) { i ->
            val angle = (i * 72.0 - 18.0) * PI / 180.0
            drawCircle(
                color  = Color(0xFFF06292),
                radius = r * 0.38f,
                center = Offset(
                    cx + (r * 0.26f * cos(angle)).toFloat(),
                    cy + (r * 0.26f * sin(angle)).toFloat()
                )
            )
        }

        // Center bud
        drawCircle(Color(0xFF880E4F), radius = r * 0.20f, center = Offset(cx, cy))
        drawCircle(Color(0xFFC2185B), radius = r * 0.12f, center = Offset(cx, cy))

        // Highlight shimmer
        drawCircle(
            color  = Color(0x66FFFFFF),
            radius = r * 0.08f,
            center = Offset(cx - r * 0.06f, cy - r * 0.08f)
        )

        // ── Dewdrops ──────────────────────────────────────────────────────
        listOf(
            Offset(w * 0.30f, h * 0.55f),
            Offset(w * 0.72f, h * 0.48f),
            Offset(w * 0.20f, h * 0.40f)
        ).forEach { pos ->
            drawCircle(Color(0x88AADDFF), radius = w * 0.018f, center = pos)
            drawCircle(Color(0x44FFFFFF), radius = w * 0.008f,
                center = Offset(pos.x - w * 0.006f, pos.y - w * 0.006f))
        }
    }
}

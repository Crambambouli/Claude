package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.puzzle.android.game.JigsawState
import com.puzzle.android.game.PuzzlePiece
import com.puzzle.android.viewmodel.PuzzleViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    navController: NavController,
    vm: PuzzleViewModel = viewModel()
) {
    val jigsaw by vm.jigsaw.collectAsState()
    val bitmap by vm.bitmap.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Puzzle Rose", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { vm.backToSetup(); navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    jigsaw?.let { state ->
                        Text(
                            text     = "${state.moveCount} Teile",
                            style    = MaterialTheme.typography.labelLarge,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val state = jigsaw
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Kein Puzzle geladen")
            }
            return@Scaffold
        }

        Column(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Win banner ───────────────────────────────────────────────────
            AnimatedVisibility(visible = state.isSolved, enter = fadeIn(), exit = fadeOut()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text       = "Gelöst in ${state.moveCount} Zügen!",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier   = Modifier.padding(20.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }

            // ── Puzzle board ─────────────────────────────────────────────────
            BoxWithConstraints(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val boardSize = minOf(maxWidth, maxHeight)
                JigsawBoard(
                    state          = state,
                    bitmap         = bitmap,
                    onPieceDropped = vm::onPieceDropped,
                    modifier       = Modifier.size(boardSize)
                )
            }

            // ── Controls ─────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick  = { vm.backToSetup(); navController.popBackStack() },
                    modifier = Modifier.height(48.dp)
                ) { Text("Motiv wählen") }

                Box(Modifier.weight(1f))

                Button(
                    onClick  = vm::newGame,
                    modifier = Modifier.height(48.dp)
                ) { Text("Neu mischen") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Jigsaw board with drag-and-drop pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun JigsawBoard(
    state          : JigsawState,
    bitmap         : ImageBitmap?,
    onPieceDropped : (id: Int, x: Float, y: Float) -> Unit,
    modifier       : Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx  = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density  = LocalDensity.current

        val dragOffsets = remember { mutableStateMapOf<Int, Offset>() }
        var topId by remember { mutableIntStateOf(-1) }

        // Background grid slots
        Canvas(Modifier.fillMaxSize()) {
            val tW = widthPx  / state.size
            val tH = heightPx / state.size
            for (r in 0 until state.size) {
                for (c in 0 until state.size) {
                    val placed = state.pieces.any { it.id == r * state.size + c && it.isPlaced }
                    drawRect(
                        color   = if (placed) Color(0x2200C853) else Color(0x18000000),
                        topLeft = Offset(c * tW, r * tH),
                        size    = Size(tW - 1f, tH - 1f),
                        style   = Stroke(2f)
                    )
                }
            }
        }

        // Placed pieces first → unplaced → actively dragged on top
        val sorted = remember(state.pieces, topId) {
            state.pieces.sortedWith(compareBy {
                when {
                    it.id == topId  -> 2
                    !it.isPlaced    -> 1
                    else            -> 0
                }
            })
        }

        sorted.forEach { piece ->
          key(piece.id) {
            val drag      = dragOffsets[piece.id] ?: Offset.Zero
            val tileWPx   = widthPx  / state.size
            val tileHPx   = heightPx / state.size
            val centerX   = piece.x * widthPx  + drag.x
            val centerY   = piece.y * heightPx + drag.y
            val leftPx    = centerX - tileWPx / 2f
            val topPx     = centerY - tileHPx / 2f
            val tileDpW   = with(density) { tileWPx.toDp() }
            val tileDpH   = with(density) { tileHPx.toDp() }
            val isDragging = dragOffsets.containsKey(piece.id)
            val shape     = RoundedCornerShape(4.dp)

            Box(
                Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .size(tileDpW, tileDpH)
                    .shadow(
                        elevation = if (isDragging) 12.dp else if (!piece.isPlaced) 3.dp else 0.dp,
                        shape     = shape
                    )
                    .clip(shape)
                    .border(
                        width = if (piece.isPlaced) 2.dp else 1.dp,
                        color = if (piece.isPlaced) Color(0xFF4CAF50) else Color(0x55000000),
                        shape = shape
                    )
                    .pointerInput(piece.id, piece.isPlaced) {
                        if (piece.isPlaced) return@pointerInput
                        detectDragGestures(
                            onDragStart = { topId = piece.id },
                            onDrag      = { change, amount ->
                                change.consume()
                                dragOffsets[piece.id] =
                                    (dragOffsets[piece.id] ?: Offset.Zero) + amount
                            },
                            onDragEnd   = {
                                val off  = dragOffsets.remove(piece.id) ?: Offset.Zero
                                val newX = (piece.x * widthPx  + off.x) / widthPx
                                val newY = (piece.y * heightPx + off.y) / heightPx
                                topId = -1
                                onPieceDropped(piece.id, newX, newY)
                            },
                            onDragCancel = {
                                dragOffsets.remove(piece.id)
                                topId = -1
                            }
                        )
                    }
            ) {
                PieceTile(
                    piece     = piece,
                    boardSize = state.size,
                    bitmap    = bitmap,
                    modifier  = Modifier.fillMaxSize()
                )
            }
          } // key
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Renders the correct section of the image for one piece
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PieceTile(
    piece    : PuzzlePiece,
    boardSize: Int,
    bitmap   : ImageBitmap?,
    modifier : Modifier = Modifier
) {
    val row = piece.id / boardSize
    val col = piece.id % boardSize

    if (bitmap != null) {
        Canvas(modifier) {
            val srcW = (bitmap.width.toFloat()  / boardSize).toInt()
            val srcH = (bitmap.height.toFloat() / boardSize).toInt()
            drawImage(
                image     = bitmap,
                srcOffset = IntOffset(col * srcW, row * srcH),
                srcSize   = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize   = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    } else {
        BoxWithConstraints(modifier.clipToBounds()) {
            val tW = maxWidth
            val tH = maxHeight
            RoseImage(
                modifier = Modifier
                    .size(tW * boardSize, tH * boardSize)
                    .offset(x = -tW * col, y = -tH * row)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fallback rose image (Canvas, no network required)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RoseImage(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF0F5), Color(0xFFFFE4EC), Color(0xFFFFF0F5)),
                startY = 0f, endY = h
            )
        )

        val stemPath = Path().apply {
            moveTo(w * 0.50f, h * 0.96f)
            cubicTo(w * 0.49f, h * 0.85f, w * 0.51f, h * 0.75f, w * 0.50f, h * 0.62f)
        }
        drawPath(stemPath, Color(0xFF2E7D32), style = Stroke(width = w * 0.030f, cap = StrokeCap.Round))

        val leftLeaf = Path().apply {
            moveTo(w * 0.50f, h * 0.78f)
            cubicTo(w * 0.28f, h * 0.66f, w * 0.18f, h * 0.82f, w * 0.32f, h * 0.86f)
            cubicTo(w * 0.40f, h * 0.83f, w * 0.46f, h * 0.81f, w * 0.50f, h * 0.78f)
        }
        drawPath(leftLeaf, Color(0xFF388E3C))
        drawPath(leftLeaf, Color(0xFF2E7D32), style = Stroke(width = w * 0.008f))

        val rightLeaf = Path().apply {
            moveTo(w * 0.50f, h * 0.70f)
            cubicTo(w * 0.72f, h * 0.58f, w * 0.82f, h * 0.74f, w * 0.68f, h * 0.78f)
            cubicTo(w * 0.60f, h * 0.76f, w * 0.54f, h * 0.73f, w * 0.50f, h * 0.70f)
        }
        drawPath(rightLeaf, Color(0xFF43A047))
        drawPath(rightLeaf, Color(0xFF2E7D32), style = Stroke(width = w * 0.008f))

        val cx = w * 0.50f
        val cy = h * 0.36f
        val r  = minOf(w, h) * 0.295f

        repeat(5) { i ->
            val angle = (i * 72.0 - 90.0) * PI / 180.0
            drawCircle(
                color  = Color(0xFFBF1650),
                radius = r * 0.60f,
                center = Offset(cx + (r * 0.85f * cos(angle)).toFloat(), cy + (r * 0.85f * sin(angle)).toFloat())
            )
        }
        repeat(5) { i ->
            val angle = (i * 72.0 - 54.0) * PI / 180.0
            drawCircle(
                color  = Color(0xFFE91E63),
                radius = r * 0.52f,
                center = Offset(cx + (r * 0.52f * cos(angle)).toFloat(), cy + (r * 0.52f * sin(angle)).toFloat())
            )
        }
        repeat(5) { i ->
            val angle = (i * 72.0 - 18.0) * PI / 180.0
            drawCircle(
                color  = Color(0xFFF06292),
                radius = r * 0.38f,
                center = Offset(cx + (r * 0.26f * cos(angle)).toFloat(), cy + (r * 0.26f * sin(angle)).toFloat())
            )
        }

        drawCircle(Color(0xFF880E4F), radius = r * 0.20f, center = Offset(cx, cy))
        drawCircle(Color(0xFFC2185B), radius = r * 0.12f, center = Offset(cx, cy))
        drawCircle(Color(0x66FFFFFF), radius = r * 0.08f,
            center = Offset(cx - r * 0.06f, cy - r * 0.08f))

        listOf(Offset(w * 0.30f, h * 0.55f), Offset(w * 0.72f, h * 0.48f), Offset(w * 0.20f, h * 0.40f))
            .forEach { pos ->
                drawCircle(Color(0x88AADDFF), radius = w * 0.018f, center = pos)
                drawCircle(Color(0x44FFFFFF), radius = w * 0.008f,
                    center = Offset(pos.x - w * 0.006f, pos.y - w * 0.006f))
            }
    }
}

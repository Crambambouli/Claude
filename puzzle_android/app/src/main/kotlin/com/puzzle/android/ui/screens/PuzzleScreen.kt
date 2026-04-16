package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.puzzle.android.game.EdgeType
import com.puzzle.android.game.JigsawPiece
import com.puzzle.android.game.JigsawShapeGenerator
import com.puzzle.android.game.JigsawState
import com.puzzle.android.game.PieceDefinition
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
    val jigsaw      by vm.jigsaw.collectAsState()
    val bitmap      by vm.bitmap.collectAsState()
    val definitions by vm.definitions.collectAsState()

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
                            text     = "${state.placedCount}/${state.total} Teile",
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
        if (state == null || definitions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Kein Puzzle geladen")
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Win banner ───────────────────────────────────────────────────
            AnimatedVisibility(visible = state.isSolved, enter = fadeIn(), exit = fadeOut()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text       = "Puzzle gelöst! ${state.total} Teile",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier   = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }

            // ── Main play area ───────────────────────────────────────────────
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val widthPx  = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val density  = LocalDensity.current

                val boardFrac = JigsawState.BOARD_FRACTION
                val boardW    = widthPx * boardFrac
                val cellWPx   = boardW / state.cols
                val cellHPx   = heightPx / state.rows

                // Tab padding in pixels (max protrusion is TAB_PEAK_FRACTION × edgeLen)
                val tabPadW   = cellWPx * JigsawShapeGenerator.TAB_PEAK_FRACTION
                val tabPadH   = cellHPx * JigsawShapeGenerator.TAB_PEAK_FRACTION

                // Pre-compute paths at display cell size
                val paths = remember(state.rows, state.cols, cellWPx, cellHPx) {
                    definitions.associateBy(
                        { it.row * state.cols + it.col },
                        { JigsawShapeGenerator.createPiecePath(it, cellWPx, cellHPx) }
                    )
                }

                // Drag state
                val dragOffsets = remember { mutableStateMapOf<Int, Offset>() }
                var topId by remember { mutableIntStateOf(-1) }

                // ── Board background & ghost grid ────────────────────────────
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Tray background
                    drawRect(
                        color   = Color(0xFFF0F0F8),
                        topLeft = Offset(boardW, 0f),
                        size    = Size(widthPx - boardW, heightPx)
                    )
                    // Ghost grid on board
                    for (r in 0 until state.rows) {
                        for (c in 0 until state.cols) {
                            val placed = state.pieces.any {
                                it.definition.row == r && it.definition.col == c && it.isPlaced
                            }
                            drawRect(
                                color   = if (placed) Color(0x1800C853) else Color(0x12000000),
                                topLeft = Offset(c * cellWPx, r * cellHPx),
                                size    = Size(cellWPx, cellHPx),
                                style   = Stroke(1.5f)
                            )
                        }
                    }
                    // Tray divider line
                    drawLine(
                        color       = Color(0x33000000),
                        start       = Offset(boardW, 0f),
                        end         = Offset(boardW, heightPx),
                        strokeWidth = 1f
                    )
                }

                // ── Pieces ───────────────────────────────────────────────────
                // Sort: placed → unplaced → actively dragged
                val sorted = remember(state.pieces, topId) {
                    state.pieces.sortedWith(compareBy {
                        when {
                            it.id == topId -> 2
                            !it.isPlaced   -> 1
                            else           -> 0
                        }
                    })
                }

                sorted.forEach { piece ->
                    key(piece.id) {
                        val drag    = dragOffsets[piece.id] ?: Offset.Zero
                        val def     = piece.definition
                        val path    = paths[piece.id] ?: return@key
                        val isDragging = dragOffsets.containsKey(piece.id)

                        // Screen center of this piece's grid cell
                        val screenCX = piece.x * widthPx + drag.x
                        val screenCY = piece.y * heightPx + drag.y

                        // Canvas size includes tab padding on all sides
                        val padLeft = if (def.left   == EdgeType.TAB) tabPadW else 0f
                        val padTop  = if (def.top    == EdgeType.TAB) tabPadH else 0f
                        val padRight= if (def.right  == EdgeType.TAB) tabPadW else 0f
                        val padBot  = if (def.bottom == EdgeType.TAB) tabPadH else 0f
                        val canvasW = padLeft + cellWPx + padRight
                        val canvasH = padTop  + cellHPx + padBot

                        val leftPx  = screenCX - cellWPx / 2f - padLeft
                        val topPx   = screenCY - cellHPx / 2f - padTop

                        val canvasDpW = with(density) { canvasW.toDp() }
                        val canvasDpH = with(density) { canvasH.toDp() }
                        val shadowElev = if (isDragging) 10.dp else if (!piece.isPlaced) 3.dp else 1.dp

                        // Offset path to account for tab padding
                        val offsetPath = remember(path, padLeft, padTop) {
                            Path().apply { addPath(path, Offset(padLeft, padTop)) }
                        }

                        Box(
                            Modifier
                                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                                .size(canvasDpW, canvasDpH)
                                .shadow(shadowElev, RoundedCornerShape(3.dp))
                                .clipToBounds()
                                .pointerInput(piece.id, piece.isPlaced) {
                                    if (piece.isPlaced) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { topId = piece.id },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsets[piece.id] =
                                                (dragOffsets[piece.id] ?: Offset.Zero) + amount
                                        },
                                        onDragEnd = {
                                            val off  = dragOffsets.remove(piece.id) ?: Offset.Zero
                                            val newX = (piece.x * widthPx  + off.x) / widthPx
                                            val newY = (piece.y * heightPx + off.y) / heightPx
                                            topId = -1
                                            vm.onPieceDropped(piece.id, newX, newY)
                                        },
                                        onDragCancel = {
                                            dragOffsets.remove(piece.id)
                                            topId = -1
                                        }
                                    )
                                }
                        ) {
                            PieceCanvas(
                                piece     = piece,
                                def       = def,
                                path      = offsetPath,
                                bitmap    = bitmap,
                                canvasW   = canvasW,
                                canvasH   = canvasH,
                                padLeft   = padLeft,
                                padTop    = padTop,
                                cellWPx   = cellWPx,
                                cellHPx   = cellHPx,
                                totalCols = state.cols,
                                totalRows = state.rows,
                                isPlaced  = piece.isPlaced,
                                isDragging= isDragging
                            )
                        }
                    }
                }
            }

            // ── Bottom controls ──────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick  = { vm.backToSetup(); navController.popBackStack() },
                    modifier = Modifier.height(44.dp)
                ) { Text("Motiv") }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick  = vm::newGame,
                    modifier = Modifier.height(44.dp)
                ) { Text("Neu mischen") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Renders one puzzle piece clipped to its jigsaw shape
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PieceCanvas(
    piece    : JigsawPiece,
    def      : PieceDefinition,
    path     : Path,
    bitmap   : ImageBitmap?,
    canvasW  : Float,
    canvasH  : Float,
    padLeft  : Float,
    padTop   : Float,
    cellWPx  : Float,
    cellHPx  : Float,
    totalCols: Int,
    totalRows: Int,
    isPlaced : Boolean,
    isDragging: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        clipPath(path) {
            if (bitmap != null) {
                // Source rect covers cell + tab padding in image pixel coords
                val scaleX   = bitmap.width.toFloat()  / (totalCols * cellWPx)
                val scaleY   = bitmap.height.toFloat() / (totalRows * cellHPx)
                val srcLeft  = (def.col * cellWPx - padLeft) * scaleX
                val srcTop   = (def.row * cellHPx - padTop)  * scaleY
                val srcW     = (canvasW * scaleX).toInt().coerceAtLeast(1)
                val srcH     = (canvasH * scaleY).toInt().coerceAtLeast(1)

                drawImage(
                    image     = bitmap,
                    srcOffset = IntOffset(srcLeft.toInt().coerceAtLeast(0), srcTop.toInt().coerceAtLeast(0)),
                    srcSize   = IntSize(
                        srcW.coerceAtMost(bitmap.width  - srcLeft.toInt().coerceAtLeast(0)),
                        srcH.coerceAtMost(bitmap.height - srcTop.toInt().coerceAtLeast(0))
                    ),
                    dstOffset = IntOffset.Zero,
                    dstSize   = IntSize(size.width.toInt(), size.height.toInt())
                )
            } else {
                // Fallback: rose image section
                drawRect(Color(0xFFFFF0F5))
            }
        }

        // Border
        val borderColor = when {
            isPlaced   -> Color(0xFF4CAF50)
            isDragging -> MaterialTheme.colorScheme.primary.let { Color(0xFF1976D2) }
            else       -> Color(0x66000000)
        }
        drawPath(path, color = borderColor, style = Stroke(width = if (isPlaced) 2.5f else 1.5f))
    }
}

// Keep RoseImage as fallback
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

        val stemPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.50f, h * 0.96f)
            cubicTo(w * 0.49f, h * 0.85f, w * 0.51f, h * 0.75f, w * 0.50f, h * 0.62f)
        }
        drawPath(stemPath, Color(0xFF2E7D32), style = Stroke(width = w * 0.030f, cap = StrokeCap.Round))

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
            drawCircle(color = Color(0xFFE91E63), radius = r * 0.52f,
                center = Offset(cx + (r * 0.52f * cos(angle)).toFloat(), cy + (r * 0.52f * sin(angle)).toFloat()))
        }
        drawCircle(Color(0xFF880E4F), radius = r * 0.20f, center = Offset(cx, cy))
        drawCircle(Color(0xFFC2185B), radius = r * 0.12f, center = Offset(cx, cy))
        drawCircle(Color(0x66FFFFFF), radius = r * 0.08f, center = Offset(cx - r * 0.06f, cy - r * 0.08f))
    }
}

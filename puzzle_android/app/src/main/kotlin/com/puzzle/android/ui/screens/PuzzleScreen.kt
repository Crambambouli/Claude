package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.roundToInt

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
                title = { Text("Puzzle", style = MaterialTheme.typography.titleLarge) },
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
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {

                // ── Win banner ───────────────────────────────────────────────
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

                // ── Main play area ───────────────────────────────────────────
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val widthPx  = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val density  = LocalDensity.current

                    val boardFrac = JigsawState.BOARD_FRACTION
                    val boardW    = widthPx * boardFrac
                    val cellWPx   = boardW / state.cols
                    val cellHPx   = heightPx / state.rows

                    val tabPadW   = cellHPx * JigsawShapeGenerator.TAB_PEAK_FRACTION  * 1.5f
                    val tabPadH   = cellWPx * JigsawShapeGenerator.TAB_PEAK_FRACTION  * 1.5f
                    val blankPadW = cellHPx * JigsawShapeGenerator.SHOULDER_FRACTION  * 1.5f
                    val blankPadH = cellWPx * JigsawShapeGenerator.SHOULDER_FRACTION  * 1.5f

                    val paths = remember(state.rows, state.cols, cellWPx, cellHPx) {
                        definitions.associateBy(
                            { it.row * state.cols + it.col },
                            { JigsawShapeGenerator.createPiecePath(it, cellWPx, cellHPx) }
                        )
                    }

                    val dragOffsets = remember { mutableStateMapOf<Int, Offset>() }
                    var topId by remember { mutableIntStateOf(-1) }

                    // ── Board background & ghost grid ────────────────────────
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color   = Color(0xFFF0F0F8),
                            topLeft = Offset(boardW, 0f),
                            size    = Size(widthPx - boardW, heightPx)
                        )
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
                        drawLine(
                            color       = Color(0x33000000),
                            start       = Offset(boardW, 0f),
                            end         = Offset(boardW, heightPx),
                            strokeWidth = 1f
                        )
                    }

                    // ── Board pieces (not in tray) ───────────────────────────
                    val boardPieces = remember(state.pieces, topId) {
                        state.pieces
                            .filter { !it.isInTray }
                            .sortedWith(compareBy {
                                when {
                                    it.id == topId -> 2
                                    !it.isPlaced   -> 1
                                    else           -> 0
                                }
                            })
                    }

                    boardPieces.forEach { piece ->
                        key(piece.id) {
                            val drag       = dragOffsets[piece.id] ?: Offset.Zero
                            val def        = piece.definition
                            val path       = paths[piece.id]
                            val isDragging = dragOffsets.containsKey(piece.id)

                            val (snapFX, snapFY) = state.correctCenter(piece)
                            val animSpec: AnimationSpec<Float> =
                                if (piece.isPlaced) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                else snap()
                            val screenCX by animateFloatAsState(
                                targetValue   = if (piece.isPlaced) snapFX * widthPx  else piece.x * widthPx  + drag.x,
                                animationSpec = animSpec, label = "cx"
                            )
                            val screenCY by animateFloatAsState(
                                targetValue   = if (piece.isPlaced) snapFY * heightPx else piece.y * heightPx + drag.y,
                                animationSpec = animSpec, label = "cy"
                            )

                            if (path != null) {
                                fun edgePadW(e: EdgeType) = when (e) { EdgeType.BLANK -> tabPadW; EdgeType.TAB -> blankPadW; else -> 0f }
                                fun edgePadH(e: EdgeType) = when (e) { EdgeType.BLANK -> tabPadH; EdgeType.TAB -> blankPadH; else -> 0f }
                                val padLeft  = edgePadW(def.left)
                                val padTop   = edgePadH(def.top)
                                val padRight = edgePadW(def.right)
                                val padBot   = edgePadH(def.bottom)
                                val canvasW  = padLeft + cellWPx + padRight
                                val canvasH  = padTop  + cellHPx + padBot

                                val leftPx = screenCX - cellWPx / 2f - padLeft
                                val topPx  = screenCY - cellHPx / 2f - padTop

                                val canvasDpW  = with(density) { canvasW.toDp() }
                                val canvasDpH  = with(density) { canvasH.toDp() }

                                val offsetPath = remember(path, padLeft, padTop) {
                                    Path().apply { addPath(path, Offset(padLeft, padTop)) }
                                }

                                Box(
                                    Modifier
                                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                                        .size(canvasDpW, canvasDpH)
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
                                        piece      = piece,
                                        def        = def,
                                        path       = offsetPath,
                                        bitmap     = bitmap,
                                        canvasW    = canvasW,
                                        canvasH    = canvasH,
                                        padLeft    = padLeft,
                                        padTop     = padTop,
                                        cellWPx    = cellWPx,
                                        cellHPx    = cellHPx,
                                        totalCols  = state.cols,
                                        totalRows  = state.rows,
                                        isPlaced   = piece.isPlaced,
                                        isDragging = isDragging
                                    )
                                }
                            }
                        }
                    }

                    // ── Tray: scrollable thumbnail grid ──────────────────────
                    val trayPieces = remember(state.pieces) {
                        state.pieces.filter { it.isInTray }
                    }

                    val trayWidthDp = with(density) { (widthPx - boardW).toDp() }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(boardW.roundToInt(), 0) }
                            .width(trayWidthDp)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F0F8))
                    ) {
                        if (trayPieces.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text      = "Alle Teile\nauf dem Feld",
                                    style     = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns               = GridCells.Fixed(2),
                                modifier              = Modifier.fillMaxSize(),
                                contentPadding        = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalArrangement   = Arrangement.spacedBy(3.dp)
                            ) {
                                items(trayPieces, key = { it.id }) { piece ->
                                    PieceThumbnail(
                                        piece     = piece,
                                        def       = piece.definition,
                                        bitmap    = bitmap,
                                        totalRows = state.rows,
                                        totalCols = state.cols,
                                        onClick   = { vm.movePieceToBoard(piece.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Bottom controls ──────────────────────────────────────────
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Renders one puzzle piece clipped to its jigsaw shape
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PieceCanvas(
    piece     : JigsawPiece,
    def       : PieceDefinition,
    path      : Path,
    bitmap    : ImageBitmap?,
    canvasW   : Float,
    canvasH   : Float,
    padLeft   : Float,
    padTop    : Float,
    cellWPx   : Float,
    cellHPx   : Float,
    totalCols : Int,
    totalRows : Int,
    isPlaced  : Boolean,
    isDragging: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        clipPath(path) {
            if (bitmap != null) {
                val scaleX  = bitmap.width.toFloat()  / (totalCols * cellWPx)
                val scaleY  = bitmap.height.toFloat() / (totalRows * cellHPx)
                val srcLeft = (def.col * cellWPx - padLeft) * scaleX
                val srcTop  = (def.row * cellHPx - padTop)  * scaleY
                val srcW    = (canvasW * scaleX).toInt().coerceAtLeast(1)
                val srcH    = (canvasH * scaleY).toInt().coerceAtLeast(1)

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
                drawRect(Color(0xFFFFF0F5))
            }
        }

        val borderColor = when {
            isPlaced   -> Color(0xFF4CAF50)
            isDragging -> Color(0xFF1976D2)
            else       -> Color(0x66000000)
        }
        drawPath(path, color = borderColor, style = Stroke(width = if (isPlaced) 3f else if (isDragging) 2.5f else 2f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small tappable thumbnail shown in the tray grid
// ─────────────────────────────────────────────────────────────────────────────

private val thumbnailColors = listOf(
    Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50),
    Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF00BCD4),
    Color(0xFFFF5722), Color(0xFF607D8B)
)

@Composable
private fun PieceThumbnail(
    piece    : JigsawPiece,
    def      : PieceDefinition,
    bitmap   : ImageBitmap?,
    totalRows: Int,
    totalCols: Int,
    onClick  : () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w    = size.width
            val h    = size.height
            val path = JigsawShapeGenerator.createPiecePath(def, w, h)

            clipPath(path) {
                if (bitmap != null) {
                    val scaleX = bitmap.width.toFloat()  / totalCols
                    val scaleY = bitmap.height.toFloat() / totalRows
                    val srcL   = (def.col * scaleX).toInt().coerceAtLeast(0)
                    val srcT   = (def.row * scaleY).toInt().coerceAtLeast(0)
                    val srcW   = scaleX.toInt().coerceAtLeast(1).coerceAtMost(bitmap.width  - srcL)
                    val srcH   = scaleY.toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - srcT)
                    drawImage(
                        image     = bitmap,
                        srcOffset = IntOffset(srcL, srcT),
                        srcSize   = IntSize(srcW, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize   = IntSize(w.toInt(), h.toInt())
                    )
                } else {
                    drawRect(thumbnailColors[piece.id % thumbnailColors.size])
                }
            }
            drawPath(path, Color(0x55000000), style = Stroke(1f))
        }
    }
}

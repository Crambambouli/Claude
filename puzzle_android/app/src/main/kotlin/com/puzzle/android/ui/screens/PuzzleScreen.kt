package com.puzzle.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldLess
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val state = jigsaw
        var isMinimized by remember { mutableStateOf(false) }

        if (state == null || definitions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Kein Puzzle geladen")
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {

                // ── Win banner ───────────────────────────────────────────────
                if (!isMinimized) {
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
                }

                // ── Main play area ───────────────────────────────────────────
                if (!isMinimized) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                        // boardScale / boardOffset live here so piece-drag lambdas capture them
                        var boardScale  by remember { mutableFloatStateOf(1f) }
                        var boardOffset by remember { mutableStateOf(Offset.Zero) }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        boardScale = (boardScale * zoom).coerceIn(0.25f, 5f)
                                        boardOffset += pan
                                    }
                                }
                        ) {
                            val widthPx  = constraints.maxWidth.toFloat()
                            val heightPx = constraints.maxHeight.toFloat()
                            val density  = LocalDensity.current

                            // Board-space cell sizes (unscaled)
                            val boardW   = widthPx * JigsawState.BOARD_FRACTION
                            val cellWPx  = boardW / state.cols
                            val cellHPx  = heightPx / state.rows

                            // Screen-space cell sizes (scale applied)
                            // Quantised so paths only recompute at 5%-steps
                            val scaleKey = (boardScale * 20).roundToInt()
                            val sCellW   = cellWPx * (scaleKey / 20f)
                            val sCellH   = cellHPx * (scaleKey / 20f)

                            val sTabPadW = sCellH * JigsawShapeGenerator.TAB_PEAK_FRACTION * 1.5f
                            val sTabPadH = sCellW * JigsawShapeGenerator.TAB_PEAK_FRACTION * 1.5f

                            val paths = remember(state.rows, state.cols, sCellW, sCellH) {
                                definitions.associateBy(
                                    { it.row * state.cols + it.col },
                                    { JigsawShapeGenerator.createPiecePath(it, sCellW, sCellH) }
                                )
                            }

                            val dragOffsets = remember { mutableStateMapOf<Int, Offset>() }
                            var topId by remember { mutableIntStateOf(-1) }

                            // ── Board background & ghost grid ────────────────
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val ox = boardOffset.x
                                val oy = boardOffset.y
                                for (r in 0 until state.rows) {
                                    for (c in 0 until state.cols) {
                                        val placed = state.pieces.any {
                                            it.definition.row == r && it.definition.col == c && it.isPlaced
                                        }
                                        drawRect(
                                            color   = if (placed) Color(0x1800C853) else Color(0x12000000),
                                            topLeft = Offset(c * sCellW + ox, r * sCellH + oy),
                                            size    = Size(sCellW, sCellH),
                                            style   = Stroke(1.5f)
                                        )
                                    }
                                }
                                val boardLineX = boardW * boardScale + boardOffset.x
                                drawLine(
                                    color       = Color(0x33000000),
                                    start       = Offset(boardLineX, 0f),
                                    end         = Offset(boardLineX, heightPx),
                                    strokeWidth = 1f
                                )
                            }

                            // ── Pieces ───────────────────────────────────────
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

                                    // Compute screen-space centre: board-fraction → board-px → scaled+offset
                                    val targetBX = if (piece.isPlaced) snapFX * widthPx else piece.x * widthPx + drag.x
                                    val targetBY = if (piece.isPlaced) snapFY * heightPx else piece.y * heightPx + drag.y
                                    val screenCX by animateFloatAsState(
                                        targetValue   = targetBX * boardScale + boardOffset.x,
                                        animationSpec = animSpec, label = "cx"
                                    )
                                    val screenCY by animateFloatAsState(
                                        targetValue   = targetBY * boardScale + boardOffset.y,
                                        animationSpec = animSpec, label = "cy"
                                    )

                                    val latestState = rememberUpdatedState(state)
                                    val latestPiece = rememberUpdatedState(piece)

                                    if (path != null) {
                                        fun edgePadW(e: EdgeType) = if (e == EdgeType.BLANK) sTabPadW else 0f
                                        fun edgePadH(e: EdgeType) = if (e == EdgeType.BLANK) sTabPadH else 0f
                                        val padLeft  = edgePadW(def.left)
                                        val padTop   = edgePadH(def.top)
                                        val padRight = edgePadW(def.right)
                                        val padBot   = edgePadH(def.bottom)
                                        val canvasW  = padLeft + sCellW + padRight
                                        val canvasH  = padTop  + sCellH + padBot

                                        val leftPx = screenCX - sCellW / 2f - padLeft
                                        val topPx  = screenCY - sCellH / 2f - padTop

                                        val canvasDpW  = with(density) { canvasW.toDp() }
                                        val canvasDpH  = with(density) { canvasH.toDp() }

                                        val offsetPath = remember(path, padLeft, padTop) {
                                            Path().apply { addPath(path, Offset(padLeft, padTop)) }
                                        }

                                        Box(
                                            Modifier
                                                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                                                .size(canvasDpW, canvasDpH)
                                                .pointerInput(piece.id, piece.isPlaced, piece.groupId) {
                                                    if (piece.isPlaced) return@pointerInput
                                                    detectDragGestures(
                                                        onDragStart = { topId = piece.id },
                                                        onDrag = { change, amount ->
                                                            change.consume()
                                                            val bd = amount / boardScale
                                                            val newOff = (dragOffsets[piece.id] ?: Offset.Zero) + bd
                                                            dragOffsets[piece.id] = newOff
                                                            val gid = latestPiece.value.groupId
                                                            if (gid != null) {
                                                                latestState.value.pieces
                                                                    .filter { it.groupId == gid && it.id != piece.id && !it.isPlaced }
                                                                    .forEach { dragOffsets[it.id] = newOff }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val p    = latestPiece.value
                                                            val off  = dragOffsets.remove(piece.id) ?: Offset.Zero
                                                            val newX = (p.x * widthPx  + off.x) / widthPx
                                                            val newY = (p.y * heightPx + off.y) / heightPx
                                                            topId = -1
                                                            if (p.groupId != null) {
                                                                latestState.value.pieces
                                                                    .filter { it.groupId == p.groupId && it.id != piece.id }
                                                                    .forEach { dragOffsets.remove(it.id) }
                                                                vm.onGroupDropped(piece.id, newX, newY)
                                                            } else {
                                                                vm.onPieceDropped(piece.id, newX, newY)
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            val gid = latestPiece.value.groupId
                                                            if (gid != null) {
                                                                latestState.value.pieces
                                                                    .filter { it.groupId == gid }
                                                                    .forEach { dragOffsets.remove(it.id) }
                                                            } else {
                                                                dragOffsets.remove(piece.id)
                                                            }
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
                                                cellWPx    = sCellW,
                                                cellHPx    = sCellH,
                                                totalCols  = state.cols,
                                                totalRows  = state.rows,
                                                isPlaced   = piece.isPlaced,
                                                isDragging = isDragging
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Vorschaubild (unverändert, immer unten-links) ─
                            val bmp = bitmap
                            if (bmp != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .size(110.dp, 74.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0x44000000), RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawImage(
                                            image     = bmp,
                                            dstOffset = IntOffset.Zero,
                                            dstSize   = IntSize(size.width.toInt(), size.height.toInt())
                                        )
                                    }
                                }
                            }

                            // ── Minimieren / Schließen ───────────────────────
                            Column(
                                modifier            = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                IconButton(
                                    onClick  = { isMinimized = !isMinimized },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.UnfoldLess, contentDescription = "Minimieren",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick  = { vm.backToSetup(); navController.popBackStack() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Schließen",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // ── Tray ────────────────────────────────────────────────────
                val trayPieces = remember(state.pieces) { state.pieces.filter { it.isInTray } }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Color(0xFFF0F0F8))
                ) {
                    if (trayPieces.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text      = "Alle Teile auf dem Feld",
                                style     = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns               = GridCells.Adaptive(minSize = 56.dp),
                            modifier              = Modifier.fillMaxSize(),
                            contentPadding        = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement   = Arrangement.spacedBy(2.dp)
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

                // ── Bottom controls ──────────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick  = { vm.backToSetup(); navController.popBackStack() },
                        modifier = Modifier.height(40.dp)
                    ) { Text("Motiv") }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick  = vm::newGame,
                        modifier = Modifier.height(40.dp)
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
            val bmp = bitmap
            if (bmp != null) {
                val scaleX  = bmp.width.toFloat()  / (totalCols * cellWPx)
                val scaleY  = bmp.height.toFloat() / (totalRows * cellHPx)
                val srcLeft = (def.col * cellWPx - padLeft) * scaleX
                val srcTop  = (def.row * cellHPx - padTop)  * scaleY
                val srcW    = (canvasW * scaleX).toInt().coerceAtLeast(1)
                val srcH    = (canvasH * scaleY).toInt().coerceAtLeast(1)

                drawImage(
                    image     = bmp,
                    srcOffset = IntOffset(srcLeft.toInt().coerceAtLeast(0), srcTop.toInt().coerceAtLeast(0)),
                    srcSize   = IntSize(
                        srcW.coerceAtMost(bmp.width  - srcLeft.toInt().coerceAtLeast(0)),
                        srcH.coerceAtMost(bmp.height - srcTop.toInt().coerceAtLeast(0))
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
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w    = size.width
            val h    = size.height
            val path = JigsawShapeGenerator.createPiecePath(def, w, h)

            clipPath(path) {
                val bmp = bitmap
                if (bmp != null) {
                    val scaleX = bmp.width.toFloat()  / totalCols
                    val scaleY = bmp.height.toFloat() / totalRows
                    val srcL   = (def.col * scaleX).toInt().coerceAtLeast(0)
                    val srcT   = (def.row * scaleY).toInt().coerceAtLeast(0)
                    val srcW   = scaleX.toInt().coerceAtLeast(1).coerceAtMost(bmp.width  - srcL)
                    val srcH   = scaleY.toInt().coerceAtLeast(1).coerceAtMost(bmp.height - srcT)
                    drawImage(
                        image     = bmp,
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

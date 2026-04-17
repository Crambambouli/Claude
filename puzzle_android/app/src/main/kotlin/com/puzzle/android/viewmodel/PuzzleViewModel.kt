package com.puzzle.android.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puzzle.android.data.image.ImageGenerator
import com.puzzle.android.data.image.TestImageGenerator
import com.puzzle.android.data.model.PuzzleCategory
import com.puzzle.android.data.model.PuzzleStyle
import com.puzzle.android.game.JigsawShapeGenerator
import com.puzzle.android.game.JigsawState
import com.puzzle.android.game.PieceDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PuzzleViewModel(application: Application) : AndroidViewModel(application) {

    // ── Setup ────────────────────────────────────────────────────────────────
    private val _category  = MutableStateFlow(PuzzleCategory.BLUMEN)
    val category: StateFlow<PuzzleCategory> = _category.asStateFlow()

    private val _style     = MutableStateFlow(PuzzleStyle.BUNT)
    val style: StateFlow<PuzzleStyle> = _style.asStateFlow()

    // Total piece count (maps to rows × cols in the ViewModel)
    private val _pieceCount = MutableStateFlow(50)
    val pieceCount: StateFlow<Int> = _pieceCount.asStateFlow()

    // ── Loading / error ──────────────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error     = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Game state ───────────────────────────────────────────────────────────
    private val _bitmap      = MutableStateFlow<ImageBitmap?>(null)
    val bitmap: StateFlow<ImageBitmap?> = _bitmap.asStateFlow()

    private val _definitions = MutableStateFlow<List<PieceDefinition>>(emptyList())
    val definitions: StateFlow<List<PieceDefinition>> = _definitions.asStateFlow()

    private val _jigsaw      = MutableStateFlow<JigsawState?>(null)
    val jigsaw: StateFlow<JigsawState?> = _jigsaw.asStateFlow()

    // ── Navigation ───────────────────────────────────────────────────────────
    private val _goToPuzzle = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goToPuzzle = _goToPuzzle.asSharedFlow()

    // ── Setup actions ────────────────────────────────────────────────────────
    fun selectCategory(cat: PuzzleCategory) { _category.value = cat }
    fun selectStyle(s: PuzzleStyle)         { _style.value = s }
    fun selectPieceCount(n: Int)            { _pieceCount.value = n }

    fun generatePuzzle() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val (rows, cols) = pieceCountToGrid(_pieceCount.value)

                // Image loading chain: assets → Pollinations.ai → TestImageGenerator
                val bmp = withContext(Dispatchers.IO) {
                    ImageGenerator.loadFromAssets(getApplication<Application>().assets)
                } ?: withContext(Dispatchers.IO) {
                    try {
                        val url = ImageGenerator.buildUrl(_category.value, _style.value)
                        ImageGenerator.download(url)
                    } catch (_: Exception) { null }
                } ?: withContext(Dispatchers.IO) {
                    TestImageGenerator.create(cols, rows)
                }

                _bitmap.value = bmp.asImageBitmap()

                val defs = JigsawShapeGenerator.generatePuzzle(rows, cols)
                _definitions.value = defs
                _jigsaw.value      = JigsawState.create(rows, cols, defs)
            } catch (e: Exception) {
                _error.value = "Fehler: ${e.message}"
            } finally {
                _isLoading.value = false
                _goToPuzzle.tryEmit(Unit)
            }
        }
    }

    // ── Game actions ─────────────────────────────────────────────────────────
    fun onPieceDropped(id: Int, x: Float, y: Float) {
        _jigsaw.value = _jigsaw.value?.movePiece(id, x, y)
    }

    fun onGroupDropped(leadId: Int, newLeadX: Float, newLeadY: Float) {
        _jigsaw.value = _jigsaw.value?.movePieceWithGroup(leadId, newLeadX, newLeadY)
    }

    fun movePieceToBoard(id: Int) {
        _jigsaw.value = _jigsaw.value?.movePieceToBoard(id)
    }

    fun newGame() {
        val defs = _definitions.value
        val state = _jigsaw.value ?: return
        _jigsaw.value = JigsawState.create(state.rows, state.cols, defs)
    }

    fun backToSetup() {
        _jigsaw.value  = null
        _bitmap.value  = null
        _error.value   = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun pieceCountToGrid(count: Int): Pair<Int, Int> = when (count) {
        50   -> Pair(5,  10)
        100  -> Pair(10, 10)
        200  -> Pair(10, 20)
        400  -> Pair(20, 20)
        850  -> Pair(25, 34)
        else -> Pair(5,  10)
    }
}

package com.puzzle.android.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puzzle.android.data.image.ImageGenerator
import com.puzzle.android.data.model.PuzzleCategory
import com.puzzle.android.data.model.PuzzleStyle
import com.puzzle.android.game.JigsawState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PuzzleViewModel : ViewModel() {

    // ── Setup selections ─────────────────────────────────────────────────────
    private val _category  = MutableStateFlow(PuzzleCategory.BLUMEN)
    val category: StateFlow<PuzzleCategory> = _category.asStateFlow()

    private val _style     = MutableStateFlow(PuzzleStyle.BUNT)
    val style: StateFlow<PuzzleStyle> = _style.asStateFlow()

    private val _boardSize = MutableStateFlow(4)
    val boardSize: StateFlow<Int> = _boardSize.asStateFlow()

    // ── Loading / error ──────────────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error     = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Game state ───────────────────────────────────────────────────────────
    private val _bitmap    = MutableStateFlow<ImageBitmap?>(null)
    val bitmap: StateFlow<ImageBitmap?> = _bitmap.asStateFlow()

    private val _jigsaw    = MutableStateFlow<JigsawState?>(null)
    val jigsaw: StateFlow<JigsawState?> = _jigsaw.asStateFlow()

    // ── Navigation signal ────────────────────────────────────────────────────
    private val _goToPuzzle = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goToPuzzle = _goToPuzzle.asSharedFlow()

    // ── Setup actions ────────────────────────────────────────────────────────
    fun selectCategory(cat: PuzzleCategory) { _category.value = cat }
    fun selectStyle(s: PuzzleStyle)         { _style.value = s }
    fun selectSize(n: Int)                  { _boardSize.value = n }

    fun generatePuzzle() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val url = ImageGenerator.buildUrl(_category.value, _style.value)
                val bmp = ImageGenerator.download(url)
                _bitmap.value = bmp?.asImageBitmap()
            } catch (_: Exception) {
                _error.value  = "Bild konnte nicht geladen werden – Rosenvorlage wird verwendet."
                _bitmap.value = null
            } finally {
                _jigsaw.value    = JigsawState.create(_boardSize.value)
                _isLoading.value = false
                _goToPuzzle.tryEmit(Unit)
            }
        }
    }

    // ── Game actions ─────────────────────────────────────────────────────────
    fun onPieceDropped(id: Int, x: Float, y: Float) {
        _jigsaw.value = _jigsaw.value?.movePiece(id, x, y)
    }

    fun newGame() {
        _jigsaw.value = _jigsaw.value?.let { JigsawState.create(it.size) }
    }

    fun backToSetup() {
        _jigsaw.value  = null
        _bitmap.value  = null
        _error.value   = null
    }
}

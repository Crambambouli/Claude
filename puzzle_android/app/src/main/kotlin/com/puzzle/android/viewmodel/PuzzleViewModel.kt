package com.puzzle.android.viewmodel

import androidx.lifecycle.ViewModel
import com.puzzle.android.game.PuzzleBoard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PuzzleViewModel : ViewModel() {

    private val _board = MutableStateFlow(PuzzleBoard.shuffled())
    val board: StateFlow<PuzzleBoard> = _board.asStateFlow()

    fun onTileTapped(index: Int) {
        _board.value = _board.value.move(index)
    }

    fun newGame() {
        _board.value = PuzzleBoard.shuffled()
    }
}

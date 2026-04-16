package com.puzzle.android.game

import kotlin.math.abs
import kotlin.random.Random

data class PuzzlePiece(
    val id: Int,
    val x: Float,           // center x as fraction of board width  (0..1)
    val y: Float,           // center y as fraction of board height (0..1)
    val isPlaced: Boolean = false
)

data class JigsawState(
    val size: Int,
    val pieces: List<PuzzlePiece>,
    val moveCount: Int = 0
) {
    val isSolved: Boolean get() = pieces.all { it.isPlaced }

    /** Fraction position of the centre of the correct cell for piece [id]. */
    fun correctCenter(id: Int): Pair<Float, Float> {
        val row = id / size
        val col = id % size
        val step = 1f / size
        return Pair((col + 0.5f) * step, (row + 0.5f) * step)
    }

    /**
     * Move piece [id] to (x, y).
     * If the new position is within half a tile of the correct cell the piece
     * snaps into place; otherwise it just moves to the new position.
     */
    fun movePiece(id: Int, x: Float, y: Float): JigsawState {
        val (cx, cy) = correctCenter(id)
        val snapDist = 0.6f / size
        val placed = abs(x - cx) < snapDist && abs(y - cy) < snapDist
        val newX = if (placed) cx else x.coerceIn(0.02f, 0.98f)
        val newY = if (placed) cy else y.coerceIn(0.02f, 0.98f)
        val delta = if (placed && !pieces.first { it.id == id }.isPlaced) 1 else 0
        return copy(
            pieces = pieces.map { if (it.id == id) it.copy(x = newX, y = newY, isPlaced = placed) else it },
            moveCount = moveCount + delta
        )
    }

    companion object {
        fun create(size: Int): JigsawState {
            val rng = Random.Default
            val pieces = (0 until size * size).map { id ->
                PuzzlePiece(
                    id = id,
                    x  = rng.nextFloat() * 0.80f + 0.10f,
                    y  = rng.nextFloat() * 0.80f + 0.10f
                )
            }
            return JigsawState(size = size, pieces = pieces)
        }
    }
}

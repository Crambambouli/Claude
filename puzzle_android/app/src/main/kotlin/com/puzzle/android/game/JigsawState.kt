package com.puzzle.android.game

import kotlin.math.abs
import kotlin.random.Random

data class JigsawPiece(
    val id        : Int,
    val definition: PieceDefinition,
    val x         : Float,           // center x, fraction of play-area width  (0..1)
    val y         : Float,           // center y, fraction of play-area height (0..1)
    val isPlaced  : Boolean = false
)

data class JigsawState(
    val rows  : Int,
    val cols  : Int,
    val pieces: List<JigsawPiece>
) {
    val isSolved    : Boolean get() = pieces.all { it.isPlaced }
    val placedCount : Int     get() = pieces.count { it.isPlaced }
    val total       : Int     get() = rows * cols

    companion object {
        /** Fraction of the total width occupied by the board. */
        const val BOARD_FRACTION = 0.75f
    }

    /** Returns the correct board position (as fractions) for the given piece. */
    fun correctCenter(piece: JigsawPiece): Pair<Float, Float> {
        val cellFracW = BOARD_FRACTION / cols
        val cellFracH = 1f / rows
        val cx = piece.definition.col * cellFracW + cellFracW / 2f
        val cy = piece.definition.row * cellFracH + cellFracH / 2f
        return Pair(cx, cy)
    }

    /**
     * Move piece [id] to fractional position (x, y).
     * Snaps into correct position when close enough; otherwise just repositions.
     */
    fun movePiece(id: Int, x: Float, y: Float): JigsawState {
        val piece = pieces.first { it.id == id }
        val (cx, cy) = correctCenter(piece)

        val snapW = (BOARD_FRACTION / cols) * 0.40f
        val snapH = (1f / rows) * 0.40f
        val snapped = abs(x - cx) < snapW && abs(y - cy) < snapH

        val newX = if (snapped) cx else x.coerceIn(0.01f, 0.99f)
        val newY = if (snapped) cy else y.coerceIn(0.01f, 0.99f)

        return copy(
            pieces = pieces.map {
                if (it.id == id) it.copy(x = newX, y = newY, isPlaced = snapped) else it
            }
        )
    }

    companion object {
        fun create(rows: Int, cols: Int, definitions: List<PieceDefinition>): JigsawState {
            val rng = Random.Default
            val trayLeft  = BOARD_FRACTION + 0.02f
            val trayRight = 0.98f
            val trayW     = trayRight - trayLeft

            // Distribute pieces evenly within tray, with a bit of random jitter
            val pieces = definitions.mapIndexed { idx, def ->
                val col2 = idx % 2
                val row2 = idx / 2
                val trayRows = (definitions.size + 1) / 2
                val baseX = trayLeft + trayW * (col2 + 0.5f) / 2f
                val baseY = (row2 + 0.5f) / trayRows.toFloat()
                val jitterX = (rng.nextFloat() - 0.5f) * trayW * 0.3f
                val jitterY = (rng.nextFloat() - 0.5f) * (1f / trayRows) * 0.4f
                JigsawPiece(
                    id         = idx,
                    definition = def,
                    x          = (baseX + jitterX).coerceIn(trayLeft, trayRight),
                    y          = (baseY + jitterY).coerceIn(0.01f, 0.99f)
                )
            }
            return JigsawState(rows = rows, cols = cols, pieces = pieces)
        }
    }
}

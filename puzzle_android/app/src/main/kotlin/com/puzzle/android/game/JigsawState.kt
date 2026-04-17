package com.puzzle.android.game

import kotlin.math.abs
import kotlin.random.Random

data class JigsawPiece(
    val id        : Int,
    val definition: PieceDefinition,
    val x         : Float,           // center x, fraction of play-area width  (0..1)
    val y         : Float,           // center y, fraction of play-area height (0..1)
    val isPlaced  : Boolean = false
) {
    val isInTray: Boolean get() = !isPlaced && x >= JigsawState.BOARD_FRACTION
}

data class JigsawState(
    val rows  : Int,
    val cols  : Int,
    val pieces: List<JigsawPiece>
) {
    val isSolved    : Boolean get() = pieces.all { it.isPlaced }
    val placedCount : Int     get() = pieces.count { it.isPlaced }
    val total       : Int     get() = rows * cols

    /** Returns the correct board position (as fractions) for the given piece. */
    fun correctCenter(piece: JigsawPiece): Pair<Float, Float> {
        val cellFracW = BOARD_FRACTION / cols.toFloat()
        val cellFracH = 1f / rows.toFloat()
        val cx = piece.definition.col.toFloat() * cellFracW + cellFracW / 2f
        val cy = piece.definition.row.toFloat() * cellFracH + cellFracH / 2f
        return Pair(cx, cy)
    }

    /**
     * Move piece [id] to fractional position (x, y).
     * Priority: 1) absolute grid snap, 2) relative snap to adjacent floating piece, 3) free placement.
     */
    fun movePiece(id: Int, x: Float, y: Float): JigsawState {
        val piece = pieces.first { it.id == id }
        val r = piece.definition.row
        val c = piece.definition.col
        val cellW  = BOARD_FRACTION / cols.toFloat()
        val cellH  = 1f / rows.toFloat()

        // 1. Absolute snap to correct grid position
        val (cx, cy) = correctCenter(piece)
        val absFactor = if (hasAnyPlacedNeighbor(piece)) 0.65f else 0.50f
        if (abs(x - cx) < cellW * absFactor && abs(y - cy) < cellH * absFactor) {
            return copy(pieces = pieces.map {
                if (it.id == id) it.copy(x = cx, y = cy, isPlaced = true) else it
            })
        }

        // 2. Relative snap to an adjacent piece that is on the board (placed or floating)
        val relTarget = pieces
            .filter { n -> !n.isInTray && n.id != id &&
                abs(n.definition.row - r) + abs(n.definition.col - c) == 1 }
            .mapNotNull { n ->
                val tx = n.x + (c - n.definition.col) * cellW
                val ty = n.y + (r - n.definition.row) * cellH
                if (abs(x - tx) < cellW * 0.55f && abs(y - ty) < cellH * 0.55f)
                    Triple(n.isPlaced, tx, ty) else null
            }
            .minByOrNull { t -> abs(x - t.second) + abs(y - t.third) }

        if (relTarget != null) {
            val tx = relTarget.second.coerceIn(0.01f, BOARD_FRACTION - 0.01f)
            val ty = relTarget.third.coerceIn(0.01f, 0.99f)
            return copy(pieces = pieces.map {
                if (it.id == id) it.copy(x = tx, y = ty, isPlaced = relTarget.first) else it
            })
        }

        // 3. Free placement within board area
        val newX = x.coerceIn(0.01f, BOARD_FRACTION - 0.01f)
        val newY = y.coerceIn(0.01f, 0.99f)
        return copy(pieces = pieces.map {
            if (it.id == id) it.copy(x = newX, y = newY, isPlaced = false) else it
        })
    }

    private fun hasAnyPlacedNeighbor(piece: JigsawPiece): Boolean {
        val r = piece.definition.row
        val c = piece.definition.col
        return pieces.any { n ->
            n.isPlaced && (
                (n.definition.row == r     && n.definition.col == c - 1) ||
                (n.definition.row == r     && n.definition.col == c + 1) ||
                (n.definition.row == r - 1 && n.definition.col == c    ) ||
                (n.definition.row == r + 1 && n.definition.col == c    )
            )
        }
    }

    /** Moves a tray piece onto the board at a random position. */
    fun movePieceToBoard(id: Int, rng: Random = Random.Default): JigsawState {
        val piece = pieces.first { it.id == id }
        if (piece.isPlaced) return this
        val x = BOARD_FRACTION * (0.2f + rng.nextFloat() * 0.6f)
        val y = 0.1f + rng.nextFloat() * 0.8f
        return copy(
            pieces = pieces.map {
                if (it.id == id) it.copy(x = x, y = y) else it
            }
        )
    }

    companion object {
        /** Fraction of the total width occupied by the board. */
        const val BOARD_FRACTION = 0.75f

        fun create(rows: Int, cols: Int, definitions: List<PieceDefinition>): JigsawState {
            val rng       = Random.Default
            val trayLeft  = BOARD_FRACTION + 0.02f
            val trayRight = 0.98f
            val trayW     = trayRight - trayLeft

            val pieces = definitions.mapIndexed { idx, def ->
                val col2     = idx % 2
                val row2     = idx / 2
                val trayRows = (definitions.size + 1) / 2
                val baseX    = trayLeft + trayW * (col2.toFloat() + 0.5f) / 2f
                val baseY    = (row2.toFloat() + 0.5f) / trayRows.toFloat()
                val jitterX  = (rng.nextFloat() - 0.5f) * trayW * 0.3f
                val jitterY  = (rng.nextFloat() - 0.5f) * (1f / trayRows.toFloat()) * 0.4f
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

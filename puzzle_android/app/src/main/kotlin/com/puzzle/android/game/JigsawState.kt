package com.puzzle.android.game

import kotlin.math.abs
import kotlin.random.Random

data class JigsawPiece(
    val id        : Int,
    val definition: PieceDefinition,
    val x         : Float,           // center x, fraction of play-area width  (0..1)
    val y         : Float,           // center y, fraction of play-area height (0..1)
    val isPlaced  : Boolean = false,
    val groupId   : Int?    = null   // non-null when snapped to another free piece
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

    fun correctCenter(piece: JigsawPiece): Pair<Float, Float> {
        val cellFracW = BOARD_FRACTION / cols.toFloat()
        val cellFracH = 1f / rows.toFloat()
        val cx = piece.definition.col.toFloat() * cellFracW + cellFracW / 2f
        val cy = piece.definition.row.toFloat() * cellFracH + cellFracH / 2f
        return Pair(cx, cy)
    }

    fun movePiece(id: Int, x: Float, y: Float): JigsawState {
        val piece = pieces.first { it.id == id }
        val r = piece.definition.row
        val c = piece.definition.col
        val cellW = BOARD_FRACTION / cols.toFloat()
        val cellH = 1f / rows.toFloat()

        // 1. Relative snap — adjacent floating piece snaps together
        val relTarget = pieces
            .filter { n -> !n.isInTray && n.id != id &&
                abs(n.definition.row - r) + abs(n.definition.col - c) == 1 }
            .mapNotNull { n ->
                val tx = n.x + (c - n.definition.col) * cellW
                val ty = n.y + (r - n.definition.row) * cellH
                if (abs(x - tx) < cellW * 0.70f && abs(y - ty) < cellH * 0.70f)
                    Pair(n, Pair(tx, ty)) else null
            }
            .minByOrNull { (_, pos) -> abs(x - pos.first) + abs(y - pos.second) }

        if (relTarget != null) {
            val (neighbor, pos) = relTarget
            val tx = pos.first.coerceIn(0.01f, BOARD_FRACTION - 0.01f)
            val ty = pos.second.coerceIn(0.01f, 0.99f)
            val neighborGid = neighbor.groupId
            val dragGid     = piece.groupId
            val newGid      = neighborGid ?: dragGid
                ?: ((pieces.mapNotNull { it.groupId }.maxOrNull() ?: 0) + 1)
            return copy(pieces = pieces.map { p ->
                when {
                    p.id == id                                       -> p.copy(x = tx, y = ty, isPlaced = neighbor.isPlaced, groupId = newGid)
                    dragGid     != null && p.groupId == dragGid      -> p.copy(groupId = newGid)
                    p.id == neighbor.id                              -> p.copy(groupId = newGid)
                    neighborGid != null && p.groupId == neighborGid  -> p.copy(groupId = newGid)
                    else                                             -> p
                }
            })
        }

        // 2. Absolute snap to correct grid cell (40 % threshold, 55 % with placed neighbour)
        val (cx, cy) = correctCenter(piece)
        val absFactor = if (hasAnyPlacedNeighbor(piece)) 0.55f else 0.40f
        if (abs(x - cx) < cellW * absFactor && abs(y - cy) < cellH * absFactor) {
            return copy(pieces = pieces.map {
                if (it.id == id) it.copy(x = cx, y = cy, isPlaced = true, groupId = null) else it
            })
        }

        // 3. Free placement — piece stays exactly where it is dropped
        val newX = x.coerceIn(0.01f, BOARD_FRACTION - 0.01f)
        val newY = y.coerceIn(0.01f, 0.99f)
        return copy(pieces = pieces.map {
            if (it.id == id) it.copy(x = newX, y = newY, isPlaced = false) else it
        })
    }

    // Moves all pieces of the same group together.
    // If the lead piece lands close to its grid cell every group member is placed.
    fun movePieceWithGroup(leadId: Int, newLeadX: Float, newLeadY: Float): JigsawState {
        val lead    = pieces.first { it.id == leadId }
        val groupId = lead.groupId ?: return movePiece(leadId, newLeadX, newLeadY)
        val deltaX  = newLeadX - lead.x
        val deltaY  = newLeadY - lead.y
        val cellW   = BOARD_FRACTION / cols.toFloat()
        val cellH   = 1f / rows.toFloat()
        val (cx, cy) = correctCenter(lead)
        val absFactor = if (hasAnyPlacedNeighbor(lead)) 0.55f else 0.40f
        val snapToGrid = abs(newLeadX - cx) < cellW * absFactor && abs(newLeadY - cy) < cellH * absFactor
        return copy(pieces = pieces.map { p ->
            when {
                p.id == leadId -> if (snapToGrid)
                    p.copy(x = cx, y = cy, isPlaced = true, groupId = null)
                else
                    p.copy(x = newLeadX.coerceIn(0.01f, BOARD_FRACTION - 0.01f),
                           y = newLeadY.coerceIn(0.01f, 0.99f))
                p.groupId == groupId -> if (snapToGrid) {
                    val (pcx, pcy) = correctCenter(p)
                    p.copy(x = pcx, y = pcy, isPlaced = true, groupId = null)
                } else {
                    p.copy(x = (p.x + deltaX).coerceIn(0.01f, BOARD_FRACTION - 0.01f),
                           y = (p.y + deltaY).coerceIn(0.01f, 0.99f))
                }
                else -> p
            }
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

    // Places a tray piece onto the first free grid cell.
    fun movePieceToBoard(id: Int): JigsawState {
        val piece = pieces.first { it.id == id }
        if (piece.isPlaced) return this
        val cellW = BOARD_FRACTION / cols.toFloat()
        val cellH = 1f / rows.toFloat()
        val occupiedKeys = pieces
            .filter { it.id != id && !it.isInTray }
            .map { p ->
                (p.x / cellW).toInt().coerceIn(0, cols - 1) * 10000 +
                (p.y / cellH).toInt().coerceIn(0, rows - 1)
            }.toSet()
        val freeKeys = (0 until rows).flatMap { r ->
            (0 until cols).map { c -> c * 10000 + r }
        }.filter { it !in occupiedKeys }
        val target = if (freeKeys.isNotEmpty()) {
            val key = freeKeys.random()
            val tc = key / 10000
            val tr = key % 10000
            Pair(tc * cellW + cellW / 2f, tr * cellH + cellH / 2f)
        } else {
            val rng = Random.Default
            Pair(BOARD_FRACTION * (0.2f + rng.nextFloat() * 0.6f), 0.1f + rng.nextFloat() * 0.8f)
        }
        return copy(pieces = pieces.map {
            if (it.id == id) it.copy(x = target.first, y = target.second) else it
        })
    }

    fun movePieceToTray(id: Int): JigsawState {
        val piece = pieces.first { it.id == id }
        if (piece.isInTray) return this
        val gid = piece.groupId
        val remainingInGroup = if (gid != null) pieces.count { it.id != id && it.groupId == gid } else 0
        return copy(pieces = pieces.map { p ->
            when {
                p.id == id -> p.copy(x = BOARD_FRACTION + 0.01f, y = 0.5f, isPlaced = false, groupId = null)
                gid != null && p.groupId == gid && remainingInGroup == 1 -> p.copy(groupId = null)
                else -> p
            }
        })
    }

    companion object {
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

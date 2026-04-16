package com.puzzle.android.game

import androidx.compose.ui.graphics.Path
import kotlin.math.sqrt
import kotlin.random.Random

enum class EdgeType { FLAT, TAB, BLANK }

data class PieceDefinition(
    val row : Int,
    val col : Int,
    val top   : EdgeType,
    val right : EdgeType,
    val bottom: EdgeType,
    val left  : EdgeType
)

object JigsawShapeGenerator {

    /**
     * Generates piece definitions for a rows×cols grid.
     * Internal edges are randomly assigned TAB / BLANK; border edges are always FLAT.
     * Complementarity is guaranteed: if piece A has TAB on its right, the neighbour has BLANK on its left.
     */
    fun generatePuzzle(rows: Int, cols: Int, seed: Long = System.currentTimeMillis()): List<PieceDefinition> {
        val rng = Random(seed)

        // hTab[r][c] = true → edge between row r-1 and row r has a tab going DOWN
        // i.e. piece (r-1, c) has TAB on its bottom, piece (r, c) has BLANK on its top
        val hTab = Array(rows - 1) { BooleanArray(cols) { rng.nextBoolean() } }

        // vTab[r][c] = true → edge between col c-1 and col c has a tab going RIGHT
        // i.e. piece (r, c-1) has TAB on its right, piece (r, c) has BLANK on its left
        val vTab = Array(rows) { BooleanArray(cols - 1) { rng.nextBoolean() } }

        return (0 until rows).flatMap { r ->
            (0 until cols).map { c ->
                PieceDefinition(
                    row = r, col = c,
                    top = when {
                        r == 0         -> EdgeType.FLAT
                        hTab[r-1][c]   -> EdgeType.BLANK  // piece above has TAB going down → indent here
                        else           -> EdgeType.TAB    // piece above has BLANK going down → tab going up
                    },
                    bottom = when {
                        r == rows - 1  -> EdgeType.FLAT
                        hTab[r][c]     -> EdgeType.TAB    // our tab going down
                        else           -> EdgeType.BLANK
                    },
                    left = when {
                        c == 0         -> EdgeType.FLAT
                        vTab[r][c-1]   -> EdgeType.BLANK  // piece left has TAB going right → indent here
                        else           -> EdgeType.TAB    // our tab going left
                    },
                    right = when {
                        c == cols - 1  -> EdgeType.FLAT
                        vTab[r][c]     -> EdgeType.TAB    // our tab going right
                        else           -> EdgeType.BLANK
                    }
                )
            }
        }
    }

    /**
     * Builds the outline Path of one piece in a coordinate system where the
     * grid cell occupies (0, 0) → (cellW, cellH).
     * Tabs protrude outside the cell boundary; blanks indent inward.
     * Use TAB_PAD to know how much the path extends beyond the cell boundary.
     */
    fun createPiecePath(def: PieceDefinition, cellW: Float, cellH: Float): Path {
        val path = Path()
        path.moveTo(0f, 0f)

        // Top edge: left→right, outward = UP (0, -1)
        addEdge(path, 0f, 0f, cellW, 0f, def.top, 0f, -1f)

        // Right edge: top→bottom, outward = RIGHT (1, 0)
        addEdge(path, cellW, 0f, cellW, cellH, def.right, 1f, 0f)

        // Bottom edge: right→left, outward = DOWN (0, 1)
        addEdge(path, cellW, cellH, 0f, cellH, def.bottom, 0f, 1f)

        // Left edge: bottom→top, outward = LEFT (-1, 0)
        addEdge(path, 0f, cellH, 0f, 0f, def.left, -1f, 0f)

        path.close()
        return path
    }

    /** Fraction of edge length that the tab protrudes (before 1.15 peak factor). */
    const val TAB_FRACTION = 0.18f

    /** Maximum protrusion as fraction of edge length (TAB_FRACTION × 1.15 peak). */
    const val TAB_PEAK_FRACTION = 0.207f

    private fun addEdge(
        path  : Path,
        x0    : Float, y0: Float,
        x1    : Float, y1: Float,
        edge  : EdgeType,
        outNx : Float, outNy: Float      // unit outward normal
    ) {
        if (edge == EdgeType.FLAT) {
            path.lineTo(x1, y1)
            return
        }

        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        val h   = len * TAB_FRACTION
        val s   = if (edge == EdgeType.TAB) 1f else -1f   // TAB = outward, BLANK = inward

        // Helpers: point on edge at fraction t, optionally displaced outward by nFrac * h
        fun ex(t: Float) = x0 + dx * t
        fun ey(t: Float) = y0 + dy * t
        fun px(t: Float, nf: Float) = ex(t) + outNx * h * nf * s
        fun py(t: Float, nf: Float) = ey(t) + outNy * h * nf * s

        // ── flat section until neck ───────────────────────────────────────────
        path.lineTo(ex(0.34f), ey(0.34f))

        // ── neck out → shoulder ──────────────────────────────────────────────
        path.cubicTo(
            px(0.35f, 0.00f), py(0.35f, 0.00f),
            px(0.37f, 0.55f), py(0.37f, 0.55f),
            px(0.40f, 0.90f), py(0.40f, 0.90f)
        )

        // ── over the bulge peak ──────────────────────────────────────────────
        path.cubicTo(
            px(0.43f, 1.10f), py(0.43f, 1.10f),
            px(0.47f, 1.15f), py(0.47f, 1.15f),
            px(0.50f, 1.15f), py(0.50f, 1.15f)   // peak
        )
        path.cubicTo(
            px(0.53f, 1.15f), py(0.53f, 1.15f),
            px(0.57f, 1.10f), py(0.57f, 1.10f),
            px(0.60f, 0.90f), py(0.60f, 0.90f)
        )

        // ── shoulder → neck back to edge ─────────────────────────────────────
        path.cubicTo(
            px(0.63f, 0.55f), py(0.63f, 0.55f),
            px(0.65f, 0.00f), py(0.65f, 0.00f),
            ex(0.66f),        ey(0.66f)
        )

        // ── flat section to end ───────────────────────────────────────────────
        path.lineTo(x1, y1)
    }
}

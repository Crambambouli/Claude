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

    fun createPiecePath(def: PieceDefinition, cellW: Float, cellH: Float): Path {
        val path = Path()
        path.moveTo(0f, 0f)
        addEdge(path, 0f,    0f,    cellW, 0f,    def.top,    0f,  -1f)
        addEdge(path, cellW, 0f,    cellW, cellH, def.right,  1f,   0f)
        addEdge(path, cellW, cellH, 0f,   cellH,  def.bottom, 0f,   1f)
        addEdge(path, 0f,    cellH, 0f,   0f,     def.left,  -1f,   0f)
        path.close()
        return path
    }

    /** Peak connector protrusion as fraction of edge length. */
    const val TAB_PEAK_FRACTION = 0.36f

    private fun addEdge(
        path  : Path,
        x0    : Float, y0: Float,
        x1    : Float, y1: Float,
        edge  : EdgeType,
        outNx : Float, outNy: Float
    ) {
        if (edge == EdgeType.FLAT) {
            path.lineTo(x1, y1)
            return
        }

        val dx  = x1 - x0
        val dy  = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        // BLANK = outward knob, TAB = inward socket
        val s   = if (edge == EdgeType.BLANK) 1f else -1f

        fun ex(t: Float) = x0 + dx * t
        fun ey(t: Float) = y0 + dy * t
        fun px(t: Float, nf: Float) = ex(t) + outNx * len * nf * s
        fun py(t: Float, nf: Float) = ey(t) + outNy * len * nf * s

        // Flat to left shoulder
        path.lineTo(ex(0.37f), ey(0.37f))

        // Rise to left neck base
        path.cubicTo(
            px(0.38f, 0.00f), py(0.38f, 0.00f),
            px(0.43f, 0.05f), py(0.43f, 0.05f),
            px(0.43f, 0.12f), py(0.43f, 0.12f)
        )

        // Left arc — CP1 at t=0.28 forces head ≈2× wider than neck (mushroom shape)
        path.cubicTo(
            px(0.28f, 0.22f), py(0.28f, 0.22f),
            px(0.40f, 0.36f), py(0.40f, 0.36f),
            px(0.50f, 0.36f), py(0.50f, 0.36f)
        )

        // Right arc — mirror of left
        path.cubicTo(
            px(0.60f, 0.36f), py(0.60f, 0.36f),
            px(0.72f, 0.22f), py(0.72f, 0.22f),
            px(0.57f, 0.12f), py(0.57f, 0.12f)
        )

        // Descent from right neck to right shoulder
        path.cubicTo(
            px(0.57f, 0.05f), py(0.57f, 0.05f),
            px(0.62f, 0.00f), py(0.62f, 0.00f),
            ex(0.63f),         ey(0.63f)
        )

        path.lineTo(x1, y1)
    }
}

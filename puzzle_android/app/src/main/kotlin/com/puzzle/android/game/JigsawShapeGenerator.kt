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
                        hTab[r-1][c]   -> EdgeType.BLANK
                        else           -> EdgeType.TAB
                    },
                    bottom = when {
                        r == rows - 1  -> EdgeType.FLAT
                        hTab[r][c]     -> EdgeType.TAB
                        else           -> EdgeType.BLANK
                    },
                    left = when {
                        c == 0         -> EdgeType.FLAT
                        vTab[r][c-1]   -> EdgeType.BLANK
                        else           -> EdgeType.TAB
                    },
                    right = when {
                        c == cols - 1  -> EdgeType.FLAT
                        vTab[r][c]     -> EdgeType.TAB
                        else           -> EdgeType.BLANK
                    }
                )
            }
        }
    }

    fun createPiecePath(def: PieceDefinition, cellW: Float, cellH: Float): Path {
        val path = Path()
        path.moveTo(0f, 0f)
        // Each edge uses a jitter derived from its shared boundary identifier,
        // so adjacent pieces always get the same jitter → complementary shapes fit.
        addEdge(path, 0f,    0f,    cellW, 0f,    def.top,    0f,  -1f, edgeJitter(def.row - 1, def.col,     true))
        addEdge(path, cellW, 0f,    cellW, cellH, def.right,  1f,   0f, edgeJitter(def.row,     def.col,     false))
        addEdge(path, cellW, cellH, 0f,   cellH,  def.bottom, 0f,   1f, edgeJitter(def.row,     def.col,     true))
        addEdge(path, 0f,    cellH, 0f,   0f,     def.left,  -1f,   0f, edgeJitter(def.row,     def.col - 1, false))
        path.close()
        return path
    }

    /**
     * Upper bound of connector protrusion as fraction of edge length — used for canvas padding.
     * Actual protrusion per edge varies with jitter in range [0.30, 0.42].
     */
    const val TAB_PEAK_FRACTION = 0.42f

    /**
     * Returns a stable value in [-1, 1] for the boundary between two adjacent cells.
     * Both pieces sharing the boundary call this with the same arguments → same jitter.
     */
    private fun edgeJitter(row: Int, col: Int, isHorizontal: Boolean): Float {
        val seed = if (isHorizontal) row * 10007L + col else row + col * 10007L
        return Random(seed).nextFloat() * 2f - 1f
    }

    private fun addEdge(
        path  : Path,
        x0    : Float, y0: Float,
        x1    : Float, y1: Float,
        edge  : EdgeType,
        outNx : Float, outNy: Float,
        jitter: Float = 0f
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

        // Per-edge organic variation — symmetric around t=0.50 so complementary edges match
        val shoulderT  = 0.34f + jitter * 0.03f   // shoulder: 31..37% along edge
        val neckT      = 0.43f + jitter * 0.03f   // neck entry: 40..46%
        val neckNf     = 0.12f + jitter * 0.03f   // neck depth: 9..15% of edge length
        val peakNf     = 0.36f + jitter * 0.06f   // head protrusion: 30..42%
        val cp1T       = neckT - 0.15f            // head CP1: always 15% left of neck
        val headCP2T   = 0.40f + jitter * 0.03f   // head shape: 37..43% (oval ↔ round)
        val rNeckT     = 1.0f - neckT
        val rShoulderT = 1.0f - shoulderT

        // Flat section to left shoulder
        path.lineTo(ex(shoulderT), ey(shoulderT))

        // Smooth rise to left neck
        path.cubicTo(
            px(shoulderT + 0.01f, 0.00f),      py(shoulderT + 0.01f, 0.00f),
            px(neckT,             neckNf * 0.45f), py(neckT,          neckNf * 0.45f),
            px(neckT,             neckNf),      py(neckT,             neckNf)
        )

        // Left arc — CP1 far left (cp1T ≈ 0.28) so head is ≈2× wider than neck
        path.cubicTo(
            px(cp1T,      peakNf * 0.62f), py(cp1T,      peakNf * 0.62f),
            px(headCP2T,  peakNf),         py(headCP2T,  peakNf),
            px(0.50f,     peakNf),         py(0.50f,     peakNf)
        )

        // Right arc — exact mirror of left arc
        path.cubicTo(
            px(1f - headCP2T, peakNf),         py(1f - headCP2T, peakNf),
            px(1f - cp1T,     peakNf * 0.62f), py(1f - cp1T,     peakNf * 0.62f),
            px(rNeckT,        neckNf),          py(rNeckT,        neckNf)
        )

        // Descent from right neck to right shoulder
        path.cubicTo(
            px(rNeckT,             neckNf * 0.45f), py(rNeckT,             neckNf * 0.45f),
            px(rShoulderT - 0.01f, 0.00f),          py(rShoulderT - 0.01f, 0.00f),
            ex(rShoulderT), ey(rShoulderT)
        )

        path.lineTo(x1, y1)
    }
}

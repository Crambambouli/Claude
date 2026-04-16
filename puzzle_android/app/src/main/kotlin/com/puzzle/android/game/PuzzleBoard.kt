package com.puzzle.android.game

import kotlin.math.abs

/**
 * Immutable representation of a sliding puzzle board.
 *
 * Tiles are stored as a flat list: value 0 = empty space, 1…(size²-1) = numbered tiles.
 * Solved state: [1, 2, 3, …, size²-1, 0]
 */
data class PuzzleBoard(
    val size: Int = 4,
    val tiles: List<Int>,
    val moveCount: Int = 0
) {
    val emptyIndex: Int get() = tiles.indexOf(0)

    val isSolved: Boolean
        get() = tiles == (1 until size * size).toList() + listOf(0)

    /** Returns true if the tile at [index] is adjacent to the empty space. */
    fun canMove(index: Int): Boolean {
        val emptyIdx = emptyIndex
        val row     = index   / size
        val col     = index   % size
        val eRow    = emptyIdx / size
        val eCol    = emptyIdx % size
        return (row == eRow && abs(col - eCol) == 1) ||
               (col == eCol && abs(row - eRow) == 1)
    }

    /** Slides the tile at [index] into the empty space. No-op if move is invalid. */
    fun move(index: Int): PuzzleBoard {
        if (!canMove(index)) return this
        val newTiles = tiles.toMutableList()
        val emptyIdx = emptyIndex
        newTiles[emptyIdx] = newTiles[index]
        newTiles[index] = 0
        return copy(tiles = newTiles, moveCount = moveCount + 1)
    }

    companion object {
        fun solved(size: Int = 4): PuzzleBoard {
            val tiles = (1 until size * size).toList() + listOf(0)
            return PuzzleBoard(size = size, tiles = tiles)
        }

        /**
         * Generates a solvable board by applying [shuffleMoves] random moves
         * starting from the solved state.
         */
        fun shuffled(size: Int = 4, shuffleMoves: Int = 300): PuzzleBoard {
            var board    = solved(size)
            var lastMove = -1

            repeat(shuffleMoves) {
                val emptyIdx = board.emptyIndex
                val row      = emptyIdx / size
                val col      = emptyIdx % size

                val neighbors = buildList {
                    if (row > 0)        add(emptyIdx - size)
                    if (row < size - 1) add(emptyIdx + size)
                    if (col > 0)        add(emptyIdx - 1)
                    if (col < size - 1) add(emptyIdx + 1)
                }.filter { it != lastMove }   // avoid immediately reversing last move

                val chosen = neighbors.random()
                lastMove   = board.emptyIndex
                board      = board.move(chosen)
            }
            return board.copy(moveCount = 0)
        }
    }
}

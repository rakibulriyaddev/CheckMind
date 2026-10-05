package com.checkmind.app.ui.game

import com.checkmind.chess.Color
import com.checkmind.chess.Squares

/**
 * The only place that knows how squares map to screen positions.
 * White at the bottom: a1 is bottom-left. Black at the bottom: a1 is top-right.
 * "Display row 0" is the top row, "display col 0" is the left column.
 */
object BoardGeometry {
    fun displayRow(square: Int, player: Color): Int =
        if (player == Color.WHITE) 7 - Squares.rank(square) else Squares.rank(square)

    fun displayCol(square: Int, player: Color): Int =
        if (player == Color.WHITE) Squares.file(square) else 7 - Squares.file(square)

    fun squareFromDisplay(row: Int, col: Int, player: Color): Int =
        if (player == Color.WHITE) Squares.of(col, 7 - row) else Squares.of(7 - col, row)

    /** Top-left corner of a square, in pixels. */
    fun squareOrigin(square: Int, player: Color, boardSize: Float): Pair<Float, Float> {
        val s = boardSize / 8f
        return displayCol(square, player) * s to displayRow(square, player) * s
    }

    fun squareCenter(square: Int, player: Color, boardSize: Float): Pair<Float, Float> {
        val (x, y) = squareOrigin(square, player, boardSize)
        val half = boardSize / 16f
        return x + half to y + half
    }

    /** Square under a pixel position, or null when outside the board. */
    fun squareAt(x: Float, y: Float, boardSize: Float, player: Color): Int? {
        if (x < 0f || y < 0f || x >= boardSize || y >= boardSize) return null
        val s = boardSize / 8f
        val col = (x / s).toInt().coerceIn(0, 7)
        val row = (y / s).toInt().coerceIn(0, 7)
        return squareFromDisplay(row, col, player)
    }
}

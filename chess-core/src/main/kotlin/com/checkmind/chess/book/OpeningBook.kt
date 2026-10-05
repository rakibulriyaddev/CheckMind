package com.checkmind.chess.book

import com.checkmind.chess.Color
import com.checkmind.chess.Move
import com.checkmind.chess.Position

data class BookEdge(val move: Move, val wins: Int)

/**
 * Two position-keyed tables: `white` holds moves White played in games White won, `black` the
 * same for Black. Lookup is by position, so transpositions reach the same hints.
 */
class OpeningBook(
    val white: BookTable,
    val black: BookTable,
    val whiteGames: Int,
    val blackGames: Int,
) {
    fun table(color: Color): BookTable = if (color == Color.WHITE) white else black

    /**
     * Winning moves for [color] in [position], or null when the position is not in the book
     * or [color] is not the side to move. Sorted by wins descending, then move code ascending.
     * Moves that are not legal in [position] (a hash collision) are dropped.
     */
    fun hints(color: Color, position: Position): List<BookEdge>? {
        if (position.sideToMove != color) return null
        val edges = table(color).lookup(PositionKey.of(position)).filter { position.isLegal(it.move) }
        if (edges.isEmpty()) return null
        return edges.sortedWith(compareByDescending<BookEdge> { it.wins }.thenBy { it.move.bits })
    }

    companion object {
        val EMPTY = OpeningBook(BookTable.EMPTY, BookTable.EMPTY, 0, 0)
    }
}

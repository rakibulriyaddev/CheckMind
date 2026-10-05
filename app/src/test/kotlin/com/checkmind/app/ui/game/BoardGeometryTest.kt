package com.checkmind.app.ui.game

import com.checkmind.chess.Color
import com.checkmind.chess.Squares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardGeometryTest {
    private val size = 800f // 100 px per square

    @Test
    fun whiteHasA1BottomLeft() {
        val a1 = Squares.parse("a1")
        assertEquals(7, BoardGeometry.displayRow(a1, Color.WHITE))
        assertEquals(0, BoardGeometry.displayCol(a1, Color.WHITE))
        assertEquals(a1, BoardGeometry.squareAt(50f, 750f, size, Color.WHITE))
        assertEquals(Squares.parse("h8"), BoardGeometry.squareAt(750f, 50f, size, Color.WHITE))
    }

    @Test
    fun blackHasA1TopRight() {
        val a1 = Squares.parse("a1")
        assertEquals(0, BoardGeometry.displayRow(a1, Color.BLACK))
        assertEquals(7, BoardGeometry.displayCol(a1, Color.BLACK))
        assertEquals(a1, BoardGeometry.squareAt(750f, 50f, size, Color.BLACK))
        assertEquals(Squares.parse("h8"), BoardGeometry.squareAt(50f, 750f, size, Color.BLACK))
    }

    @Test
    fun roundTripForAllSquaresBothOrientations() {
        for (player in Color.entries) {
            for (sq in 0..63) {
                val (cx, cy) = BoardGeometry.squareCenter(sq, player, size)
                assertEquals(sq, BoardGeometry.squareAt(cx, cy, size, player))
                val row = BoardGeometry.displayRow(sq, player)
                val col = BoardGeometry.displayCol(sq, player)
                assertEquals(sq, BoardGeometry.squareFromDisplay(row, col, player))
            }
        }
    }

    @Test
    fun outsideBoardIsNull() {
        assertNull(BoardGeometry.squareAt(-1f, 10f, size, Color.WHITE))
        assertNull(BoardGeometry.squareAt(10f, 800f, size, Color.WHITE))
        assertNull(BoardGeometry.squareAt(800f, 10f, size, Color.BLACK))
    }
}

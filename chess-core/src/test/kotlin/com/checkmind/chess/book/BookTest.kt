package com.checkmind.chess.book

import com.checkmind.chess.Color
import com.checkmind.chess.Move
import com.checkmind.chess.Position
import com.checkmind.chess.toSan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTest {
    private fun movesOf(vararg uci: String): List<Move> = uci.map { Move.fromUci(it) }

    private fun after(vararg uci: String): Position = uci.fold(Position.START) { p, m -> p.play(Move.fromUci(m)) }

    private fun tableOf(winner: Color, vararg games: List<Move>): BookTable =
        BookTableBuilder().apply { games.forEach { insert(winner, it) } }.build()

    /** 1.e4 e5 (W win), 1.e4 c5 (W win), 1.d4 d5 (B win) */
    private fun sampleBook(): OpeningBook {
        val white = tableOf(Color.WHITE, movesOf("e2e4", "e7e5"), movesOf("e2e4", "c7c5"))
        val black = tableOf(Color.BLACK, movesOf("d2d4", "d7d5"))
        return OpeningBook(white, black, 2, 1)
    }

    // ---------------------------------------------------------------- lookup

    @Test
    fun countsWinsPerPosition() {
        val book = sampleBook()
        val rootHints = book.hints(Color.WHITE, Position.START)!!
        assertEquals(1, rootHints.size)
        assertEquals(Move.fromUci("e2e4"), rootHints[0].move)
        assertEquals(2, rootHints[0].wins)

        val black = book.hints(Color.BLACK, after("d2d4"))!!
        assertEquals(listOf("d7d5"), black.map { it.move.uci() })
    }

    @Test
    fun onlyTheWinnersOwnMovesAreHints() {
        val book = sampleBook()
        // The opponent's replies (e5, c5) are not recorded in White's table.
        assertNull(book.hints(Color.WHITE, after("e2e4")))
        assertNull(book.hints(Color.BLACK, after("e2e4")))
        // After 1.e4 e5, White has no recorded continuation (the game ended).
        assertNull(book.hints(Color.WHITE, after("e2e4", "e7e5")))
    }

    @Test
    fun wrongSideOrEmptyBookGivesNull() {
        val book = sampleBook()
        assertNull(book.hints(Color.BLACK, Position.START)) // White to move
        assertNotNull(book.hints(Color.WHITE, Position.START))
        assertNull(OpeningBook.EMPTY.hints(Color.WHITE, Position.START))
    }

    @Test
    fun sortedByWinsDescending() {
        val white = tableOf(
            Color.WHITE,
            movesOf("d2d4"),
            movesOf("e2e4"), movesOf("e2e4"), movesOf("e2e4"),
            movesOf("g1f3"), movesOf("g1f3"),
        )
        val hints = OpeningBook(white, BookTable.EMPTY, 6, 0).hints(Color.WHITE, Position.START)!!
        assertEquals(listOf("e2e4", "g1f3", "d2d4"), hints.map { it.move.uci() })
        assertEquals(listOf(3, 2, 1), hints.map { it.wins })
    }

    @Test
    fun transpositionsShareHints() {
        // Same position after 1.Nf3 d5 2.d4 Nf6 and 1.d4 d5 2.Nf3 Nf6.
        val white = tableOf(
            Color.WHITE,
            movesOf("g1f3", "d7d5", "d2d4", "g8f6", "c2c4"),
            movesOf("d2d4", "d7d5", "g1f3", "g8f6", "e2e3"),
        )
        val book = OpeningBook(white, BookTable.EMPTY, 2, 0)
        val viaA = book.hints(Color.WHITE, after("g1f3", "d7d5", "d2d4", "g8f6"))!!
        val viaB = book.hints(Color.WHITE, after("d2d4", "d7d5", "g1f3", "g8f6"))!!
        assertEquals(viaA, viaB)
        assertEquals(setOf("c2c4", "e2e3"), viaA.map { it.move.uci() }.toSet())
        assertEquals(listOf(1, 1), viaA.map { it.wins })
    }

    @Test
    fun winCountSaturates() {
        val b = BookTableBuilder()
        repeat(70_000) { b.insert(Color.WHITE, movesOf("e2e4")) }
        val hints = OpeningBook(b.build(), BookTable.EMPTY, 70_000, 0).hints(Color.WHITE, Position.START)!!
        assertEquals(65_535, hints[0].wins)
    }

    @Test
    fun illegalMoveFromCollisionIsDropped() {
        // An entry whose key matches the start position but whose move is illegal there.
        val key = PositionKey.of(Position.START)
        val illegal = Move.fromUci("e2e5")
        val legal = Move.fromUci("e2e4")
        val packed = longArrayOf(PositionKey.pack(key, legal.bits), PositionKey.pack(key, illegal.bits)).also { it.sort() }
        val table = BookTable(packed, shortArrayOf(1, 1))
        val hints = OpeningBook(table, BookTable.EMPTY, 1, 0).hints(Color.WHITE, Position.START)!!
        assertEquals(listOf(legal), hints.map { it.move })
    }

    @Test
    fun tableStats() {
        val table = tableOf(
            Color.WHITE,
            movesOf("e2e4", "e7e5", "g1f3"),
            movesOf("e2e4", "c7c5", "g1f3"),
        )
        assertEquals(3, table.size) // e4 at the start (2 wins), Nf3 after 1...e5, Nf3 after 1...c5
        assertEquals(3, table.positionCount())
        assertEquals(0, BookTable.EMPTY.positionCount())
        assertEquals(0, BookTable.EMPTY.size)
    }

    // ---------------------------------------------------------------- position key

    @Test
    fun keyDiffersByPlacementSideCastlingAndEnPassant() {
        val start = PositionKey.of(Position.START)
        assertNotEquals(start, PositionKey.of(after("e2e4")))
        assertNotEquals(PositionKey.of(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w Q - 0 1")), PositionKey.of(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")))
        assertNotEquals(PositionKey.of(Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")), PositionKey.of(Position.fromFen("4k3/8/8/8/8/8/8/4K3 b - - 0 1")))
        // En passant matters only when a pawn can capture.
        val capturable = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val noEp = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 1")
        assertNotEquals(PositionKey.of(capturable), PositionKey.of(noEp))
        val notCapturable = Position.fromFen("4k3/8/8/3p4/8/8/8/4K3 w - d6 0 1")
        val notCapturableNoEp = Position.fromFen("4k3/8/8/3p4/8/8/8/4K3 w - - 0 1")
        assertEquals(PositionKey.of(notCapturable), PositionKey.of(notCapturableNoEp))
    }

    @Test
    fun keyIgnoresClocksAndFitsIn47Bits() {
        val a = Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val b = Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 17 40")
        assertEquals(PositionKey.of(a), PositionKey.of(b))
        assertTrue(PositionKey.of(Position.START) in 0 until (1L shl PositionKey.BITS))
    }

    @Test
    fun keyIsStableAcrossRuns() {
        // Golden value: the builder and the app must always agree. If this fails you changed the
        // key layout or seed: bump BookCodec.VERSION and rebuild book.bin.
        assertEquals(GOLDEN_START_KEY, PositionKey.of(Position.START))
    }

    // ---------------------------------------------------------------- codec

    @Test
    fun codecRoundTrip() {
        val book = sampleBook()
        val back = BookCodec.read(BookCodec.write(book))
        assertEquals(book.white, back.white)
        assertEquals(book.black, back.black)
        assertEquals(2, back.whiteGames)
        assertEquals(1, back.blackGames)
        assertEquals(2, back.hints(Color.WHITE, Position.START)!![0].wins)
    }

    @Test
    fun codecGoldenBytes() {
        // Single white game 1.e4 from the start position, black table empty.
        val book = OpeningBook(tableOf(Color.WHITE, movesOf("e2e4")), BookTable.EMPTY, 1, 0)
        val packed = PositionKey.pack(GOLDEN_START_KEY, Move.fromUci("e2e4").bits)
        val expected = java.io.ByteArrayOutputStream().apply {
            write(byteArrayOf('C'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte()))
            write(byteArrayOf(3, 0, 1, 0, 0)) // version, whiteGames, blackGames
            write(byteArrayOf(0, 0, 0, 1)) // white: entryCount
            write(java.nio.ByteBuffer.allocate(8).putLong(packed).array())
            write(byteArrayOf(0, 1)) // wins
            write(byteArrayOf(0, 0, 0, 0)) // black: entryCount
        }.toByteArray()
        assertArrayEquals(expected, BookCodec.write(book))
    }

    @Test
    fun codecRejectsBadInput() {
        val good = BookCodec.write(sampleBook())
        assertThrows(BookFormatException::class.java) { BookCodec.read(good.copyOf(3)) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(good.copyOf(good.size - 1)) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(good + byteArrayOf(0)) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(good.copyOf().also { it[0] = 'X'.code.toByte() }) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(good.copyOf().also { it[4] = 2 }) } // old version
    }

    @Test
    fun codecRejectsUnsortedAndZeroWins() {
        fun table(vararg entries: Pair<Long, Int>): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf('C'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 3, 0, 2, 0, 0))
            out.write(java.nio.ByteBuffer.allocate(4).putInt(entries.size).array())
            for ((p, _) in entries) out.write(java.nio.ByteBuffer.allocate(8).putLong(p).array())
            for ((_, w) in entries) out.write(java.nio.ByteBuffer.allocate(2).putShort(w.toShort()).array())
            out.write(byteArrayOf(0, 0, 0, 0))
            return out.toByteArray()
        }
        assertNotNull(BookCodec.read(table(5L to 1, 9L to 2)))
        assertThrows(BookFormatException::class.java) { BookCodec.read(table(9L to 1, 5L to 1)) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(table(5L to 1, 5L to 1)) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(table(5L to 0)) }
        assertThrows(BookFormatException::class.java) { BookCodec.read(table(-1L to 1)) }
    }

    @Test
    fun hintsMatchRealGame() {
        val white = tableOf(Color.WHITE, movesOf("e2e4", "e7e5", "g1f3", "b8c6"))
        val book = OpeningBook(white, BookTable.EMPTY, 1, 0)
        val pos = after("e2e4", "e7e5")
        val hints = book.hints(Color.WHITE, pos)!!
        assertEquals("Nf3", pos.toSan(hints[0].move))
    }

    @Test
    fun bigRandomBookRoundTripAndLookup() {
        // Many games from a small move pool: counts at the start position must add up.
        val rnd = java.util.Random(7)
        val pool = listOf("e2e4", "d2d4", "g1f3", "c2c4")
        val b = BookTableBuilder()
        repeat(5_000) { b.insert(Color.WHITE, listOf(Move.fromUci(pool[rnd.nextInt(pool.size)]))) }
        val book = OpeningBook(b.build(), BookTable.EMPTY, 5_000, 0)
        val back = BookCodec.read(BookCodec.write(book))
        assertEquals(book.white, back.white)
        assertEquals(5_000, back.hints(Color.WHITE, Position.START)!!.sumOf { it.wins })
    }

    private companion object {
        /** Key of the start position, pinned by keyIsStableAcrossRuns. */
        const val GOLDEN_START_KEY = 114042949876215L
    }
}

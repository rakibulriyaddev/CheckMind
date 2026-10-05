package com.checkmind.book

import com.checkmind.chess.Color
import com.checkmind.chess.Move
import com.checkmind.chess.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BookBuilderTest {
    private fun build(pgn: String) = BookBuilder.build(PgnParser.parse(pgn))

    private fun afterMoves(vararg uci: String) = uci.fold(Position.START) { p, m -> p.play(Move.fromUci(m)) }

    // ---------------------------------------------------------------- parser

    @Test
    fun parserHandlesChessComStyleGame() {
        val pgn = """
            [Event "Live Chess"]
            [White "me"]
            [Black "x"]
            [Result "1-0"]

            1. e4 {[%clk 0:09:58]} 1... e5 {[%clk 0:09:57]} 2. Nf3 ${'$'}1 Nc6 3. Bb5 (3. Bc4 Bc5 {a comment}) 3... a6! ; rest of line
            4. Ba4 Nf6 5. 0-0 Be7 1-0
        """.trimIndent()
        val games = PgnParser.parse(pgn)
        assertEquals(1, games.size)
        val g = games[0]
        assertEquals("1-0", g.termination)
        assertEquals("1-0", g.tags["Result"])
        assertEquals(
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7"),
            g.moves,
        )
    }

    @Test
    fun parserSplitsMultipleGames() {
        val pgn = """
            [Result "1-0"]

            1. e4 e5 1-0

            [Result "0-1"]

            1. f3 e5 2. g4 Qh4# 0-1
        """.trimIndent()
        val games = PgnParser.parse(pgn)
        assertEquals(2, games.size)
        assertEquals(listOf("e4", "e5"), games[0].moves)
        assertEquals("0-1", games[1].termination)
        assertEquals(4, games[1].moves.size)
    }

    @Test
    fun parserFallsBackToTagWhenNoTerminator() {
        val games = PgnParser.parse("[Result \"1-0\"]\n\n1. e4 e5\n")
        assertEquals(1, games.size)
        assertNull(games[0].termination)
        assertEquals("1-0", games[0].tags["Result"])
    }

    // ---------------------------------------------------------------- builder

    @Test
    fun buildsTriesFromWinsOnly() {
        val (book, summary) = build(
            """
            [Result "1-0"]

            1. e4 e5 1-0

            [Result "1-0"]

            1. e4 c5 1-0

            [Result "0-1"]

            1. d4 d5 0-1

            [Result "1/2-1/2"]

            1. c4 c5 1/2-1/2
            """.trimIndent(),
        )
        assertEquals(2, summary.whiteGames)
        assertEquals(1, summary.blackGames)
        assertEquals(1, summary.skipped.size)
        assertEquals(2, book.whiteGames)

        val rootHints = book.hints(Color.WHITE, Position.START)!!
        assertEquals(Move.fromUci("e2e4"), rootHints[0].move)
        assertEquals(2, rootHints[0].wins)
        // Black won 1.d4 d5: its reply is a hint after 1.d4, and the drawn 1.c4 game is not used.
        assertEquals(Move.fromUci("d7d5"), book.hints(Color.BLACK, afterMoves("d2d4"))!![0].move)
        assertNull(book.hints(Color.BLACK, afterMoves("c2c4")))
        // White's table holds only White's own moves: nothing was recorded after 1.e4 e5.
        assertNull(book.hints(Color.WHITE, afterMoves("e2e4", "e7e5")))
        assertEquals(1, summary.whiteEntries)
        assertEquals(1, summary.blackEntries)
    }

    @Test
    fun acceptsMatesPromotionsCastlingAndDisambiguation() {
        val (_, summary) = build(
            """
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. O-O Nxe4 5. Re1 d5 6. Bxd5 Qxd5 7. Nc3 Qa5 8. Nxe4 Be6 9. Nc3 O-O-O 10. d4 1-0
            """.trimIndent(),
        )
        assertEquals(1, summary.whiteGames)
        assertEquals(19, summary.deepestPlies)
    }

    @Test
    fun illegalMoveFailsWithGameNumber() {
        val e = assertThrows(BookBuildException::class.java) {
            build("[Result \"1-0\"]\n\n1. e4 e5 2. Ke3 1-0\n")
        }
        assertTrue(e.message!!.contains("game 1"))
        assertTrue(e.message!!.contains("Ke3"))
    }

    @Test
    fun fenGameFails() {
        assertThrows(BookBuildException::class.java) {
            build("[SetUp \"1\"]\n[FEN \"8/8/8/8/8/8/8/K6k w - - 0 1\"]\n[Result \"1-0\"]\n\n1. Ka2 1-0\n")
        }
    }

    @Test
    fun resultMismatchFails() {
        assertThrows(BookBuildException::class.java) {
            build("[Result \"0-1\"]\n\n1. e4 e5 1-0\n")
        }
    }

    @Test
    fun nonStandardVariantSkipped() {
        val (_, summary) = build("[Variant \"Chess960\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0\n")
        assertEquals(0, summary.whiteGames)
        assertEquals(1, summary.skipped.size)
    }
}

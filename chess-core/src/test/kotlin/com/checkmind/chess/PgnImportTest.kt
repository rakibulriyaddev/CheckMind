package com.checkmind.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnImportTest {
    private fun sanOf(text: String): List<String> = Game().also { g -> parsePgnMoves(text).forEach(g::play) }.sanMoves()

    @Test
    fun readsTheGameFromTheRequest() {
        val pgn = """
            1. g4 d5 2. e3 e5 3. Nc3 Bb4 4. Bg2 Bxc3 5. dxc3 e4 6. h3 Ne7 7. Ne2 Nbc6 8. Nd4
            Nxd4 9. exd4 b6 10. Be3 Qd6 11. Qd2 *
        """.trimIndent()
        val moves = parsePgnMoves(pgn)
        assertEquals(21, moves.size)
        val game = Game().also { g -> moves.forEach(g::play) }
        assertEquals(Color.BLACK, game.position.sideToMove)
        assertEquals("Qd2", game.sanMoves().last())
        assertEquals(GameStatus.Ongoing, game.status)
    }

    @Test
    fun roundTripsOurOwnExport() {
        val game = Game()
        for (uci in listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1", "f6e4")) game.play(Move.fromUci(uci))
        assertEquals(game.moves, parsePgnMoves(game.toPgn()))
    }

    @Test
    fun ignoresTagsCommentsVariationsAndMarks() {
        val pgn = """
            [Event "Live Chess"]
            [White "me"]
            [Result "1-0"]

            1.e4 {[%clk 0:09:58]} 1... e5!? 2. Nf3 ${'$'}1 Nc6 3. Bb5 (3. Bc4 Bc5 {a comment}) 3... a6 ; rest of line
            4. Ba4 Nf6 5. 0-0 Be7 1-0
        """.trimIndent()
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7"), sanOf(pgn))
    }

    @Test
    fun handlesCastlingPromotionAndDisambiguation() {
        assertEquals("O-O-O", sanOf("1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7 5. O-O-O").last())
        // Promotion with and without "=".
        val promo = "1. a4 b5 2. axb5 a6 3. bxa6 Bb7 4. axb7 Nc6 5. bxa8Q"
        assertEquals("bxa8=Q", sanOf(promo).last())
        assertEquals("bxa8=Q", sanOf(promo.replace("a8Q", "a8=Q")).last())
        // Both knights can reach d2: the file in the move picks one.
        assertEquals("Nbd2", sanOf("1. d3 d6 2. Nf3 Nf6 3. Nbd2").last())
    }

    @Test
    fun emptyTextHasNoMoves() {
        assertTrue(parsePgnMoves("  \n").isEmpty())
        assertTrue(parsePgnMoves("[Event \"x\"]\n\n*").isEmpty())
    }

    @Test
    fun explainsWhatIsWrong() {
        val illegal = assertThrows(PgnParseException::class.java) { parsePgnMoves("1. e4 e5 2. Nf6") }
        assertTrue(illegal.message!!, illegal.message!!.contains("2. Nf6") && illegal.message!!.contains("not legal"))

        val garbage = assertThrows(PgnParseException::class.java) { parsePgnMoves("1. e4 hello") }
        assertTrue(garbage.message!!.contains("1... hello"))

        val ambiguous = assertThrows(PgnParseException::class.java) { parsePgnMoves("1. d3 d6 2. Nf3 Nf6 3. Nd2") }
        assertTrue(ambiguous.message!!.contains("ambiguous"))

        val fen = assertThrows(PgnParseException::class.java) {
            parsePgnMoves("[SetUp \"1\"]\n[FEN \"4k3/8/8/8/8/8/4P3/4K3 w - - 0 1\"]\n\n1. e4")
        }
        assertTrue(fen.message!!.contains("custom start"))
    }

    @Test
    fun movesAfterCheckmateAreRejected() {
        val e = assertThrows(PgnParseException::class.java) { parsePgnMoves("1. f3 e5 2. g4 Qh4# 3. a3") }
        assertTrue(e.message!!.contains("already over"))
    }

    @Test
    fun textAfterTheResultIsIgnored() {
        assertEquals(listOf("e4", "e5"), sanOf("1. e4 e5 1-0\n\n[Event \"second\"]\n\n1. d4 d5"))
    }
}

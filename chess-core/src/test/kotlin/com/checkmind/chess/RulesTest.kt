package com.checkmind.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun uciSet(p: Position) = p.legalMoves().map { it.uci() }.toSet()

    private fun Game.playSan(vararg sans: String) {
        for (san in sans) {
            val pos = position
            val m = pos.legalMoves().firstOrNull { pos.toSan(it) == san }
                ?: error("No legal move $san in ${pos.toFen()}")
            play(m)
        }
    }

    // ------------------------------------------------------------ castling

    @Test
    fun castlingBothSidesAvailable() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val moves = uciSet(p)
        assertTrue("e1g1" in moves)
        assertTrue("e1c1" in moves)
    }

    @Test
    fun castlingRefusedWhenThroughAttackedSquare() {
        // Black rook on f8 attacks f1: kingside castling forbidden, queenside fine.
        val p = Position.fromFen("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val moves = uciSet(p)
        assertFalse("e1g1" in moves)
        assertTrue("e1c1" in moves)
    }

    @Test
    fun castlingRefusedWhenInCheck() {
        val p = Position.fromFen("4r2k/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val moves = uciSet(p)
        assertFalse("e1g1" in moves)
        assertFalse("e1c1" in moves)
    }

    @Test
    fun castlingMovesRook() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").play(Move.fromUci("e1g1"))
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), p.pieceAt(Squares.parse("f1")))
        assertEquals(null, p.pieceAt(Squares.parse("h1")))
        assertEquals(0, p.castling and (Position.WK or Position.WQ))
    }

    @Test
    fun castlingRightsLostWhenRookCaptured() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/6b1/R3K2R b KQkq - 0 1").play(Move.fromUci("g2h1"))
        assertEquals(0, p.castling and Position.WK)
        assertTrue(p.castling and Position.WQ != 0)
    }

    // ------------------------------------------------------------ en passant

    @Test
    fun enPassantAvailableOnlyImmediately() {
        val g = Game()
        g.playSan("e4", "a6", "e5", "d5")
        val pos = g.position
        assertTrue("e5d6" in uciSet(pos))
        g.playSan("Nf3", "a5")
        assertFalse("e5d6" in uciSet(g.position))
    }

    @Test
    fun enPassantRemovesCapturedPawn() {
        val g = Game()
        g.playSan("e4", "a6", "e5", "d5", "exd6")
        assertEquals(null, g.position.pieceAt(Squares.parse("d5")))
        assertEquals(Piece(Color.WHITE, PieceType.PAWN), g.position.pieceAt(Squares.parse("d6")))
    }

    @Test
    fun enPassantRefusedWhenExposingKing() {
        val p = Position.fromFen("8/8/8/8/k2Pp2Q/8/8/3K4 b - d3 0 1")
        assertFalse("e4d3" in uciSet(p))
    }

    // ------------------------------------------------------------ promotion

    @Test
    fun promotionGivesFourChoices() {
        val p = Position.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1")
        val promos = p.legalMoves().filter { it.from == Squares.parse("a7") }.map { it.uci() }.toSet()
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), promos)
    }

    @Test
    fun promotionReplacesPawn() {
        val p = Position.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1").play(Move.fromUci("a7a8n"))
        assertEquals(Piece(Color.WHITE, PieceType.KNIGHT), p.pieceAt(Squares.parse("a8")))
    }

    // ------------------------------------------------------------ SAN

    @Test
    fun sanBasics() {
        val g = Game()
        val pos = g.position
        assertEquals("e4", pos.toSan(Move.fromUci("e2e4")))
        assertEquals("Nf3", pos.toSan(Move.fromUci("g1f3")))
    }

    @Test
    fun sanDisambiguationByFile() {
        val p = Position.fromFen("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1")
        assertEquals("Nbd2", p.toSan(Move.fromUci("b1d2")))
        assertEquals("Nfd2", p.toSan(Move.fromUci("f3d2")))
    }

    @Test
    fun sanDisambiguationByRank() {
        val p = Position.fromFen("7k/8/8/8/8/4R3/8/K3R3 w - - 0 1")
        assertEquals("R1e2", p.toSan(Move.fromUci("e1e2")))
        assertEquals("R3e2", p.toSan(Move.fromUci("e3e2")))
    }

    @Test
    fun sanPawnCaptureAndEnPassant() {
        val g = Game()
        g.playSan("e4", "a6", "e5", "d5")
        assertEquals("exd6", g.position.toSan(Move.fromUci("e5d6")))
    }

    @Test
    fun sanPromotionWithCheck() {
        val p = Position.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1")
        assertEquals("a8=Q+", p.toSan(Move.fromUci("a7a8q")))
    }

    @Test
    fun sanCastling() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals("O-O", p.toSan(Move.fromUci("e1g1")))
        assertEquals("O-O-O", p.toSan(Move.fromUci("e1c1")))
    }

    @Test
    fun sanMateSuffix() {
        val g = Game()
        g.playSan("f3", "e5", "g4", "Qh4#")
        assertEquals(GameStatus.Checkmate(Color.BLACK), g.status)
    }

    // ------------------------------------------------------------ status

    @Test
    fun foolsMateEndsGame() {
        val g = Game()
        g.playSan("f3", "e5", "g4", "Qh4#")
        assertEquals(GameStatus.Checkmate(Color.BLACK), g.status)
        assertTrue(g.legalMoves().isEmpty())
    }

    @Test
    fun stalemateDetected() {
        val g = Game(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))
        assertEquals(GameStatus.Stalemate, g.status)
    }

    @Test
    fun insufficientMaterial() {
        fun status(fen: String) = Game(Position.fromFen(fen)).status
        assertEquals(GameStatus.DrawInsufficientMaterial, status("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))
        assertEquals(GameStatus.DrawInsufficientMaterial, status("4k3/8/8/8/8/8/8/3NK3 w - - 0 1"))
        assertEquals(GameStatus.DrawInsufficientMaterial, status("4k3/8/8/8/8/8/8/3BK3 w - - 0 1"))
        // Bishops on same colour squares (c1 dark, f8 dark): draw
        assertEquals(GameStatus.DrawInsufficientMaterial, status("5b1k/8/8/8/8/8/8/2B1K3 w - - 0 1"))
        // Bishops on opposite colour squares (c1 dark, e8 light): not a draw
        assertEquals(GameStatus.Ongoing, status("4b2k/8/8/8/8/8/8/2B1K3 w - - 0 1"))
        assertEquals(GameStatus.Ongoing, status("4k3/8/8/8/8/8/P7/4K3 w - - 0 1"))
    }

    @Test
    fun threefoldRepetition() {
        val g = Game()
        g.playSan("Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(GameStatus.Ongoing, g.status)
        g.playSan("Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(GameStatus.DrawThreefold, g.status)
    }

    @Test
    fun fiftyMoveRule() {
        val g = Game(Position.fromFen("4k3/8/8/8/8/8/8/4K2R w - - 99 80"))
        assertEquals(GameStatus.Ongoing, g.status)
        g.play(Move.fromUci("h1h2"))
        assertEquals(GameStatus.DrawFiftyMove, g.status)
    }

    @Test
    fun checkmateBeatsFiftyMove() {
        // Back-rank mate on the 100th half-move.
        val g = Game(Position.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 99 80"))
        g.play(Move.fromUci("a1a8"))
        assertEquals(GameStatus.Checkmate(Color.WHITE), g.status)
    }

    // ------------------------------------------------------------ game control

    @Test
    fun undoRestoresPositionExactly() {
        val g = Game()
        val startFen = g.position.toFen()
        g.playSan("e4", "e5", "Nf3", "Nc6", "Bb5", "a6")
        repeat(6) { assertTrue(g.undo()) }
        assertEquals(startFen, g.position.toFen())
        assertFalse(g.undo())
    }

    @Test
    fun undoRestoresCastlingRights() {
        val g = Game(Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        val before = g.position.toFen()
        g.play(Move.fromUci("e1g1"))
        g.undo()
        assertEquals(before, g.position.toFen())
    }

    @Test
    fun undoShrinksRepetitionCount() {
        val g = Game()
        g.playSan("Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1")
        assertEquals(GameStatus.Ongoing, g.status)
        g.undo()
        g.playSan("Ng1", "Ng8")
        assertEquals(GameStatus.DrawThreefold, g.status)
    }

    @Test
    fun undoAfterCheckmateReopensGame() {
        val g = Game()
        g.playSan("f3", "e5", "g4", "Qh4#")
        assertTrue(g.undo())
        assertEquals(GameStatus.Ongoing, g.status)
    }

    @Test
    fun resignBlocksUndoAndPlay() {
        val g = Game()
        g.playSan("e4")
        g.resign(Color.BLACK)
        assertEquals(GameStatus.Resigned(Color.WHITE), g.status)
        assertFalse(g.undo())
        assertTrue(g.legalMoves().isEmpty())
    }
}

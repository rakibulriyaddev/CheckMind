package com.checkmind.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {
    private fun gameOf(vararg uci: String): Game = Game().also { g -> uci.forEach { g.play(Move.fromUci(it)) } }

    @Test
    fun movetextIsNumbered() {
        val g = gameOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5")
        assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bb5", g.movetext())
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5"), g.sanMoves())
    }

    @Test
    fun pgnHasTagsMovesAndResult() {
        val pgn = gameOf("e2e4", "e7e5").toPgn(date = "2026.10.05")
        assertTrue(pgn.startsWith("[Event \"CheckMind game\"]\n"))
        assertTrue(pgn.contains("[Date \"2026.10.05\"]"))
        assertTrue(pgn.contains("[Result \"*\"]"))
        assertTrue(pgn.endsWith("\n\n1. e4 e5 *"))
    }

    @Test
    fun checkmateAndResignationSetResult() {
        val mate = gameOf("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals("1. f3 e5 2. g4 Qh4#", mate.movetext())
        assertTrue(mate.toPgn().endsWith("Qh4# 0-1"))
        assertTrue(mate.toPgn().contains("[Result \"0-1\"]"))

        val resigned = gameOf("e2e4").also { it.resign(Color.BLACK) }
        assertTrue(resigned.toPgn().endsWith("1. e4 1-0"))
    }

    @Test
    fun emptyGameIsJustTagsAndResult() {
        val pgn = Game().toPgn()
        assertTrue(pgn.endsWith("\n\n*"))
    }

    @Test
    fun blackToMoveStartUsesEllipsisAndFenTags() {
        val start = Position.fromFen("4k3/8/8/8/8/8/4P3/4K3 b - - 0 7")
        val g = Game(start).also { it.play(Move.fromUci("e8e7")); it.play(Move.fromUci("e2e4")) }
        assertEquals("7... Ke7 8. e4", g.movetext())
        val pgn = g.toPgn()
        assertTrue(pgn.contains("[SetUp \"1\"]"))
        assertTrue(pgn.contains("[FEN \"4k3/8/8/8/8/8/4P3/4K3 b - - 0 7\"]"))
    }

    @Test
    fun pgnRoundTripsThroughTheParserFormat() {
        // Disambiguation and castling come out the way the book builder reads them back.
        val g = gameOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1")
        assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. O-O", g.movetext())
    }
}

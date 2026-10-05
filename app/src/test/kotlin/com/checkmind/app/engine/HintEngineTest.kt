package com.checkmind.app.engine

import com.checkmind.chess.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintEngineTest {
    @Test
    fun parsesCentipawnLine() {
        val (multipv, line) = parseInfoLine(
            "info depth 22 seldepth 31 multipv 2 score cp -17 nodes 1234 nps 99 hashfull 3 time 40 pv d2d4 d7d5 c2c4",
        )!!
        assertEquals(2, multipv)
        assertEquals(EngineLine(Move.fromUci("d2d4"), -17, null), line)
        assertEquals("-0.17", line.evalLabel)
    }

    @Test
    fun parsesMateAndPromotion() {
        val (multipv, line) = parseInfoLine("info depth 9 score mate 3 time 5 pv e7e8q a1a2")!!
        assertEquals(1, multipv)
        assertEquals(Move.fromUci("e7e8q"), line.move)
        assertEquals("M3", line.evalLabel)
        assertEquals("-M2", EngineLine(Move.fromUci("a1a2"), null, -2).evalLabel)
    }

    @Test
    fun formatsPositiveScoreWithSign() {
        assertEquals("+0.35", EngineLine(Move.fromUci("e2e4"), 35, null).evalLabel)
        assertEquals("+0.00", EngineLine(Move.fromUci("e2e4"), 0, null).evalLabel)
    }

    @Test
    fun ignoresLinesWithoutAFinishedScoreAndMove() {
        assertNull(parseInfoLine("info string NNUE evaluation using nn-1c0000000000.nnue"))
        assertNull(parseInfoLine("info depth 5 score cp 10 lowerbound pv e2e4"))
        assertNull(parseInfoLine("info depth 5 currmove e2e4 currmovenumber 1"))
        assertNull(parseInfoLine("bestmove e2e4 ponder e7e5"))
    }
}

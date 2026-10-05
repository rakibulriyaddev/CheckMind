package com.checkmind.chess.book

import com.checkmind.chess.Color
import com.checkmind.chess.PieceType
import com.checkmind.chess.Position
import com.checkmind.chess.Squares

/**
 * Zobrist-style 47-bit key of a position: piece placement, side to move, castling rights, and the
 * en passant file only when a pawn could actually capture there. Two move orders that reach the
 * same position give the same key, which is what lets the book survive transpositions.
 *
 * The key must be identical in the book builder and in the app, so the random table comes from a
 * fixed seed. Changing the seed or the layout invalidates every book.bin: bump [BookCodec.VERSION].
 */
object PositionKey {
    /** 47 bits, so `key shl 16 or move` is always a non-negative Long. */
    const val BITS = 47
    private const val MASK = (1L shl BITS) - 1

    private val pieceKeys = LongArray(6 * 2 * 64)
    private val sideKey: Long
    private val castlingKeys = LongArray(16)
    private val epFileKeys = LongArray(8)

    init {
        var state = 0x43686B4D696E64L // "ChkMind"
        fun next(): Long {
            // SplitMix64
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
        for (i in pieceKeys.indices) pieceKeys[i] = next()
        sideKey = next()
        for (i in castlingKeys.indices) castlingKeys[i] = next()
        for (i in epFileKeys.indices) epFileKeys[i] = next()
    }

    fun of(position: Position): Long {
        var h = 0L
        for (square in 0..63) {
            val p = position.pieceAt(square) ?: continue
            h = h xor pieceKeys[(p.type.ordinal * 2 + p.color.ordinal) * 64 + square]
        }
        if (position.sideToMove == Color.BLACK) h = h xor sideKey
        h = h xor castlingKeys[position.castling and 15]
        if (enPassantCapturable(position)) h = h xor epFileKeys[Squares.file(position.epSquare)]
        return h and MASK
    }

    /** Packs a position key and a move code (15 bits) into one sortable Long. */
    fun pack(key: Long, moveBits: Int): Long = (key shl 16) or moveBits.toLong()

    fun keyOf(packed: Long): Long = packed ushr 16

    fun moveOf(packed: Long): Int = (packed and 0xFFFF).toInt()

    /** Pseudo-legal check (pins ignored, like Polyglot): an own pawn stands next to the en passant target. */
    private fun enPassantCapturable(p: Position): Boolean {
        val ep = p.epSquare
        if (ep < 0) return false
        val pawnRank = if (p.sideToMove == Color.WHITE) Squares.rank(ep) - 1 else Squares.rank(ep) + 1
        if (pawnRank !in 0..7) return false
        for (df in intArrayOf(-1, 1)) {
            val f = Squares.file(ep) + df
            if (f !in 0..7) continue
            val piece = p.pieceAt(Squares.of(f, pawnRank))
            if (piece != null && piece.type == PieceType.PAWN && piece.color == p.sideToMove) return true
        }
        return false
    }
}

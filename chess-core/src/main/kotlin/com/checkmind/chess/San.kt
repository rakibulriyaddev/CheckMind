package com.checkmind.chess

import com.checkmind.chess.Squares.file
import com.checkmind.chess.Squares.rank
import kotlin.math.abs

/** SAN without the check / mate suffix. The move must be legal in this position. */
fun Position.sanBase(move: Move): String {
    val piece = pieceAt(move.from) ?: error("No piece on ${Squares.name(move.from)}")
    val from = move.from
    val to = move.to
    if (piece.type == PieceType.KING && abs(file(to) - file(from)) == 2) {
        return if (file(to) > file(from)) "O-O" else "O-O-O"
    }
    val capture = pieceAt(to) != null || (piece.type == PieceType.PAWN && file(from) != file(to))
    val sb = StringBuilder()
    if (piece.type == PieceType.PAWN) {
        if (capture) sb.append('a' + file(from)).append('x')
        sb.append(Squares.name(to))
        move.promotion?.let { sb.append('=').append(it.letter) }
    } else {
        sb.append(piece.type.letter)
        val others = legalMoves().filter {
            it != move && it.to == to && it.from != from && pieceAt(it.from)?.type == piece.type
        }
        if (others.isNotEmpty()) {
            val sameFile = others.any { file(it.from) == file(from) }
            val sameRank = others.any { rank(it.from) == rank(from) }
            when {
                !sameFile -> sb.append('a' + file(from))
                !sameRank -> sb.append('1' + rank(from))
                else -> sb.append('a' + file(from)).append('1' + rank(from))
            }
        }
        if (capture) sb.append('x')
        sb.append(Squares.name(to))
    }
    return sb.toString()
}

/** Full SAN including `+` or `#`. */
fun Position.toSan(move: Move): String {
    val base = sanBase(move)
    val next = play(move)
    val suffix = when {
        !next.isInCheck() -> ""
        next.legalMoves().isEmpty() -> "#"
        else -> "+"
    }
    return base + suffix
}

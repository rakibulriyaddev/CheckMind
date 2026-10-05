package com.checkmind.chess

enum class Color {
    WHITE, BLACK;

    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val letter: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K')
}

data class Piece(val color: Color, val type: PieceType)

/** Squares are Ints 0..63 with a1 = 0, b1 = 1, ... h1 = 7, a2 = 8, ... h8 = 63. */
object Squares {
    const val A1 = 0
    const val C1 = 2
    const val D1 = 3
    const val E1 = 4
    const val F1 = 5
    const val G1 = 6
    const val H1 = 7
    const val A8 = 56
    const val C8 = 58
    const val D8 = 59
    const val E8 = 60
    const val F8 = 61
    const val G8 = 62
    const val H8 = 63

    fun file(square: Int): Int = square and 7
    fun rank(square: Int): Int = square shr 3
    fun of(file: Int, rank: Int): Int = rank * 8 + file

    fun name(square: Int): String = "${'a' + file(square)}${rank(square) + 1}"

    fun parse(name: String): Int {
        require(name.length == 2) { "Bad square: $name" }
        val file = name[0] - 'a'
        val rank = name[1] - '1'
        require(file in 0..7 && rank in 0..7) { "Bad square: $name" }
        return of(file, rank)
    }

    /** True when the square is dark (a1 is dark). */
    fun isDark(square: Int): Boolean = (file(square) + rank(square)) % 2 == 0
}

/**
 * A move packed into an Int.
 * bits 0..5 from, bits 6..11 to, bits 12..14 promotion (0 none, 1 knight, 2 bishop, 3 rook, 4 queen).
 * Castling is a king move of two files, en passant is a pawn move to the empty en passant square.
 */
@JvmInline
value class Move(val bits: Int) {
    val from: Int get() = bits and 63
    val to: Int get() = (bits shr 6) and 63

    val promotion: PieceType?
        get() = when ((bits shr 12) and 7) {
            1 -> PieceType.KNIGHT
            2 -> PieceType.BISHOP
            3 -> PieceType.ROOK
            4 -> PieceType.QUEEN
            else -> null
        }

    fun uci(): String {
        val promo = when (promotion) {
            PieceType.KNIGHT -> "n"
            PieceType.BISHOP -> "b"
            PieceType.ROOK -> "r"
            PieceType.QUEEN -> "q"
            else -> ""
        }
        return Squares.name(from) + Squares.name(to) + promo
    }

    override fun toString(): String = uci()

    companion object {
        fun of(from: Int, to: Int, promotion: PieceType? = null): Move {
            val code = when (promotion) {
                null -> 0
                PieceType.KNIGHT -> 1
                PieceType.BISHOP -> 2
                PieceType.ROOK -> 3
                PieceType.QUEEN -> 4
                else -> throw IllegalArgumentException("Cannot promote to $promotion")
            }
            return Move(from or (to shl 6) or (code shl 12))
        }

        fun fromUci(uci: String): Move {
            require(uci.length == 4 || uci.length == 5) { "Bad move: $uci" }
            val promo = if (uci.length == 5) {
                when (uci[4]) {
                    'n' -> PieceType.KNIGHT
                    'b' -> PieceType.BISHOP
                    'r' -> PieceType.ROOK
                    'q' -> PieceType.QUEEN
                    else -> throw IllegalArgumentException("Bad promotion: $uci")
                }
            } else null
            return of(Squares.parse(uci.substring(0, 2)), Squares.parse(uci.substring(2, 4)), promo)
        }
    }
}

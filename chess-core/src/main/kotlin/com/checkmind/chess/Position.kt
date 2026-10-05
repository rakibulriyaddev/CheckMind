package com.checkmind.chess

import com.checkmind.chess.Squares.file
import com.checkmind.chess.Squares.rank
import kotlin.math.abs

/** Immutable chess position. Every move produces a new Position. */
class Position(
    board: Array<Piece?>,
    val sideToMove: Color,
    /** Bit set: WK = 1, WQ = 2, BK = 4, BQ = 8. */
    val castling: Int,
    /** Square a pawn may capture onto en passant, or -1. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    private val sq: Array<Piece?> = board.copyOf()

    init {
        require(board.size == 64) { "Board must have 64 squares" }
    }

    fun pieceAt(square: Int): Piece? = sq[square]

    fun boardList(): List<Piece?> = List(64) { sq[it] }

    private val legal: List<Move> by lazy(LazyThreadSafetyMode.NONE) {
        val us = sideToMove
        pseudoLegal().filter { !playUnchecked(it).isInCheck(us) }
    }

    fun legalMoves(): List<Move> = legal

    fun legalMovesFrom(from: Int): List<Move> = legal.filter { it.from == from }

    fun isLegal(move: Move): Boolean = legal.contains(move)

    /** Plays a legal move and returns the new position. Throws if the move is not legal. */
    fun play(move: Move): Position {
        require(isLegal(move)) { "Illegal move ${move.uci()} in ${toFen()}" }
        return playUnchecked(move)
    }

    fun kingSquare(color: Color): Int {
        for (s in 0..63) {
            val p = sq[s]
            if (p != null && p.color == color && p.type == PieceType.KING) return s
        }
        return -1
    }

    fun isInCheck(color: Color = sideToMove): Boolean {
        val k = kingSquare(color)
        return k >= 0 && isSquareAttacked(k, color.opposite)
    }

    fun isSquareAttacked(target: Int, by: Color): Boolean {
        val tf = file(target)
        val tr = rank(target)

        val pawnDir = if (by == Color.WHITE) 1 else -1
        val pr = tr - pawnDir
        if (pr in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val pf = tf + df
                if (pf in 0..7) {
                    val p = sq[Squares.of(pf, pr)]
                    if (p != null && p.color == by && p.type == PieceType.PAWN) return true
                }
            }
        }
        for (i in 0 until 8) {
            val f = tf + KN_DF[i]
            val r = tr + KN_DR[i]
            if (f in 0..7 && r in 0..7) {
                val p = sq[Squares.of(f, r)]
                if (p != null && p.color == by && p.type == PieceType.KNIGHT) return true
            }
            val kf = tf + K_DF[i]
            val kr = tr + K_DR[i]
            if (kf in 0..7 && kr in 0..7) {
                val p = sq[Squares.of(kf, kr)]
                if (p != null && p.color == by && p.type == PieceType.KING) return true
            }
        }
        if (rayHits(tf, tr, B_DF, B_DR, by, PieceType.BISHOP)) return true
        if (rayHits(tf, tr, R_DF, R_DR, by, PieceType.ROOK)) return true
        return false
    }

    private fun rayHits(tf: Int, tr: Int, dfs: IntArray, drs: IntArray, by: Color, slider: PieceType): Boolean {
        for (d in dfs.indices) {
            var f = tf + dfs[d]
            var r = tr + drs[d]
            while (f in 0..7 && r in 0..7) {
                val p = sq[Squares.of(f, r)]
                if (p != null) {
                    if (p.color == by && (p.type == slider || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += dfs[d]
                r += drs[d]
            }
        }
        return false
    }

    // ---------------------------------------------------------------- move generation

    private fun pseudoLegal(): List<Move> {
        val out = ArrayList<Move>(48)
        val us = sideToMove
        for (from in 0..63) {
            val p = sq[from] ?: continue
            if (p.color != us) continue
            when (p.type) {
                PieceType.PAWN -> genPawn(from, out)
                PieceType.KNIGHT -> jump(from, KN_DF, KN_DR, out)
                PieceType.BISHOP -> slide(from, B_DF, B_DR, out)
                PieceType.ROOK -> slide(from, R_DF, R_DR, out)
                PieceType.QUEEN -> {
                    slide(from, B_DF, B_DR, out)
                    slide(from, R_DF, R_DR, out)
                }
                PieceType.KING -> {
                    jump(from, K_DF, K_DR, out)
                    genCastling(from, out)
                }
            }
        }
        return out
    }

    private fun genPawn(from: Int, out: MutableList<Move>) {
        val us = sideToMove
        val f = file(from)
        val r = rank(from)
        val dir = if (us == Color.WHITE) 1 else -1
        val startRank = if (us == Color.WHITE) 1 else 6
        val promoRank = if (us == Color.WHITE) 7 else 0
        val nr = r + dir
        if (nr !in 0..7) return
        val forward = Squares.of(f, nr)
        if (sq[forward] == null) {
            addPawnMove(from, forward, nr == promoRank, out)
            if (r == startRank) {
                val two = Squares.of(f, r + 2 * dir)
                if (sq[two] == null) out.add(Move.of(from, two))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val to = Squares.of(nf, nr)
            val target = sq[to]
            if ((target != null && target.color != us) || to == epSquare) {
                addPawnMove(from, to, nr == promoRank, out)
            }
        }
    }

    private fun addPawnMove(from: Int, to: Int, promotes: Boolean, out: MutableList<Move>) {
        if (promotes) {
            out.add(Move.of(from, to, PieceType.QUEEN))
            out.add(Move.of(from, to, PieceType.ROOK))
            out.add(Move.of(from, to, PieceType.BISHOP))
            out.add(Move.of(from, to, PieceType.KNIGHT))
        } else {
            out.add(Move.of(from, to))
        }
    }

    private fun jump(from: Int, dfs: IntArray, drs: IntArray, out: MutableList<Move>) {
        val us = sideToMove
        val f0 = file(from)
        val r0 = rank(from)
        for (d in dfs.indices) {
            val f = f0 + dfs[d]
            val r = r0 + drs[d]
            if (f !in 0..7 || r !in 0..7) continue
            val to = Squares.of(f, r)
            val t = sq[to]
            if (t == null || t.color != us) out.add(Move.of(from, to))
        }
    }

    private fun slide(from: Int, dfs: IntArray, drs: IntArray, out: MutableList<Move>) {
        val us = sideToMove
        val f0 = file(from)
        val r0 = rank(from)
        for (d in dfs.indices) {
            var f = f0 + dfs[d]
            var r = r0 + drs[d]
            while (f in 0..7 && r in 0..7) {
                val to = Squares.of(f, r)
                val t = sq[to]
                if (t == null) {
                    out.add(Move.of(from, to))
                } else {
                    if (t.color != us) out.add(Move.of(from, to))
                    break
                }
                f += dfs[d]
                r += drs[d]
            }
        }
    }

    private fun genCastling(from: Int, out: MutableList<Move>) {
        val us = sideToMove
        val them = us.opposite
        val home = if (us == Color.WHITE) Squares.E1 else Squares.E8
        if (from != home) return
        val rankBase = if (us == Color.WHITE) 0 else 56
        val kingSideBit = if (us == Color.WHITE) WK else BK
        val queenSideBit = if (us == Color.WHITE) WQ else BQ
        val hasKingSide = castling and kingSideBit != 0
        val hasQueenSide = castling and queenSideBit != 0
        if (!hasKingSide && !hasQueenSide) return
        if (isSquareAttacked(home, them)) return
        if (hasKingSide &&
            sq[rankBase + 5] == null && sq[rankBase + 6] == null &&
            sq[rankBase + 7]?.let { it.color == us && it.type == PieceType.ROOK } == true &&
            !isSquareAttacked(rankBase + 5, them) && !isSquareAttacked(rankBase + 6, them)
        ) {
            out.add(Move.of(home, rankBase + 6))
        }
        if (hasQueenSide &&
            sq[rankBase + 1] == null && sq[rankBase + 2] == null && sq[rankBase + 3] == null &&
            sq[rankBase]?.let { it.color == us && it.type == PieceType.ROOK } == true &&
            !isSquareAttacked(rankBase + 3, them) && !isSquareAttacked(rankBase + 2, them)
        ) {
            out.add(Move.of(home, rankBase + 2))
        }
    }

    // ---------------------------------------------------------------- applying moves

    private fun playUnchecked(move: Move): Position {
        val us = sideToMove
        val nb = sq.copyOf()
        val from = move.from
        val to = move.to
        val piece = nb[from] ?: error("No piece on ${Squares.name(from)}")
        val captured = nb[to]
        nb[from] = null
        var newEp = -1
        var placed = piece
        var resetClock = captured != null

        when (piece.type) {
            PieceType.PAWN -> {
                resetClock = true
                if (captured == null && file(from) != file(to) && to == epSquare) {
                    nb[Squares.of(file(to), rank(from))] = null
                }
                if (abs(rank(to) - rank(from)) == 2) newEp = (from + to) / 2
                val promo = move.promotion
                if (promo != null) placed = Piece(us, promo)
            }
            PieceType.KING -> {
                if (abs(file(to) - file(from)) == 2) {
                    val base = rank(from) * 8
                    if (file(to) > file(from)) {
                        nb[base + 5] = nb[base + 7]
                        nb[base + 7] = null
                    } else {
                        nb[base + 3] = nb[base]
                        nb[base] = null
                    }
                }
            }
            else -> {}
        }
        nb[to] = placed

        val newCastling = castling and castlingMask(from).inv() and castlingMask(to).inv()
        return Position(
            board = nb,
            sideToMove = us.opposite,
            castling = newCastling,
            epSquare = newEp,
            halfmoveClock = if (resetClock) 0 else halfmoveClock + 1,
            fullmoveNumber = if (us == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    private fun castlingMask(square: Int): Int = when (square) {
        Squares.E1 -> WK or WQ
        Squares.A1 -> WQ
        Squares.H1 -> WK
        Squares.E8 -> BK or BQ
        Squares.A8 -> BQ
        Squares.H8 -> BK
        else -> 0
    }

    // ---------------------------------------------------------------- draws

    fun hasInsufficientMaterial(): Boolean {
        val minorSquares = ArrayList<Int>(2)
        for (s in 0..63) {
            val p = sq[s] ?: continue
            when (p.type) {
                PieceType.KING -> {}
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.KNIGHT, PieceType.BISHOP -> minorSquares.add(s)
            }
        }
        if (minorSquares.size <= 1) return true
        if (minorSquares.size == 2) {
            val a = sq[minorSquares[0]]!!
            val b = sq[minorSquares[1]]!!
            if (a.type == PieceType.BISHOP && b.type == PieceType.BISHOP && a.color != b.color &&
                Squares.isDark(minorSquares[0]) == Squares.isDark(minorSquares[1])
            ) return true
        }
        return false
    }

    /** Key for repetition: placement, side to move, castling rights, en passant only if capturable. */
    fun repetitionKey(): String {
        val ep = if (epSquare >= 0 && legal.any { it.to == epSquare && sq[it.from]?.type == PieceType.PAWN && file(it.from) != file(it.to) }) {
            Squares.name(epSquare)
        } else "-"
        return "${placementFen()}|${if (sideToMove == Color.WHITE) 'w' else 'b'}|$castling|$ep"
    }

    // ---------------------------------------------------------------- FEN

    private fun placementFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = sq[Squares.of(f, r)]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    val ch = p.type.letter
                    sb.append(if (p.color == Color.WHITE) ch else ch.lowercaseChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        return sb.toString()
    }

    fun toFen(): String {
        val c = buildString {
            if (castling and WK != 0) append('K')
            if (castling and WQ != 0) append('Q')
            if (castling and BK != 0) append('k')
            if (castling and BQ != 0) append('q')
            if (isEmpty()) append('-')
        }
        val ep = if (epSquare >= 0) Squares.name(epSquare) else "-"
        return "${placementFen()} ${if (sideToMove == Color.WHITE) 'w' else 'b'} $c $ep $halfmoveClock $fullmoveNumber"
    }

    companion object {
        const val WK = 1
        const val WQ = 2
        const val BK = 4
        const val BQ = 8

        private val KN_DF = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
        private val KN_DR = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)
        private val K_DF = intArrayOf(1, 1, 1, 0, -1, -1, -1, 0)
        private val K_DR = intArrayOf(1, 0, -1, -1, -1, 0, 1, 1)
        private val B_DF = intArrayOf(1, 1, -1, -1)
        private val B_DR = intArrayOf(1, -1, 1, -1)
        private val R_DF = intArrayOf(1, -1, 0, 0)
        private val R_DR = intArrayOf(0, 0, 1, -1)

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val START: Position by lazy { fromFen(START_FEN) }

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 2) { "Bad FEN: $fen" }
            val board = arrayOfNulls<Piece>(64)
            val rows = parts[0].split('/')
            require(rows.size == 8) { "Bad FEN placement: $fen" }
            for ((i, row) in rows.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (ch in row) {
                    if (ch.isDigit()) {
                        file += ch - '0'
                    } else {
                        val color = if (ch.isUpperCase()) Color.WHITE else Color.BLACK
                        val type = when (ch.uppercaseChar()) {
                            'P' -> PieceType.PAWN
                            'N' -> PieceType.KNIGHT
                            'B' -> PieceType.BISHOP
                            'R' -> PieceType.ROOK
                            'Q' -> PieceType.QUEEN
                            'K' -> PieceType.KING
                            else -> throw IllegalArgumentException("Bad FEN piece '$ch'")
                        }
                        require(file in 0..7) { "Bad FEN row: $row" }
                        board[Squares.of(file, rank)] = Piece(color, type)
                        file++
                    }
                }
                require(file == 8) { "Bad FEN row: $row" }
            }
            val side = if (parts[1] == "w") Color.WHITE else Color.BLACK
            var castling = 0
            val c = parts.getOrElse(2) { "-" }
            if ('K' in c) castling = castling or WK
            if ('Q' in c) castling = castling or WQ
            if ('k' in c) castling = castling or BK
            if ('q' in c) castling = castling or BQ
            val epText = parts.getOrElse(3) { "-" }
            val ep = if (epText == "-") -1 else Squares.parse(epText)
            val half = parts.getOrNull(4)?.toInt() ?: 0
            val full = parts.getOrNull(5)?.toInt() ?: 1
            return Position(board, side, castling, ep, half, full)
        }
    }
}

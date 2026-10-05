package com.checkmind.book

import com.checkmind.chess.Color
import com.checkmind.chess.Move
import com.checkmind.chess.PieceType
import com.checkmind.chess.Position
import com.checkmind.chess.Squares
import com.checkmind.chess.book.BookTableBuilder
import com.checkmind.chess.book.OpeningBook
import com.checkmind.chess.book.PositionKey
import com.checkmind.chess.sanBase

class BookBuildException(message: String) : Exception(message)

data class BuildSummary(
    val gamesRead: Int,
    val whiteGames: Int,
    val blackGames: Int,
    val skipped: List<String>,
    val whiteEntries: Int,
    val blackEntries: Int,
    val whitePositions: Int,
    val blackPositions: Int,
    val deepestPlies: Int,
)

object BookBuilder {
    private val PROMOTION_WITHOUT_EQUALS = Regex("^([a-h](?:x[a-h])?[18])([QRBNqrbn])$")

    fun build(games: Iterable<PgnGame>): Pair<OpeningBook, BuildSummary> = build(games.asSequence())

    fun build(games: Sequence<PgnGame>): Pair<OpeningBook, BuildSummary> {
        var gamesRead = 0
        val white = BookTableBuilder()
        val black = BookTableBuilder()
        var deepest = 0
        val skipped = ArrayList<String>()
        var whiteGames = 0
        var blackGames = 0

        /** Strict games fail the build, lenient (bulk) games are skipped. Returns true when skipped. */
        fun problem(g: PgnGame, message: String): Boolean {
            if (!g.lenient) throw BookBuildException("${g.label}: $message")
            skipped.add("${g.label}: $message")
            return true
        }

        game@ for (g in games) {
            gamesRead++
            val variant = g.tags["Variant"]
            if (variant != null && !variant.equals("Standard", ignoreCase = true)) {
                skipped.add("${g.label}: variant '$variant'")
                continue
            }
            if (g.tags.containsKey("FEN") || g.tags["SetUp"] == "1") {
                problem(g, "custom start position (FEN/SetUp) is not supported")
                continue
            }

            val term = g.termination
            val tagResult = g.tags["Result"]
            if (term != null && term != "*" && tagResult != null && tagResult != "*" && term != tagResult) {
                problem(g, "result mismatch (tag '$tagResult', movetext '$term')")
                continue
            }
            val result = when {
                term != null && term != "*" -> term
                tagResult != null -> tagResult
                else -> term
            }
            val winner: Color = when (result) {
                "1-0" -> Color.WHITE
                "0-1" -> Color.BLACK
                null -> {
                    skipped.add("${g.label}: no result")
                    continue@game
                }
                else -> {
                    skipped.add("${g.label}: not a win ('$result')")
                    continue@game
                }
            }

            var pos = Position.START
            // Only the winner's own moves are hints, so only positions where the winner is to move.
            val entries = LongArray(g.moves.size)
            var kept = 0
            var ply = 0
            for (token in g.moves) {
                val move = findMove(pos, token)
                if (move == null) {
                    problem(g, "illegal or ambiguous move '$token' at ply ${ply + 1}")
                    continue@game
                }
                if (pos.sideToMove == winner) entries[kept++] = PositionKey.pack(PositionKey.of(pos), move.bits)
                ply++
                pos = pos.play(move)
            }
            if (ply == 0) {
                skipped.add("${g.label}: no moves")
                continue
            }

            deepest = maxOf(deepest, ply)
            if (winner == Color.WHITE) {
                white.addGame(entries, kept)
                whiteGames++
            } else {
                black.addGame(entries, kept)
                blackGames++
            }
        }

        val book = OpeningBook(white.build(), black.build(), whiteGames, blackGames)
        val summary = BuildSummary(
            gamesRead = gamesRead,
            whiteGames = whiteGames,
            blackGames = blackGames,
            skipped = skipped,
            whiteEntries = book.white.size,
            blackEntries = book.black.size,
            whitePositions = book.white.positionCount(),
            blackPositions = book.black.positionCount(),
            deepestPlies = deepest,
        )
        return book to summary
    }

    private fun normalize(token: String): String {
        var t = token.trimEnd('+', '#', '!', '?')
        PROMOTION_WITHOUT_EQUALS.matchEntire(t)?.let {
            t = it.groupValues[1] + "=" + it.groupValues[2].uppercase()
        }
        return t
    }

    /** Finds the single legal move whose SAN equals [token] (check marks and annotations ignored). */
    private fun findMove(pos: Position, token: String): Move? {
        val wanted = normalize(token)
        val legal = pos.legalMoves()
        // Narrow down by destination square and piece first, so SAN is built only for a few moves.
        val candidates = if (wanted.startsWith("O-O")) {
            legal.filter { m ->
                pos.pieceAt(m.from)?.type == PieceType.KING &&
                    Math.abs(Squares.file(m.to) - Squares.file(m.from)) == 2
            }
        } else {
            val core = wanted.substringBefore('=')
            if (core.length < 2) return null
            val dest = try {
                Squares.parse(core.takeLast(2))
            } catch (e: IllegalArgumentException) {
                return null
            }
            val type = when (core[0]) {
                'N' -> PieceType.KNIGHT
                'B' -> PieceType.BISHOP
                'R' -> PieceType.ROOK
                'Q' -> PieceType.QUEEN
                'K' -> PieceType.KING
                else -> PieceType.PAWN
            }
            legal.filter { it.to == dest && pos.pieceAt(it.from)?.type == type }
        }
        return candidates.filter { pos.sanBase(it) == wanted }.singleOrNull()
    }
}
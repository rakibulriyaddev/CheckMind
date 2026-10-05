package com.checkmind.chess

import kotlin.math.abs

/** The pasted text is not a PGN game this app can load. [message] is meant for the user. */
class PgnParseException(message: String) : Exception(message)

private val SAN_TOKEN = Regex("^([NBRQK])?([a-h])?([1-8])?x?([a-h][1-8])(?:=?([NBRQnbrq]))?$")
private val MOVE_NUMBER = Regex("^\\d+\\.+")
private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

/**
 * The moves of the first game in [text], checked against the rules from the standard start position.
 *
 * Accepts what chess sites export: tag lines, move numbers (`1.` and `1...`, also glued as `1.e4`),
 * `{comments}`, `;` comments, `(variations)` (ignored), `$1` marks, `!?` annotations, `0-0` castling,
 * promotion with or without `=`, and a result token that ends the game. Everything after the first
 * result token is ignored. Throws [PgnParseException] with a readable reason otherwise.
 */
fun parsePgnMoves(text: String): List<Move> {
    val game = Game()
    val moves = ArrayList<Move>()
    for (token in movetextTokens(text)) {
        if (token in RESULTS) break
        val ply = moves.size
        val label = "${ply / 2 + 1}${if (ply % 2 == 0) "." else "..."} $token"
        if (game.status !is GameStatus.Ongoing) {
            throw PgnParseException("The game is already over before $label.")
        }
        val move = findMove(game.position, token, label)
        game.play(move)
        moves.add(move)
    }
    return moves
}

private fun movetextTokens(text: String): List<String> {
    val body = StringBuilder()
    for (line in text.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("[")) {
            if (Regex("^\\[\\s*FEN\\s").containsMatchIn(trimmed)) {
                throw PgnParseException("Games from a custom start position are not supported.")
            }
            continue
        }
        if (trimmed.startsWith("%")) continue
        body.append(line).append('\n')
    }

    val clean = StringBuilder()
    var depth = 0
    var i = 0
    val s = body
    while (i < s.length) {
        val c = s[i]
        when {
            c == '{' -> {
                val end = s.indexOf('}', i)
                i = if (end < 0) s.length else end + 1
                clean.append(' ')
                continue
            }
            c == ';' && depth == 0 -> {
                val end = s.indexOf('\n', i)
                i = if (end < 0) s.length else end
                continue
            }
            c == '(' -> depth++
            c == ')' -> if (depth > 0) depth--
            depth == 0 -> clean.append(c)
        }
        i++
    }

    val tokens = ArrayList<String>()
    for (raw in clean.split(Regex("\\s+"))) {
        if (raw.isEmpty() || raw.startsWith("$")) continue
        val token = MOVE_NUMBER.replaceFirst(raw, "").trimEnd('+', '#', '!', '?')
        if (token.isEmpty()) continue
        tokens.add(token)
    }
    return tokens
}

private fun findMove(pos: Position, token: String, label: String): Move {
    val legal = pos.legalMoves()
    val castle = token.replace('0', 'O')
    if (castle == "O-O" || castle == "O-O-O") {
        val kingSide = castle == "O-O"
        return legal.singleOrNull { m ->
            pos.pieceAt(m.from)?.type == PieceType.KING &&
                abs(Squares.file(m.to) - Squares.file(m.from)) == 2 &&
                (Squares.file(m.to) > Squares.file(m.from)) == kingSide
        } ?: throw PgnParseException("Cannot castle at $label.")
    }

    val match = SAN_TOKEN.matchEntire(token) ?: throw PgnParseException("Cannot read the move $label.")
    val (pieceLetter, fileHint, rankHint, dest, promoLetter) = match.destructured
    val type = if (pieceLetter.isEmpty()) PieceType.PAWN else PieceType.entries.first { it.letter == pieceLetter[0] }
    val to = Squares.parse(dest)
    val promotion = promoLetter.takeIf { it.isNotEmpty() }
        ?.let { letter -> PieceType.entries.first { it.letter == letter[0].uppercaseChar() } }

    val candidates = legal.filter { m ->
        pos.pieceAt(m.from)?.type == type &&
            m.to == to &&
            m.promotion == promotion &&
            (fileHint.isEmpty() || Squares.file(m.from) == fileHint[0] - 'a') &&
            (rankHint.isEmpty() || Squares.rank(m.from) == rankHint[0] - '1')
    }
    return when (candidates.size) {
        1 -> candidates[0]
        0 -> throw PgnParseException("The move $label is not legal in this position.")
        else -> throw PgnParseException("The move $label is ambiguous.")
    }
}

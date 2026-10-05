package com.checkmind.chess

/** PGN result token for [status]: `1-0`, `0-1`, `1/2-1/2`, or `*` while the game is still going. */
fun GameStatus.pgnResult(): String = when (this) {
    GameStatus.Ongoing -> "*"
    is GameStatus.Checkmate -> if (winner == Color.WHITE) "1-0" else "0-1"
    is GameStatus.Resigned -> if (winner == Color.WHITE) "1-0" else "0-1"
    GameStatus.Stalemate, GameStatus.DrawInsufficientMaterial,
    GameStatus.DrawThreefold, GameStatus.DrawFiftyMove -> "1/2-1/2"
}

/** The moves in SAN, in order, with check and mate marks (`e4`, `Nf3`, `O-O`, `Qxf7#`). */
fun Game.sanMoves(): List<String> {
    var pos = start
    return moves.map { move ->
        val san = pos.toSan(move)
        pos = pos.play(move)
        san
    }
}

/** Numbered movetext without tags or result, e.g. `1. e4 e5 2. Nf3 Nc6`. */
fun Game.movetext(): String {
    val offset = if (start.sideToMove == Color.WHITE) 0 else 1 // 1 when Black moves first
    val sb = StringBuilder()
    for ((i, san) in sanMoves().withIndex()) {
        val ply = i + offset
        val number = start.fullmoveNumber + ply / 2
        val whiteMove = ply % 2 == 0
        if (sb.isNotEmpty()) sb.append(' ')
        if (whiteMove) sb.append(number).append(". ") else if (i == 0) sb.append(number).append("... ")
        sb.append(san)
    }
    return sb.toString()
}

/**
 * The game as PGN: the seven required tags (plus SetUp/FEN for a custom start), a blank line,
 * the movetext and the result.
 */
fun Game.toPgn(
    date: String = "????.??.??",
    white: String = "White",
    black: String = "Black",
    event: String = "CheckMind game",
): String {
    val result = status.pgnResult()
    val sb = StringBuilder()
    fun tag(name: String, value: String) {
        sb.append('[').append(name).append(" \"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n")
    }
    tag("Event", event)
    tag("Site", "CheckMind")
    tag("Date", date)
    tag("Round", "-")
    tag("White", white)
    tag("Black", black)
    tag("Result", result)
    if (start.toFen() != Position.START_FEN) {
        tag("SetUp", "1")
        tag("FEN", start.toFen())
    }
    sb.append('\n')
    val text = movetext()
    if (text.isNotEmpty()) sb.append(text).append(' ')
    sb.append(result)
    return sb.toString()
}

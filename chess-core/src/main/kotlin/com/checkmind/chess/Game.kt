package com.checkmind.chess

sealed interface GameStatus {
    data object Ongoing : GameStatus
    data class Checkmate(val winner: Color) : GameStatus
    data object Stalemate : GameStatus
    data object DrawInsufficientMaterial : GameStatus
    data object DrawThreefold : GameStatus
    data object DrawFiftyMove : GameStatus
    data class Resigned(val winner: Color) : GameStatus
}

/** A game in progress: move history, undo, status and resignation. */
class Game(val start: Position = Position.START) {
    private val positions = arrayListOf(start)
    private val moveList = arrayListOf<Move>()
    private val keys = arrayListOf(start.repetitionKey())
    private var resignedWinner: Color? = null

    var status: GameStatus = evaluate()
        private set

    val position: Position get() = positions.last()
    val moves: List<Move> get() = moveList
    val lastMove: Move? get() = moveList.lastOrNull()

    fun legalMoves(): List<Move> = if (status is GameStatus.Ongoing) position.legalMoves() else emptyList()

    fun play(move: Move) {
        check(status is GameStatus.Ongoing) { "Game is over: $status" }
        val next = position.play(move)
        positions.add(next)
        moveList.add(move)
        keys.add(next.repetitionKey())
        status = evaluate()
    }

    /** Takes back one ply. Returns false if there is nothing to undo or the game was resigned. */
    fun undo(): Boolean {
        if (resignedWinner != null || moveList.isEmpty()) return false
        positions.removeAt(positions.lastIndex)
        moveList.removeAt(moveList.lastIndex)
        keys.removeAt(keys.lastIndex)
        status = evaluate()
        return true
    }

    fun resign(color: Color) {
        check(status is GameStatus.Ongoing) { "Game is over: $status" }
        resignedWinner = color.opposite
        status = evaluate()
    }

    private fun evaluate(): GameStatus {
        resignedWinner?.let { return GameStatus.Resigned(it) }
        val pos = position
        if (pos.legalMoves().isEmpty()) {
            return if (pos.isInCheck()) GameStatus.Checkmate(pos.sideToMove.opposite) else GameStatus.Stalemate
        }
        if (pos.hasInsufficientMaterial()) return GameStatus.DrawInsufficientMaterial
        val key = keys.last()
        if (keys.count { it == key } >= 3) return GameStatus.DrawThreefold
        if (pos.halfmoveClock >= 100) return GameStatus.DrawFiftyMove
        return GameStatus.Ongoing
    }
}

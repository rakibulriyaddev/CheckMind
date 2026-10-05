package com.checkmind.app.ui.game

import com.checkmind.chess.Color
import com.checkmind.chess.GameStatus
import com.checkmind.chess.Move
import com.checkmind.chess.Piece

/** One engine suggestion; [eval] is the score for the side to move, e.g. `+0.35` or `M3`. */
data class HintRow(val move: Move, val san: String, val eval: String)

data class PendingPromotion(val from: Int, val to: Int, val color: Color)

enum class Confirm { RESIGN, NEW_GAME }

data class GameUiState(
    val playerColor: Color,
    /** 64 entries, index = square (a1 = 0 ... h8 = 63). */
    val board: List<Piece?>,
    val sideToMove: Color,
    val selected: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    val captureTargets: Set<Int> = emptySet(),
    val lastMove: Pair<Int, Int>? = null,
    val checkSquare: Int? = null,
    val statusText: String = "",
    val result: GameStatus = GameStatus.Ongoing,
    val canUndo: Boolean = false,
    /** True once any move was played, also after resignation (when undo is off). */
    val hasMoves: Boolean = false,
    /** PGN text while the export dialog is open, otherwise null. */
    val exportPgn: String? = null,
    val hintAvailable: Boolean = false,
    val hintsOpen: Boolean = false,
    val hintsThinking: Boolean = false,
    val hintsFailed: Boolean = false,
    val hints: List<HintRow> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
    val confirm: Confirm? = null,
    val gameOverDialogVisible: Boolean = false,
    /** False while a dialog is open or the game is over. */
    val boardEnabled: Boolean = true,
)

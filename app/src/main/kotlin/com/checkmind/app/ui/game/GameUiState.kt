package com.checkmind.app.ui.game

import com.checkmind.app.engine.EngineLine
import com.checkmind.chess.Color
import com.checkmind.chess.GameStatus
import com.checkmind.chess.Move
import com.checkmind.chess.Piece
import java.util.Locale
import kotlin.math.exp

/** One engine suggestion; [eval] is the score for the side to move, e.g. `+0.35` or `M3`. */
data class HintRow(val move: Move, val san: String, val eval: String)

data class PendingPromotion(val from: Int, val to: Int, val color: Color)

/** Who is ahead, always from White's view. [whiteShare] is 0..1 (0.5 = equal) and drives the eval bar. */
data class Evaluation(val whiteShare: Float, val label: String) {
    companion object {
        val EVEN = Evaluation(0.5f, "0.00")

        /** [line] is scored for [sideToMove], the side whose turn it is in the analysed position. */
        fun from(line: EngineLine, sideToMove: Color): Evaluation {
            val sign = if (sideToMove == Color.WHITE) 1 else -1
            line.mate?.let { mate ->
                val white = mate * sign
                return Evaluation(if (white > 0) 1f else 0f, if (white > 0) "M$white" else "-M${-white}")
            }
            val cp = (line.cp ?: 0) * sign
            val share = (1.0 / (1.0 + exp(-SHARE_SLOPE * cp))).toFloat()
            return Evaluation(share, String.format(Locale.ROOT, "%+.2f", cp / 100.0))
        }

        /** The score of a finished game, or null while it is still on. */
        fun of(status: GameStatus): Evaluation? = when (status) {
            GameStatus.Ongoing -> null
            is GameStatus.Checkmate -> decided(status.winner)
            is GameStatus.Resigned -> decided(status.winner)
            else -> Evaluation(0.5f, "½-½")
        }

        private fun decided(winner: Color) =
            if (winner == Color.WHITE) Evaluation(1f, "1-0") else Evaluation(0f, "0-1")

        // The win-chance curve lichess uses for its eval bar: +4.00 is about an 81% share.
        private const val SHARE_SLOPE = 0.00368208
    }
}

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
    /** Engine score of the current position; null until the first answer, e.g. while the engine starts. */
    val evaluation: Evaluation? = null,
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

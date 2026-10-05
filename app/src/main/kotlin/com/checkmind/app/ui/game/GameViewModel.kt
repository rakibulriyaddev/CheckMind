package com.checkmind.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkmind.app.engine.EngineLine
import com.checkmind.app.engine.HintEngine
import com.checkmind.chess.Color
import com.checkmind.chess.Game
import com.checkmind.chess.GameStatus
import com.checkmind.chess.Move
import com.checkmind.chess.PieceType
import com.checkmind.chess.Squares
import com.checkmind.chess.toPgn
import com.checkmind.chess.toSan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.checkmind.chess.toSan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GameViewModel(
    val playerColor: Color,
    private val engine: HintEngine,
    /** Moves already played from the start position, e.g. from a pasted PGN. */
    initialMoves: List<Move> = emptyList(),
) : ViewModel() {

    private var game = Game().also { g -> initialMoves.forEach(g::play) }
    private var selected: Int? = null
    private var hintsOpen = false
    private var hintsThinking = false
    private var hintsFailed = false
    private var hintLines: List<EngineLine> = emptyList()
    private var hintJob: Job? = null
    private var pending: PendingPromotion? = null
    private var confirm: Confirm? = null
    private var gameOverDialog = game.status !is GameStatus.Ongoing
    private var exportOpen = false

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    // ------------------------------------------------------------------ board input

    private fun locked(): Boolean =
        game.status !is GameStatus.Ongoing || pending != null || confirm != null

    fun onSquareTap(square: Int) {
        if (locked()) return
        val pos = game.position
        val sel = selected
        if (sel != null) {
            if (sel == square) {
                selected = null
                refresh()
                return
            }
            if (pos.legalMovesFrom(sel).any { it.to == square }) {
                tryMove(sel, square)
                return
            }
        }
        selected = if (pos.pieceAt(square)?.color == pos.sideToMove) square else null
        refresh()
    }

    fun onDragStart(square: Int) {
        if (locked()) return
        val pos = game.position
        selected = if (pos.pieceAt(square)?.color == pos.sideToMove) square else null
        refresh()
    }

    fun onDrop(from: Int, to: Int) {
        if (locked() || from == to) return
        if (game.position.legalMovesFrom(from).any { it.to == to }) {
            tryMove(from, to)
        } else {
            refresh()
        }
    }

    private fun tryMove(from: Int, to: Int) {
        val candidates = game.position.legalMovesFrom(from).filter { it.to == to }
        when {
            candidates.isEmpty() -> refresh()
            candidates.size > 1 -> {
                pending = PendingPromotion(from, to, game.position.sideToMove)
                refresh()
            }
            else -> playMove(candidates[0])
        }
    }

    fun onPromotionChosen(type: PieceType) {
        val p = pending ?: return
        pending = null
        val move = Move.of(p.from, p.to, type)
        if (game.position.isLegal(move)) playMove(move) else refresh()
    }

    fun onPromotionCancelled() {
        pending = null
        refresh()
    }

    private fun playMove(move: Move) {
        game.play(move)
        selected = null
        closeHints()
        pending = null
        gameOverDialog = game.status !is GameStatus.Ongoing
        refresh()
    }

    // ------------------------------------------------------------------ controls

    fun onUndo() {
        if (confirm != null || pending != null) return
        if (game.undo()) {
            selected = null
            closeHints()
            gameOverDialog = false
        }
        refresh()
    }

    fun onHintsClick() {
        if (!hintAvailable()) return
        if (hintsOpen) {
            closeHints()
        } else {
            hintsOpen = true
            hintsThinking = true
            hintsFailed = false
            hintLines = emptyList()
            val moves = game.moves.toList()
            hintJob = viewModelScope.launch {
                try {
                    hintLines = engine.analyse(moves, HINT_LINES, HINT_MOVE_TIME_MS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    hintsFailed = true
                }
                hintsThinking = false
                refresh()
            }
        }
        refresh()
    }

    fun onHintRowClick(move: Move) {
        if (locked() || !game.position.isLegal(move)) return
        playMove(move)
    }

    fun onResignClick() {
        if (game.status !is GameStatus.Ongoing) return
        confirm = Confirm.RESIGN
        refresh()
    }

    fun onNewGameClick() {
        if (game.moves.isEmpty() || game.status !is GameStatus.Ongoing) {
            resetGame()
        } else {
            confirm = Confirm.NEW_GAME
        }
        refresh()
    }

    /** New game from the game-over dialog: no extra confirmation. */
    fun onNewGameConfirmedFromDialog() {
        resetGame()
        refresh()
    }

    fun onConfirm() {
        when (confirm) {
            Confirm.RESIGN -> {
                confirm = null
                if (game.status is GameStatus.Ongoing) {
                    game.resign(playerColor)
                    selected = null
                    closeHints()
                    gameOverDialog = true
                }
            }
            Confirm.NEW_GAME -> resetGame()
            null -> {}
        }
        refresh()
    }

    fun onDismissConfirm() {
        confirm = null
        refresh()
    }

    fun onExportClick() {
        if (game.moves.isEmpty()) return
        exportOpen = true
        refresh()
    }

    fun onExportDismiss() {
        exportOpen = false
        refresh()
    }

    fun onGameOverDismiss() {
        gameOverDialog = false
        refresh()
    }

    private fun resetGame() {
        game = Game()
        selected = null
        closeHints()
        pending = null
        confirm = null
        gameOverDialog = false
        exportOpen = false
    }

    // ------------------------------------------------------------------ state

    private fun hintAvailable(): Boolean = game.status is GameStatus.Ongoing

    private fun closeHints() {
        hintJob?.cancel()
        hintJob = null
        hintsOpen = false
        hintsThinking = false
        hintsFailed = false
        hintLines = emptyList()
    }

    private fun refresh() {
        _state.value = buildState()
    }

    private fun buildState(): GameUiState {
        val pos = game.position
        val status = game.status
        val hintsAllowed = hintAvailable()
        if (!hintsAllowed && hintsOpen) closeHints()

        val sel = selected
        val selectedMoves = if (sel != null) pos.legalMovesFrom(sel) else emptyList()
        val targets = selectedMoves.map { it.to }.toSet()
        val captures = selectedMoves.filter { m ->
            pos.pieceAt(m.to) != null ||
                (pos.pieceAt(m.from)?.type == PieceType.PAWN && Squares.file(m.from) != Squares.file(m.to))
        }.map { it.to }.toSet()

        val hintRows = if (hintsOpen) {
            hintLines.filter { pos.isLegal(it.move) }.map { HintRow(it.move, pos.toSan(it.move), it.evalLabel) }
        } else emptyList()

        val last = game.lastMove
        return GameUiState(
            playerColor = playerColor,
            board = pos.boardList(),
            sideToMove = pos.sideToMove,
            selected = sel,
            legalTargets = targets,
            captureTargets = captures,
            lastMove = last?.let { it.from to it.to },
            checkSquare = if (pos.isInCheck()) pos.kingSquare(pos.sideToMove) else null,
            statusText = statusText(status, pos.isInCheck(), pos.sideToMove),
            result = status,
            canUndo = game.moves.isNotEmpty() && status !is GameStatus.Resigned,
            hasMoves = game.moves.isNotEmpty(),
            exportPgn = if (exportOpen) game.toPgn(date = LocalDate.now().format(PGN_DATE)) else null,
            hintAvailable = hintsAllowed,
            hintsOpen = hintsOpen,
            hintsThinking = hintsThinking,
            hintsFailed = hintsFailed,
            hints = hintRows,
            pendingPromotion = pending,
            confirm = confirm,
            gameOverDialogVisible = gameOverDialog && status !is GameStatus.Ongoing,
            boardEnabled = status is GameStatus.Ongoing && pending == null && confirm == null,
        )
    }

    companion object {
        private const val HINT_LINES = 3
        private const val HINT_MOVE_TIME_MS = 2000

        private val PGN_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")

        fun statusText(status: GameStatus, inCheck: Boolean, sideToMove: Color): String = when (status) {
            GameStatus.Ongoing ->
                (if (inCheck) "Check · " else "") + "${sideToMove.label()} to move"
            is GameStatus.Checkmate -> "Checkmate, ${status.winner.label()} wins"
            GameStatus.Stalemate -> "Stalemate, draw"
            GameStatus.DrawInsufficientMaterial -> "Draw: insufficient material"
            GameStatus.DrawThreefold -> "Draw by repetition"
            GameStatus.DrawFiftyMove -> "Draw by fifty-move rule"
            is GameStatus.Resigned -> "${status.winner.opposite.label()} resigned, ${status.winner.label()} wins"
        }

        private fun Color.label(): String = if (this == Color.WHITE) "White" else "Black"

        fun factory(playerColor: Color, engine: HintEngine, initialMoves: List<Move> = emptyList()) = viewModelFactory {
            initializer { GameViewModel(playerColor, engine, initialMoves) }
        }
    }
}

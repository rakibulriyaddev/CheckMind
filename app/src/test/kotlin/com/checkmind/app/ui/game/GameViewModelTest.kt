package com.checkmind.app.ui.game

import com.checkmind.app.engine.EngineLine
import com.checkmind.app.engine.HintEngine
import com.checkmind.chess.Color
import com.checkmind.chess.GameStatus
import com.checkmind.chess.Move
import com.checkmind.chess.PieceType
import com.checkmind.chess.Squares
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    /** Answers with canned lines, or suspends forever when [gate] is set, to look at the "thinking" state. */
    private class FakeEngine(
        var lines: List<EngineLine> = listOf(EngineLine(Move.fromUci("e2e4"), 35, null)),
        var failure: Throwable? = null,
        val gate: CompletableDeferred<Unit>? = null,
    ) : HintEngine {
        val requests = ArrayList<List<Move>>()

        override suspend fun analyse(moves: List<Move>, lines: Int, moveTimeMs: Int): List<EngineLine> {
            requests.add(moves)
            gate?.await()
            failure?.let { throw it }
            return this.lines
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun sq(name: String) = Squares.parse(name)

    private fun movesOf(vararg uci: String) = uci.map { Move.fromUci(it) }

    private fun GameViewModel.move(from: String, to: String) {
        onSquareTap(sq(from))
        onSquareTap(sq(to))
    }

    // ------------------------------------------------------------------ basic play

    @Test
    fun startsWithStandardPosition() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        val s = vm.state.value
        assertEquals(Color.WHITE, s.sideToMove)
        assertEquals("White to move", s.statusText)
        assertEquals(32, s.board.count { it != null })
        assertFalse(s.canUndo)
    }

    @Test
    fun tapSelectsAndShowsTargets() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.onSquareTap(sq("e2"))
        val s = vm.state.value
        assertEquals(sq("e2"), s.selected)
        assertEquals(setOf(sq("e3"), sq("e4")), s.legalTargets)
        assertTrue(s.captureTargets.isEmpty())

        vm.onSquareTap(sq("e2")) // tap again deselects
        assertNull(vm.state.value.selected)
    }

    @Test
    fun cannotSelectOpponentPiece() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.onSquareTap(sq("e7"))
        assertNull(vm.state.value.selected)
    }

    @Test
    fun tapMovesAndSwitchesTurn() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.move("e2", "e4")
        val s = vm.state.value
        assertEquals(Color.BLACK, s.sideToMove)
        assertEquals(sq("e2") to sq("e4"), s.lastMove)
        assertTrue(s.canUndo)
    }

    @Test
    fun illegalDropLeavesBoardUnchanged() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        val before = vm.state.value.board
        vm.onDragStart(sq("e2"))
        vm.onDrop(sq("e2"), sq("e5"))
        assertEquals(before, vm.state.value.board)
        assertEquals(Color.WHITE, vm.state.value.sideToMove)
        assertEquals(sq("e2"), vm.state.value.selected)
    }

    @Test
    fun dragAndDropPlaysMove() {
        val vm = GameViewModel(Color.BLACK, FakeEngine())
        vm.onDragStart(sq("g1"))
        vm.onDrop(sq("g1"), sq("f3"))
        assertEquals(Color.BLACK, vm.state.value.sideToMove)
        assertNotNull(vm.state.value.board[sq("f3")])
    }

    // ------------------------------------------------------------------ promotion

    /** 1.a4 b5 2.axb5 a6 3.bxa6 Bb7 4.a7 h6, leaving a7xb8 as a promotion capture. */
    private fun GameViewModel.playToPromotion() {
        listOf(
            "a2" to "a4", "b7" to "b5", "a4" to "b5", "a7" to "a6", "b5" to "a6", "c8" to "b7",
            "a6" to "a7", "h7" to "h6",
        ).forEach { (f, t) -> move(f, t) }
    }

    @Test
    fun promotionFlow() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.playToPromotion()
        vm.move("a7", "b8")
        assertNotNull(vm.state.value.pendingPromotion)
        assertFalse(vm.state.value.boardEnabled)

        vm.onPromotionChosen(PieceType.QUEEN)
        assertNull(vm.state.value.pendingPromotion)
        assertEquals(PieceType.QUEEN, vm.state.value.board[sq("b8")]?.type)
        assertEquals(Color.BLACK, vm.state.value.sideToMove)
    }

    @Test
    fun promotionCancelKeepsPosition() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.playToPromotion()
        vm.move("a7", "b8")
        vm.onPromotionCancelled()
        assertNull(vm.state.value.pendingPromotion)
        assertEquals(PieceType.PAWN, vm.state.value.board[sq("a7")]?.type)
        assertEquals(Color.WHITE, vm.state.value.sideToMove)
    }

    // ------------------------------------------------------------------ hints

    @Test
    fun hintsAreOfferedOnlyOnMyTurn() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        assertTrue(vm.state.value.hintAvailable)
        vm.move("e2", "e4")
        assertFalse(vm.state.value.hintAvailable) // black to move
        vm.onHintsClick()
        assertFalse(vm.state.value.hintsOpen)
        vm.move("e7", "e5")
        assertTrue(vm.state.value.hintAvailable)

        assertFalse(GameViewModel(Color.BLACK, FakeEngine()).state.value.hintAvailable)
    }

    @Test
    fun hintsShowEngineLinesWithScoreAndAskForTheCurrentGame() {
        val engine = FakeEngine(
            lines = listOf(EngineLine(Move.fromUci("g1f3"), 32, null), EngineLine(Move.fromUci("d2d4"), -5, null)),
        )
        val vm = GameViewModel(Color.WHITE, engine)
        vm.move("e2", "e4")
        vm.move("e7", "e5")
        vm.onHintsClick()

        val s = vm.state.value
        assertTrue(s.hintsOpen)
        assertFalse(s.hintsThinking)
        assertEquals(listOf("Nf3", "d4"), s.hints.map { it.san })
        assertEquals(listOf("+0.32", "-0.05"), s.hints.map { it.eval })
        assertEquals(listOf(movesOf("e2e4", "e7e5")), engine.requests)
    }

    @Test
    fun hintsShowThinkingUntilTheEngineAnswers() {
        val gate = CompletableDeferred<Unit>()
        val vm = GameViewModel(Color.WHITE, FakeEngine(gate = gate))
        vm.onHintsClick()
        assertTrue(vm.state.value.hintsThinking)
        assertTrue(vm.state.value.hints.isEmpty())
        gate.complete(Unit)
        assertFalse(vm.state.value.hintsThinking)
        assertEquals(listOf("e4"), vm.state.value.hints.map { it.san })
    }

    @Test
    fun hintRowPlaysTheMoveAndClosesThePanel() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.onHintsClick()
        vm.onHintRowClick(vm.state.value.hints[0].move)
        val s = vm.state.value
        assertFalse(s.hintsOpen)
        assertEquals(Color.BLACK, s.sideToMove)
        assertEquals(PieceType.PAWN, s.board[sq("e4")]?.type)
    }

    @Test
    fun hintsToggleAndCloseOnMoveUndoAndLeftoverAnswer() {
        val gate = CompletableDeferred<Unit>()
        val vm = GameViewModel(Color.WHITE, FakeEngine(gate = gate))
        vm.onHintsClick()
        vm.onHintsClick()
        assertFalse(vm.state.value.hintsOpen)

        vm.onHintsClick() // asks again, still waiting
        vm.move("e2", "e4") // the position changed, so the pending answer is dropped
        gate.complete(Unit)
        val s = vm.state.value
        assertFalse(s.hintsOpen)
        assertTrue(s.hints.isEmpty())
    }

    @Test
    fun engineFailureIsShownNotThrown() {
        val vm = GameViewModel(Color.WHITE, FakeEngine(failure = IllegalStateException("no engine")))
        vm.onHintsClick()
        val s = vm.state.value
        assertTrue(s.hintsOpen)
        assertTrue(s.hintsFailed)
        assertFalse(s.hintsThinking)
    }

    @Test
    fun staleEngineMoveIsNotListed() {
        val vm = GameViewModel(Color.WHITE, FakeEngine(lines = listOf(EngineLine(Move.fromUci("e2e5"), 0, null))))
        vm.onHintsClick()
        assertTrue(vm.state.value.hints.isEmpty())
    }

    // ------------------------------------------------------------------ export

    @Test
    fun exportShowsPgnOfCurrentGame() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.onExportClick() // nothing to export yet
        assertNull(vm.state.value.exportPgn)
        assertFalse(vm.state.value.hasMoves)

        vm.move("e2", "e4")
        vm.move("e7", "e5")
        assertTrue(vm.state.value.hasMoves)
        vm.onExportClick()
        val pgn = vm.state.value.exportPgn!!
        assertTrue(pgn.contains("[Result \"*\"]"))
        assertTrue(pgn.endsWith("1. e4 e5 *"))

        vm.onExportDismiss()
        assertNull(vm.state.value.exportPgn)
    }

    @Test
    fun exportStillWorksAfterResignAndNewGameClosesIt() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.move("e2", "e4")
        vm.onResignClick()
        vm.onConfirm()
        assertFalse(vm.state.value.canUndo)
        assertTrue(vm.state.value.hasMoves)
        vm.onExportClick()
        assertTrue(vm.state.value.exportPgn!!.endsWith("1. e4 0-1"))

        vm.onNewGameConfirmedFromDialog()
        assertNull(vm.state.value.exportPgn)
        assertFalse(vm.state.value.hasMoves)
    }

    // ------------------------------------------------------------------ controls

    @Test
    fun resignLocksBoardAndDisablesUndo() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.move("e2", "e4")
        vm.onResignClick()
        assertEquals(Confirm.RESIGN, vm.state.value.confirm)
        vm.onDismissConfirm()
        assertNull(vm.state.value.confirm)

        vm.onResignClick()
        vm.onConfirm()
        val s = vm.state.value
        assertEquals(GameStatus.Resigned(Color.BLACK), s.result)
        assertTrue(s.gameOverDialogVisible)
        assertFalse(s.canUndo)
        assertFalse(s.boardEnabled)
        assertEquals("White resigned, Black wins", s.statusText)

        vm.onSquareTap(sq("e7"))
        assertNull(vm.state.value.selected)
    }

    @Test
    fun newGameResets() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.move("e2", "e4")
        vm.onNewGameClick()
        assertEquals(Confirm.NEW_GAME, vm.state.value.confirm)
        vm.onConfirm()
        val s = vm.state.value
        assertEquals(Color.WHITE, s.sideToMove)
        assertFalse(s.canUndo)
        assertNull(s.lastMove)
    }

    @Test
    fun newGameAtStartNeedsNoConfirmation() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.onNewGameClick()
        assertNull(vm.state.value.confirm)
    }

    @Test
    fun foolsMateShowsGameOver() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.move("f2", "f3")
        vm.move("e7", "e5")
        vm.move("g2", "g4")
        vm.move("d8", "h4")
        val s = vm.state.value
        assertEquals(GameStatus.Checkmate(Color.BLACK), s.result)
        assertTrue(s.gameOverDialogVisible)
        assertEquals("Checkmate, Black wins", s.statusText)
        assertEquals(sq("e1"), s.checkSquare)

        vm.onGameOverDismiss()
        assertFalse(vm.state.value.gameOverDialogVisible)
        assertTrue(vm.state.value.canUndo)

        vm.onUndo()
        assertEquals(GameStatus.Ongoing, vm.state.value.result)
    }

    @Test
    fun undoClearsSelection() {
        val vm = GameViewModel(Color.WHITE, FakeEngine())
        vm.move("e2", "e4")
        vm.move("e7", "e5")
        vm.onSquareTap(sq("g1"))
        assertNotNull(vm.state.value.selected)
        vm.onUndo()
        assertNull(vm.state.value.selected)
    }
}

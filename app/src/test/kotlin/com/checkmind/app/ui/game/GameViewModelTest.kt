package com.checkmind.app.ui.game

import com.checkmind.app.data.BookSource
import com.checkmind.chess.Color
import com.checkmind.chess.GameStatus
import com.checkmind.chess.Move
import com.checkmind.chess.PieceType
import com.checkmind.chess.Squares
import com.checkmind.chess.book.BookTable
import com.checkmind.chess.book.BookTableBuilder
import com.checkmind.chess.book.OpeningBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private class FakeBook(initial: OpeningBook?) : BookSource {
        val flow = MutableStateFlow(initial)
        override val book: StateFlow<OpeningBook?> get() = flow
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun sq(name: String) = Squares.parse(name)

    private fun movesOf(vararg uci: String) = uci.map { Move.fromUci(it) }

    /** White won 1.e4 e5 2.Nf3 Nc6; Black won 1.e4 e5 2.Nf3 Nc6 (shares the line for symmetry). */
    private fun book(): OpeningBook {
        val w = BookTableBuilder().apply {
            insert(Color.WHITE, movesOf("e2e4", "e7e5", "g1f3", "b8c6"))
            insert(Color.WHITE, movesOf("e2e4", "c7c5", "g1f3", "d7d6"))
        }
        val b = BookTableBuilder().apply {
            insert(Color.BLACK, movesOf("e2e4", "e7e5", "g1f3", "b8c6"))
            insert(Color.BLACK, movesOf("d2d4", "d7d5", "c2c4", "e7e6"))
        }
        return OpeningBook(w.build(), b.build(), 2, 2)
    }

    private fun GameViewModel.move(from: String, to: String) {
        onSquareTap(sq(from))
        onSquareTap(sq(to))
    }

    // ------------------------------------------------------------------ basic play

    @Test
    fun startsWithStandardPosition() {
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
        val s = vm.state.value
        assertEquals(Color.WHITE, s.sideToMove)
        assertEquals("White to move", s.statusText)
        assertEquals(32, s.board.count { it != null })
        assertFalse(s.canUndo)
        assertFalse(s.hintAvailable)
    }

    @Test
    fun tapSelectsAndShowsTargets() {
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
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
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
        vm.onSquareTap(sq("e7"))
        assertNull(vm.state.value.selected)
    }

    @Test
    fun tapMovesAndSwitchesTurn() {
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
        vm.move("e2", "e4")
        val s = vm.state.value
        assertEquals(Color.BLACK, s.sideToMove)
        assertEquals(sq("e2") to sq("e4"), s.lastMove)
        assertTrue(s.canUndo)
    }

    @Test
    fun illegalDropLeavesBoardUnchanged() {
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
        val before = vm.state.value.board
        vm.onDragStart(sq("e2"))
        vm.onDrop(sq("e2"), sq("e5"))
        assertEquals(before, vm.state.value.board)
        assertEquals(Color.WHITE, vm.state.value.sideToMove)
        assertEquals(sq("e2"), vm.state.value.selected)
    }

    @Test
    fun dragAndDropPlaysMove() {
        val vm = GameViewModel(Color.BLACK, FakeBook(null))
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
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
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
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
        vm.playToPromotion()
        vm.move("a7", "b8")
        vm.onPromotionCancelled()
        assertNull(vm.state.value.pendingPromotion)
        assertEquals(PieceType.PAWN, vm.state.value.board[sq("a7")]?.type)
        assertEquals(Color.WHITE, vm.state.value.sideToMove)
    }

    // ------------------------------------------------------------------ hints

    @Test
    fun whiteHintsFollowTheBook() {
        val vm = GameViewModel(Color.WHITE, FakeBook(book()))
        assertTrue(vm.state.value.hintAvailable)

        vm.onHintsClick()
        var s = vm.state.value
        assertTrue(s.hintsOpen)
        assertEquals(listOf("e4"), s.hints.map { it.san })
        assertEquals("e4 · 2 wins", s.hints[0].label)

        vm.onHintRowClick(s.hints[0].move)
        s = vm.state.value
        assertFalse(s.hintsOpen)
        assertEquals(Color.BLACK, s.sideToMove)
        assertFalse("not my turn", s.hintAvailable)

        vm.move("e7", "e5")
        assertTrue(vm.state.value.hintAvailable)
        vm.onHintsClick()
        assertEquals(listOf("Nf3"), vm.state.value.hints.map { it.san })
    }

    @Test
    fun hintsDisappearWhenLeavingTheBookAndReturnOnUndo() {
        val vm = GameViewModel(Color.WHITE, FakeBook(book()))
        vm.move("e2", "e4")
        vm.move("e7", "e6") // e6 is not in the book
        assertFalse(vm.state.value.hintAvailable)
        vm.onUndo()
        vm.move("e7", "e5")
        assertTrue(vm.state.value.hintAvailable)
        vm.onUndo()
        assertFalse(vm.state.value.hintAvailable) // black to move again
        vm.onUndo()
        assertTrue(vm.state.value.hintAvailable) // start position, white to move
    }

    @Test
    fun blackUsesOnlyBlackWins() {
        val vm = GameViewModel(Color.BLACK, FakeBook(book()))
        assertFalse(vm.state.value.hintAvailable) // white to move
        vm.move("d2", "d4")
        assertTrue(vm.state.value.hintAvailable)
        vm.onHintsClick()
        assertEquals(listOf("d5"), vm.state.value.hints.map { it.san })
    }

    @Test
    fun blackHintsFromSharedLine() {
        val vm = GameViewModel(Color.BLACK, FakeBook(book()))
        vm.move("e2", "e4")
        assertTrue(vm.state.value.hintAvailable)
        vm.onHintsClick()
        assertEquals(listOf("e5"), vm.state.value.hints.map { it.san })
    }

    @Test
    fun hintsOpenClosesOnAnyMoveAndToggles() {
        val vm = GameViewModel(Color.WHITE, FakeBook(book()))
        vm.onHintsClick()
        assertTrue(vm.state.value.hintsOpen)
        vm.onHintsClick()
        assertFalse(vm.state.value.hintsOpen)
        vm.onHintsClick()
        vm.move("g1", "f3")
        assertFalse(vm.state.value.hintsOpen)
    }

    @Test
    fun lateArrivingBookShowsHintButton() {
        val source = FakeBook(null)
        val vm = GameViewModel(Color.WHITE, source)
        assertFalse(vm.state.value.hintAvailable)
        source.flow.value = book()
        assertTrue(vm.state.value.hintAvailable)
    }

    @Test
    fun emptyBookNeverShowsHints() {
        val vm = GameViewModel(Color.WHITE, FakeBook(OpeningBook(BookTable.EMPTY, BookTable.EMPTY, 0, 0)))
        assertFalse(vm.state.value.hintAvailable)
    }

    // ------------------------------------------------------------------ controls

    @Test
    fun resignLocksBoardAndDisablesUndo() {
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
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
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
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
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
        vm.onNewGameClick()
        assertNull(vm.state.value.confirm)
    }

    @Test
    fun foolsMateShowsGameOver() {
        val vm = GameViewModel(Color.WHITE, FakeBook(null))
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
    fun undoClearsSelectionAndHints() {
        val vm = GameViewModel(Color.WHITE, FakeBook(book()))
        vm.move("e2", "e4")
        vm.move("e7", "e5")
        vm.onHintsClick()
        assertTrue(vm.state.value.hintsOpen)
        vm.onUndo()
        assertFalse(vm.state.value.hintsOpen)
        assertNull(vm.state.value.selected)
    }
}

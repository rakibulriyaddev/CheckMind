package com.checkmind.app.ui.game

import com.checkmind.app.data.BookSource
import com.checkmind.book.BookBuilder
import com.checkmind.book.PgnParser
import com.checkmind.chess.Color
import com.checkmind.chess.Move
import com.checkmind.chess.Position
import com.checkmind.chess.book.BookCodec
import com.checkmind.chess.book.OpeningBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * End-to-end check of hints against real games. It builds the book from every PGN in
 * book-builder/data exactly like the Gradle task (including the codec round trip), then plays the
 * first [SAMPLE] games through [GameViewModel] by tapping squares. On every turn of the side that
 * won the game, the hints shown must equal an independent count made straight from the games:
 * the same moves with the same win counts, keyed by position. Skipped when the data is absent
 * (the 91 MB bulk file is not in git). Takes about 6 minutes, so it only runs when the environment
 * variable CHECKMIND_REAL_DATA_TEST is set:
 *
 *     CHECKMIND_REAL_DATA_TEST=1 ./gradlew :app:testDebugUnitTest --tests "*HintsAgainstRealGamesTest*"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HintsAgainstRealGamesTest {
    private class FakeBook(initial: OpeningBook?) : BookSource {
        override val book: StateFlow<OpeningBook?> = MutableStateFlow(initial)
    }

    private class Sample(val index: Int, val winner: Color, val moves: List<Move>, val keys: List<String?>)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstThousandGamesGetCorrectHints() {
        assumeTrue("set CHECKMIND_REAL_DATA_TEST=1 to run", System.getenv("CHECKMIND_REAL_DATA_TEST") != null)
        val files = File("../book-builder/data").listFiles { f -> f.isFile && f.name.endsWith(".pgn") }
            ?.sortedBy { it.name }
        assumeTrue("no PGN data to test with", !files.isNullOrEmpty())

        // Independent oracle: position key -> move bits -> number of won games, filled while the
        // book is built. Positions are the ones met by the sample games; later games only add to them.
        val counts = HashMap<String, HashMap<Int, Int>>()
        val samples = ArrayList<Sample>()
        var seen = 0

        val tee = files!!.asSequence().flatMap { file ->
            val lenient = file.name.startsWith("bulk")
            PgnParser.parseSequence(file.readText(Charsets.UTF_8))
                .map { it.copy(source = file.name, lenient = lenient) }
        }.onEach { g ->
            seen++
            val result = g.termination?.takeIf { it != "*" } ?: g.tags["Result"]
            val winner = when (result) {
                "1-0" -> Color.WHITE
                "0-1" -> Color.BLACK
                else -> return@onEach
            }
            if (g.tags.containsKey("FEN") || g.tags["SetUp"] == "1") return@onEach
            val moves = BookBuilder.movesOf(g) ?: return@onEach
            if (moves.isEmpty()) return@onEach

            val inSample = samples.size < SAMPLE
            var pos = Position.START
            val keys = ArrayList<String?>(moves.size)
            for (m in moves) {
                var key: String? = null
                if (pos.sideToMove == winner) {
                    key = "$winner|${pos.repetitionKey()}"
                    val perMove = if (inSample) counts.getOrPut(key) { HashMap() } else counts[key]
                    perMove?.merge(m.bits, 1, Int::plus)
                }
                keys.add(key)
                pos = pos.play(m)
            }
            if (inSample) samples.add(Sample(g.index, winner, moves, keys))
        }

        val (built, summary) = BookBuilder.build(tee)
        val book = BookCodec.read(BookCodec.write(built)) // what the app actually loads
        println("games in files: $seen, used: ${summary.whiteGames + summary.blackGames}, skipped: ${summary.skipped.size}")
        assertTrue("fewer than $SAMPLE usable games in the data", samples.size == SAMPLE)

        val source = FakeBook(book)
        val problems = ArrayList<String>()
        var hintChecks = 0
        var multiHint = 0
        var maxHints = 0

        for (s in samples) {
            val vm = GameViewModel(s.winner, source)
            for ((ply, move) in s.moves.withIndex()) {
                val where = "game ${s.index} ply ${ply + 1}"
                val state = vm.state.value
                val key = s.keys[ply]

                if (key == null) { // opponent's turn: no hint button
                    if (state.hintAvailable) problems.add("$where: hint button on the opponent's turn")
                } else {
                    hintChecks++
                    if (!state.hintAvailable) {
                        problems.add("$where: no hints, but this very game is in the book")
                    } else {
                        vm.onHintsClick()
                        val hints = vm.state.value.hints
                        val shown = hints.associate { it.move.bits to it.wins }
                        val expected = counts.getValue(key)
                        if (shown != expected) problems.add("$where: shown $shown, expected $expected")
                        if (move.bits !in shown) problems.add("$where: the move played in the game is not a hint")
                        if (hints.zipWithNext().any { (a, b) -> a.wins < b.wins }) problems.add("$where: hints not sorted by wins")
                        if (hints.size > 1) multiHint++
                        maxHints = maxOf(maxHints, hints.size)
                        vm.onHintsClick() // close the panel again
                    }
                }

                vm.onSquareTap(move.from)
                vm.onSquareTap(move.to)
                if (vm.state.value.pendingPromotion != null) vm.onPromotionChosen(move.promotion!!)
                if (vm.state.value.lastMove != (move.from to move.to)) {
                    problems.add("$where: ${move.uci()} was not played")
                    break
                }
            }
            if (problems.size > 50) break
        }

        println("hint checks: $hintChecks across ${samples.size} games (positions with several hints: $multiHint, most hints at once: $maxHints)")
        assertTrue("${problems.size} problems, first ones:\n" + problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val SAMPLE = 1000
    }
}

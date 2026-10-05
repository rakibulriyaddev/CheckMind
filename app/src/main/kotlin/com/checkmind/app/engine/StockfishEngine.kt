package com.checkmind.app.engine

import android.content.Context
import com.checkmind.chess.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.TreeMap

/**
 * Stockfish 19 at full strength. The NNUE net ships as an asset and are copied to app storage on first
 * use, because the engine reads them from files. One engine lives for the whole process, and only one
 * analysis runs at a time.
 */
class StockfishEngine private constructor(private val context: Context) : HintEngine {
    private val output = Channel<String>(Channel.UNLIMITED)
    private val lock = Mutex()
    private var ready = false
    private var readerStarted = false

    override suspend fun analyse(moves: List<Move>, lines: Int, moveTimeMs: Int): List<EngineLine> =
        lock.withLock {
            withContext(Dispatchers.IO) {
                ensureReady()
                search(moves, lines, moveTimeMs)
            }
        }

    private suspend fun search(moves: List<Move>, lines: Int, moveTimeMs: Int): List<EngineLine> {
        drain()
        send("setoption name MultiPV value $lines")
        send("position startpos" + if (moves.isEmpty()) "" else " moves " + moves.joinToString(" ") { it.uci() })
        send("go movetime $moveTimeMs")

        val latest = TreeMap<Int, EngineLine>()
        var finished = false
        try {
            withTimeout(moveTimeMs + SEARCH_GRACE_MS) {
                while (true) {
                    val line = output.receive()
                    if (line.startsWith("bestmove")) {
                        finished = true
                        break
                    }
                    parseInfoLine(line)?.let { (multipv, entry) -> latest[multipv] = entry }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Engine did not answer", e)
        } finally {
            if (!finished) withContext(NonCancellable) { abandonSearch() }
        }
        return latest.values.toList()
    }

    /** Stops a running search and swallows its `bestmove`, so the next analysis starts clean. */
    private suspend fun abandonSearch() {
        send("stop")
        try {
            withTimeout(SEARCH_GRACE_MS) {
                while (!output.receive().startsWith("bestmove")) {
                    // skip search output
                }
            }
        } catch (e: Exception) {
            ready = false // engine out of sync, set it up again next time
        }
    }

    private suspend fun ensureReady() {
        if (ready) return
        val net = installNet()
        StockfishNative.start()
        if (!readerStarted) {
            readerStarted = true
            Thread({
                while (true) output.trySend(StockfishNative.readLine() ?: break)
            }, "stockfish-reader").apply { isDaemon = true }.start()
        }
        drain()
        send("uci")
        await("uciok")
        send("setoption name Threads value ${(Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)}")
        send("setoption name Hash value 64")
        send("setoption name EvalFile value $net")
        send("isready")
        await("readyok")
        ready = true
    }

    private fun drain() {
        while (output.tryReceive().isSuccess) {
            // drop banner and leftovers
        }
    }

    private suspend fun await(token: String) = withTimeout(SETUP_TIMEOUT_MS) {
        while (true) {
            val line = output.receive()
            check(!(line.startsWith("info string") && line.contains("ERROR", ignoreCase = true))) { line }
            if (line == token) break
        }
    }

    private fun send(command: String) = StockfishNative.write(command)

    /** Copies the net from assets to app storage once and returns its path. Older nets are removed. */
    private fun installNet(): String {
        val dir = File(context.filesDir, "nnue").also { it.mkdirs() }
        val names = context.assets.list("nnue").orEmpty().filter { it.endsWith(".nnue") }
        check(names.size == 1) { "NNUE net missing from assets" }
        val name = names[0]
        dir.listFiles { f -> f.name != name }?.forEach { it.delete() }
        val target = File(dir, name)
        val size = context.assets.openFd("nnue/$name").use { it.length }
        if (!target.exists() || target.length() != size) {
            val tmp = File(dir, "$name.part")
            context.assets.open("nnue/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "Could not install $name" }
        }
        return target.absolutePath
    }

    companion object {
        private const val SEARCH_GRACE_MS = 5_000L
        private const val SETUP_TIMEOUT_MS = 60_000L

        @Volatile private var instance: StockfishEngine? = null

        fun get(context: Context): StockfishEngine =
            instance ?: synchronized(this) {
                instance ?: StockfishEngine(context.applicationContext).also { instance = it }
            }
    }
}

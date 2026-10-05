package com.checkmind.app.engine

import com.checkmind.chess.Move
import java.util.Locale

/** One candidate move from the engine. [cp] is centipawns, [mate] is moves to mate; both from the mover's view. */
data class EngineLine(val move: Move, val cp: Int?, val mate: Int?) {
    val evalLabel: String
        get() = when {
            mate != null -> if (mate > 0) "M$mate" else "-M${-mate}"
            cp != null -> String.format(Locale.ROOT, "%+.2f", cp / 100.0)
            else -> ""
        }
}

/** Anything that can suggest moves for the side to move. */
interface HintEngine {
    /** The best [lines] moves after [moves] from the start position, best first. */
    suspend fun analyse(moves: List<Move>, lines: Int, moveTimeMs: Int): List<EngineLine>
}

/** Parses one UCI `info` line. Returns null for lines that carry no usable move and score. */
internal fun parseInfoLine(line: String): Pair<Int, EngineLine>? {
    val t = line.trim().split(' ').filter { it.isNotEmpty() }
    if (t.firstOrNull() != "info" || "string" in t) return null
    var multipv = 1
    var cp: Int? = null
    var mate: Int? = null
    var pv: String? = null
    var i = 1
    while (i < t.size) {
        when (t[i]) {
            "multipv" -> {
                multipv = t.getOrNull(i + 1)?.toIntOrNull() ?: return null
                i += 2
            }
            "score" -> {
                val value = t.getOrNull(i + 2)?.toIntOrNull() ?: return null
                when (t.getOrNull(i + 1)) {
                    "cp" -> cp = value
                    "mate" -> mate = value
                    else -> return null
                }
                i += 3
                // A bound is not a finished score for the move.
                if (t.getOrNull(i) == "lowerbound" || t.getOrNull(i) == "upperbound") return null
            }
            "pv" -> {
                pv = t.getOrNull(i + 1)
                i = t.size
            }
            else -> i++
        }
    }
    if (pv == null || (cp == null && mate == null)) return null
    val move = try {
        Move.fromUci(pv)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return multipv to EngineLine(move, cp, mate)
}

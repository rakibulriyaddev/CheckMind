package com.checkmind.book

/**
 * One game from a PGN file. [moves] are raw SAN tokens (annotations, comments, variations,
 * NAGs and move numbers removed). [termination] is the movetext result token, if present.
 */
data class PgnGame(
    val index: Int,
    val tags: Map<String, String>,
    val moves: List<String>,
    val termination: String?,
    /** File the game came from, for messages. */
    val source: String = "",
    /** Bulk data: a bad game is skipped with a warning instead of failing the build. */
    val lenient: Boolean = false,
) {
    val label: String get() = if (source.isEmpty()) "game $index" else "$source game $index"
}

object PgnParser {
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
    private val MOVE_NUMBER = Regex("^\\d+\\.+")

    fun parse(text: String): List<PgnGame> = parseSequence(text).toList()

    /** Lazily yields games, so a huge file never holds all its games in memory at once. */
    fun parseSequence(text: String): Sequence<PgnGame> = sequence {
        var count = 0
        var tags = LinkedHashMap<String, String>()
        var moves = ArrayList<String>()
        var termination: String? = null

        fun finish(): PgnGame? {
            val game = if (tags.isNotEmpty() || moves.isNotEmpty() || termination != null) {
                PgnGame(++count, tags, moves, termination)
            } else null
            tags = LinkedHashMap()
            moves = ArrayList()
            termination = null
            return game
        }

        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '{' -> {
                    val end = text.indexOf('}', i + 1)
                    i = if (end < 0) n else end + 1
                }
                c == ';' -> {
                    val end = text.indexOf('\n', i)
                    i = if (end < 0) n else end + 1
                }
                c == '%' && (i == 0 || text[i - 1] == '\n') -> {
                    val end = text.indexOf('\n', i)
                    i = if (end < 0) n else end + 1
                }
                c == '(' -> i = skipVariation(text, i)
                c == '[' -> {
                    if (moves.isNotEmpty() || termination != null) finish()?.let { yield(it) }
                    i = readTag(text, i, tags)
                }
                c == '$' -> {
                    i++
                    while (i < n && text[i].isDigit()) i++
                }
                else -> {
                    val start = i
                    while (i < n && !text[i].isWhitespace() && text[i] !in "{(;[") i++
                    if (i == start) {
                        i++
                        continue
                    }
                    val token = text.substring(start, i)
                    if (token in RESULTS) {
                        termination = token
                        finish()?.let { yield(it) }
                    } else {
                        cleanMove(token)?.let { moves.add(it) }
                    }
                }
            }
        }
        finish()?.let { yield(it) }
    }

    private fun cleanMove(token: String): String? {
        var t = MOVE_NUMBER.replace(token, "")
        t = t.trimEnd('!', '?')
        if (t.isEmpty()) return null
        if (t == "0-0") t = "O-O"
        if (t == "0-0-0") t = "O-O-O"
        if (t.startsWith("0-0-0")) t = "O-O-O" + t.substring(5)
        else if (t.startsWith("0-0")) t = "O-O" + t.substring(3)
        return t
    }

    private fun skipVariation(text: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
                '{' -> {
                    val end = text.indexOf('}', i)
                    if (end < 0) return text.length
                    i = end
                }
            }
            i++
        }
        return text.length
    }

    private fun readTag(text: String, start: Int, tags: MutableMap<String, String>): Int {
        val n = text.length
        var j = start + 1
        val keyStart = j
        while (j < n && !text[j].isWhitespace() && text[j] != ']') j++
        val key = text.substring(keyStart, j)
        while (j < n && text[j].isWhitespace()) j++
        val value = StringBuilder()
        if (j < n && text[j] == '"') {
            j++
            while (j < n && text[j] != '"') {
                if (text[j] == '\\' && j + 1 < n) j++
                value.append(text[j])
                j++
            }
            j++
        }
        val close = text.indexOf(']', j)
        if (key.isNotEmpty()) tags[key] = value.toString()
        return if (close < 0) n else close + 1
    }
}

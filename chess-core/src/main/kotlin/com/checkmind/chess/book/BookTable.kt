package com.checkmind.chess.book

import com.checkmind.chess.Color
import com.checkmind.chess.Move
import com.checkmind.chess.Position

/**
 * Immutable table of "in this position, this move won this many games", stored as two flat arrays:
 *
 *  - `packed[i]` = `positionKey shl 16 or moveCode`, strictly ascending (see [PositionKey.pack])
 *  - `wins[i]`   = number of won games that played that move there (unsigned 16-bit, saturating)
 *
 * All moves from one position are contiguous, so a lookup is one binary search plus a short scan.
 * Cost: 10 bytes per entry.
 */
class BookTable(
    private val packed: LongArray,
    private val wins: ShortArray,
) {
    init {
        require(packed.size == wins.size) { "Column sizes differ" }
    }

    val size: Int get() = packed.size

    fun packedAt(index: Int): Long = packed[index]
    fun winsAt(index: Int): Int = wins[index].toInt() and 0xFFFF

    /** All (move, wins) recorded for [key], in table order (move code ascending). */
    fun lookup(key: Long): List<BookEdge> {
        var lo = 0
        var hi = packed.size
        val first = key shl 16
        while (lo < hi) { // lower bound of `first`
            val mid = (lo + hi) ushr 1
            if (packed[mid] < first) lo = mid + 1 else hi = mid
        }
        var i = lo
        var out: ArrayList<BookEdge>? = null
        while (i < packed.size && PositionKey.keyOf(packed[i]) == key) {
            if (out == null) out = ArrayList(4)
            out.add(BookEdge(Move(PositionKey.moveOf(packed[i])), winsAt(i)))
            i++
        }
        return out ?: emptyList()
    }

    /** Number of distinct positions in the table. */
    fun positionCount(): Int {
        var n = 0
        var prev = -1L
        for (v in packed) {
            val k = PositionKey.keyOf(v)
            if (k != prev) {
                n++
                prev = k
            }
        }
        return n
    }

    internal fun packedArray(): LongArray = packed
    internal fun winsArray(): ShortArray = wins

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookTable) return false
        return packed.contentEquals(other.packed) && wins.contentEquals(other.wins)
    }

    override fun hashCode(): Int = 31 * packed.contentHashCode() + wins.contentHashCode()

    companion object {
        val EMPTY = BookTable(LongArray(0), ShortArray(0))
    }
}

/**
 * Collects the moves one colour played in games that colour won, then builds a [BookTable].
 * Only positions where that colour was to move are recorded: hints are asked only on its turn.
 */
class BookTableBuilder {
    private var raw = LongArray(1 shl 16)
    private var count = 0

    /** Raw entries added so far (before merging equal ones). */
    val entryCount: Int get() = count

    /** Adds the first [n] packed entries of one finished game. */
    fun addGame(entries: LongArray, n: Int) {
        if (count + n > raw.size) raw = raw.copyOf(maxOf(raw.size * 2, count + n))
        System.arraycopy(entries, 0, raw, count, n)
        count += n
    }

    /** Replays [moves] from the start position and records those played by [winner]. */
    fun insert(winner: Color, moves: List<Move>) {
        var pos = Position.START
        val entries = LongArray(moves.size)
        var n = 0
        for (m in moves) {
            if (pos.sideToMove == winner) entries[n++] = PositionKey.pack(PositionKey.of(pos), m.bits)
            pos = pos.play(m)
        }
        addGame(entries, n)
    }

    fun build(): BookTable {
        if (count == 0) return BookTable.EMPTY
        val sorted = raw.copyOf(count)
        java.util.Arrays.sort(sorted)
        val packed = LongArray(count)
        val wins = ShortArray(count)
        var edges = 0
        var i = 0
        while (i < count) {
            var j = i
            while (j < count && sorted[j] == sorted[i]) j++
            packed[edges] = sorted[i]
            wins[edges] = minOf(j - i, 0xFFFF).toShort()
            edges++
            i = j
        }
        return BookTable(packed.copyOf(edges), wins.copyOf(edges))
    }
}

package com.checkmind.chess.book

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

class BookFormatException(message: String) : Exception(message)

/**
 * Binary book format, version 3 (big-endian). One sorted position table per colour:
 *
 *     magic "CMBK" | version u8 | whiteGames u16 | blackGames u16 | white table | black table
 *     table := entryCount u32 | packed u64[entryCount] | wins u16[entryCount]
 *
 * `packed` is `positionKey(47 bits) shl 16 or moveCode`, see [PositionKey]. Entries are strictly
 * ascending, so equal positions are adjacent and no entry repeats. `wins` is at least 1.
 * The game counts in the header saturate at 65535, like each entry's win count.
 */
object BookCodec {
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val VERSION = 3
    private const val MAX_ENTRIES = 80_000_000
    private const val CHUNK = 1 shl 16

    fun write(book: OpeningBook): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(MAGIC)
        out.writeByte(VERSION)
        out.writeShort(minOf(book.whiteGames, 0xFFFF))
        out.writeShort(minOf(book.blackGames, 0xFFFF))
        writeTable(out, book.white)
        writeTable(out, book.black)
        out.flush()
        return bytes.toByteArray()
    }

    private fun writeTable(out: DataOutputStream, table: BookTable) {
        out.writeInt(table.size)
        val packed = ByteBuffer.allocate(table.size * 8)
        packed.asLongBuffer().put(table.packedArray())
        out.write(packed.array())
        val wins = ByteBuffer.allocate(table.size * 2)
        wins.asShortBuffer().put(table.winsArray())
        out.write(wins.array())
    }

    fun read(data: ByteArray): OpeningBook = read(ByteArrayInputStream(data))

    fun read(input: InputStream): OpeningBook {
        val d = DataInputStream(input.buffered(CHUNK))
        try {
            val magic = ByteArray(4)
            d.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw BookFormatException("Bad magic")
            val version = d.readUnsignedByte()
            if (version != VERSION) throw BookFormatException("Unsupported version $version")
            val whiteGames = d.readUnsignedShort()
            val blackGames = d.readUnsignedShort()
            val white = readTable(d)
            val black = readTable(d)
            if (d.read() != -1) throw BookFormatException("Trailing bytes")
            return OpeningBook(white, black, whiteGames, blackGames)
        } catch (e: EOFException) {
            throw BookFormatException("Truncated data")
        }
    }

    private fun readTable(d: DataInputStream): BookTable {
        val n = d.readInt()
        if (n < 0 || n > MAX_ENTRIES) throw BookFormatException("Bad entry count $n")

        val packed = LongArray(n)
        val chunk = ByteArray(CHUNK)
        var done = 0
        while (done < n) {
            val take = minOf(chunk.size / 8, n - done)
            d.readFully(chunk, 0, take * 8)
            ByteBuffer.wrap(chunk, 0, take * 8).asLongBuffer().get(packed, done, take)
            done += take
        }
        val wins = ShortArray(n)
        done = 0
        while (done < n) {
            val take = minOf(chunk.size / 2, n - done)
            d.readFully(chunk, 0, take * 2)
            ByteBuffer.wrap(chunk, 0, take * 2).asShortBuffer().get(wins, done, take)
            done += take
        }

        for (i in 0 until n) {
            if (packed[i] < 0) throw BookFormatException("Bad entry")
            if (i > 0 && packed[i] <= packed[i - 1]) throw BookFormatException("Entries not sorted")
            if (wins[i].toInt() == 0) throw BookFormatException("Zero win count")
        }
        return BookTable(packed, wins)
    }
}

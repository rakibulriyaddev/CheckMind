package com.checkmind.book

import com.checkmind.chess.book.BookCodec
import java.io.File
import kotlin.system.exitProcess

/**
 * Usage: BuildBook <input> <output.bin>
 *
 * <input> is a PGN file or a directory of *.pgn files. Files whose name starts with "bulk"
 * are lenient: a bad game in them is skipped with a warning. All other files are strict:
 * a bad game fails the build.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: BuildBook <input.pgn | input-dir> <output.bin>")
        exitProcess(2)
    }
    val input = File(args[0])
    val output = File(args[1])
    try {
        val files = when {
            input.isDirectory -> input.listFiles { f -> f.isFile && f.name.endsWith(".pgn") }!!.sortedBy { it.name }
            input.isFile -> listOf(input)
            else -> throw BookBuildException("input not found: ${input.absolutePath}")
        }
        if (files.isEmpty()) throw BookBuildException("no .pgn files in ${input.absolutePath}")

        // Lazy: each file is read when reached and its games are consumed one at a time.
        val games = files.asSequence().flatMap { file ->
            val lenient = file.name.startsWith("bulk")
            PgnParser.parseSequence(file.readText(Charsets.UTF_8)).map { it.copy(source = file.name, lenient = lenient) }
        }
        val (book, summary) = BookBuilder.build(games)
        val bytes = BookCodec.write(book)
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)

        println("Book built: ${output.path}")
        println("  input files     : ${files.joinToString { it.name }}")
        println("  games read      : ${summary.gamesRead}")
        println("  white wins used : ${summary.whiteGames} (${summary.whiteEntries} entries, ${summary.whitePositions} positions)")
        println("  black wins used : ${summary.blackGames} (${summary.blackEntries} entries, ${summary.blackPositions} positions)")
        println("  longest game    : ${summary.deepestPlies} plies")
        println("  output size     : ${bytes.size} bytes")
        println("  skipped         : ${summary.skipped.size}")
        for (s in summary.skipped.take(10)) println("    $s")
        if (summary.skipped.size > 10) println("    ... and ${summary.skipped.size - 10} more")
    } catch (e: BookBuildException) {
        System.err.println("BOOK BUILD ERROR: ${e.message}")
        exitProcess(1)
    }
}

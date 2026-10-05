package com.checkmind.app.engine

/** Raw JNI link to the Stockfish UCI loop running inside this process (see src/main/cpp/engine_jni.cpp). */
internal object StockfishNative {
    init {
        System.loadLibrary("checkmind_stockfish")
    }

    /** Starts the engine thread. Safe to call more than once. */
    external fun start()

    /** Sends one UCI command line. */
    external fun write(line: String)

    /** Blocks until the engine prints a line; null when the engine has exited. */
    external fun readLine(): String?
}

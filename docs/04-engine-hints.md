# 04. Engine hints (Stockfish)

On your turn a **Hints** button asks Stockfish for the best moves in the current position and lists them.

## Behaviour
- The **Hints** button is shown when the game is ongoing and it is the chosen color's turn.
- Tapping it opens a panel below the action row. It shows `Thinking…` for about 2 seconds, then up to three moves, best first. Each row shows the move in SAN and the engine's score for the side to move: `Nf3 · +0.32`, or `M3` / `-M2` for forced mate.
- Tapping a row plays the move and closes the panel. Tapping **Hints** again, any move, undo, resign or new game also closes it and cancels a running search.
- If the engine cannot start, the panel says so. The rest of the app keeps working.

## Engine
- Stockfish 17.1, unmodified, vendored under `app/src/main/cpp/stockfish/` and built by CMake/NDK into `libcheckmind_stockfish.so` for `arm64-v8a` and `x86_64`.
- Full strength, no Elo limit. (Stockfish's own scale ends at `UCI_Elo` 3190; unrestricted play is stronger.) 2 s per request, 3 lines (`MultiPV 3`), up to 4 threads, 64 MB hash.
- ARM builds use baseline NEON (no dot-product instructions), x86_64 builds use SSE4.1, so it runs on any supported device.
- The two NNUE nets (`nn-1c0000000000.nnue` ~75 MB, `nn-37f18f62d772.nnue` ~3.5 MB) are not committed. Gradle task `downloadNnue` fetches them from `tests.stockfishchess.org` into `app/build/generated/nnue/assets/nnue/` on the first build and checks the SHA-256 prefix in the file name. They end up in the APK assets (about +78 MB) and are copied to `filesDir/nnue/` on first use, because the engine reads them from files.

## Plumbing
- `engine_jni.cpp` renames Stockfish's `main` to `stockfish_main`, rebinds `std::cin` / `std::cout` to in-memory buffers, and runs the UCI loop on its own thread. Kotlin sends command lines with `write` and reads output lines with a blocking `readLine`. No child process, no pipes.
- `StockfishEngine` (Kotlin, `app/.../engine/`) is a process-wide singleton behind the `HintEngine` interface. It sets up the engine on first use, sends `position startpos moves ...` so repetition history counts, runs `go movetime`, and parses `info ... multipv ... score ... pv ...` lines. A cancelled search is stopped and its `bestmove` drained, so the next one starts clean.
- `GameViewModel` takes a `HintEngine`, so tests use a fake.

## License
Stockfish is **GPLv3**. If the app is distributed, the app's source must be made available under the GPL as well, and `app/src/main/cpp/stockfish/Copying.txt` and `AUTHORS` ship with the source. Check this before any store release.

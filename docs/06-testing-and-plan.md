# 06. Testing and Implementation Plan

## Test plan

All JVM tests use JUnit 4. They run with `./gradlew test`.

### `chess-core`

**Perft** (the main correctness gate for move generation; node counts at depth 1, 2, 3, 4):

| Position | FEN | d1 | d2 | d3 | d4 |
|---|---|---|---|---|---|
| Start | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | 20 | 400 | 8902 | 197281 |
| Kiwipete | `r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1` | 48 | 2039 | 97862 | 4085603 |
| Position 3 | `8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1` | 14 | 191 | 2812 | 43238 |
| Position 4 | `r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1` | 6 | 264 | 9467 | 422333 |
| Position 5 | `rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8` | 44 | 1486 | 62379 | (skip) |

Position 5 at depth 4 can be skipped for speed. Kiwipete depth 4 takes a few seconds; keep it but mark it `@Test(timeout = ...)` generously.

**Other engine tests**
- Castling: both sides, both wings, refused when king is in check, passes through attacked square, or rook/king moved; castling rights lost after rook captured on its home square.
- En passant: available only immediately after the double push; refused if it would expose own king (the horizontal-pin case `8/8/8/8/k2Pp2Q/8/8/3K4 b - d3 0 1`).
- Promotion: four pieces, with and without capture.
- SAN: `Nbd2`, `R1e2`, `Qh4xe1` (full disambiguation), `exd6` (en passant), `e8=Q+`, `O-O`, `O-O-O#`, check `+`, mate `#`.
- Status: fool's mate gives `Checkmate(BLACK)`. Classic stalemate (`7k/5Q2/6K1/8/8/8/8/8 b - - 0 1`) gives `Stalemate`. Insufficient material cases (K v K, KN v K, KB v K, KB v KB same color; KB v KB opposite colors is **not** a draw). Threefold with an interleaved knight shuffle. 50-move at clock 100. Checkmate takes priority when the 100th half-move also mates.
- Game undo: play N moves, undo all, position equals start; undo after castling restores rights; repetition counts shrink after undo; undo after resignation returns `false`.

### `app` (JVM unit tests)
- `BoardGeometry`: round-trip `squareCenter` then `squareAt` for all 64 squares in both orientations. `a1` bottom-left (White), top-right (Black).
- `GameViewModel`:
  - Tap select / deselect / reselect, legal targets, capture targets.
  - Illegal drop leaves the board unchanged.
  - Promotion flow: chooser, chosen piece, cancel.
  - Resign: dialog, result, board locked, undo disabled.
  - New game resets state.

- `HintEngine` parsing: `info` lines with cp / mate / multipv / promotion; bounds, `info string` and `bestmove` ignored.
- `GameViewModel` hints use a fake engine: shown on my turn only, lines with scores, `Thinking…` state, row tap plays the move, closes on move / undo / toggle (late answers dropped), engine failure shown, illegal engine move not listed.

### Instrumented / Compose UI tests (`androidTest`, optional for first release)
- Home shows both buttons. Tapping navigates to Game and the title text matches.
- Hints on a device: button on my turn, `Thinking…` then 3 moves, row tap plays the move. First use after install copies the nets (a few seconds).
- Game screen: tap `e2` then `e4` plays the move (status changes to `Black to move`).

### Manual checklist before release
1. White and Black boards look correct, coordinates on the right squares.
2. Drag and tap both work, including over the board edge.
3. Play a full game to checkmate; to stalemate; resign; a threefold repetition.
4. Rotate the device (portrait lock should prevent it) and background the app (state kept).

## Implementation order

Each step ends with `./gradlew test` green.

1. **Project skeleton.** Gradle settings, version catalog, two modules (`chess-core`, `app`), empty app showing a text. `.gitignore` (build outputs).
2. **chess-core types and FEN.** Square, Color, Piece, Move, Position, `fromFen` / `toFen`.
3. **Move generation, `play`, check detection.** Pass the perft table.
4. **SAN.** Tests from the list above.
5. **Game and status.** Undo, draw rules, resign, repetition.
6. **Theme, Home screen, navigation.**
7. **BoardGeometry and BoardView.** Static board first, then pieces, then highlights and coordinates, then tap, then drag.
8. **GameViewModel and GameUiState.** Then wire GameScreen, action row, status line.
9. **Promotion dialog, resign / new game dialogs, game-over dialog.**
10. **Piece assets and credits.**
11. **Polish and manual checklist.** Then add Compose UI tests.

## Definition of done
- All acceptance criteria in 01 pass.
- `./gradlew test` passes. `./gradlew :app:assembleDebug` produces an installable APK.
- Perft table passes exactly.
- No known illegal move can be played and no legal move is refused.

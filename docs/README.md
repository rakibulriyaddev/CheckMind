# CheckMind: Design Docs

CheckMind is a two-screen Android chess app (Kotlin, Jetpack Compose). The home screen has **Play as White** and **Play as Black**. The game screen shows a chess.com-style board, with your color at the bottom. Both sides are played by a human on the same device. On your turn a **Hints** button lists the moves that led to a win in your own database of won games.

This folder is the complete spec. Implementation starts only after these docs are agreed.

## Document index

| File | Contents |
|---|---|
| [01-requirements.md](01-requirements.md) | Functional / non-functional requirements, non-goals, acceptance criteria |
| [02-architecture.md](02-architecture.md) | Modules, tech stack, package layout, state flow, Gradle wiring |
| [03-chess-core.md](03-chess-core.md) | Rules engine: types, move generation, SAN, game status, draw rules |
| [04-opening-book.md](04-opening-book.md) | PGN database, position-keyed table (v3), binary format, build task, hint query |
| [05-ui-spec.md](05-ui-spec.md) | Screens, board rendering, interactions, colors, assets |
| [06-testing-and-plan.md](06-testing-and-plan.md) | Test plan, implementation order, definition of done |

## Decisions locked (from the design Q&A)

| Topic | Decision |
|---|---|
| Opponent | Human on the same device. You move both sides. Board orientation is fixed to your chosen color. |
| Hint meaning | Moves from games **where my color won**, from the current move sequence. A move qualifies with at least 1 win. |
| Hint display | Text list under the board. Each row shows `SAN` and win count. Tapping a row **plays** the move. |
| Hint visibility | Button is shown **only on my turn and only while the game is still in the book** (see 04). |
| Database source | PGN exports from chess.com / lichess. About 100 games. Only won games (no draws). |
| Database storage | Source of truth is one **PGN file** in the repo. A Gradle task compiles it to a compact **binary asset**. The app loads it into an **in-memory position-keyed table**. |
| Table key | **Position hash** (placement, side, castling, en passant), so transpositions share hints. |
| Rules | chess.com standard: castling, en passant, promotion, check/checkmate/stalemate, draws (threefold, 50-move, insufficient material). |
| Controls | Undo (one ply), New game, Resign. |
| UI toolkit | Jetpack Compose, Material 3, two screens. |
| Piece graphics | v1 draws Unicode chess glyphs (no assets). Open-license vector set (Cburnett) is the planned upgrade, isolated behind `PieceRenderer`. |

### Resolved conflict
An earlier answer asked for a "No winning moves in database" message. The later answer chose **hide the button when out of book**. The later answer wins. No empty-hints message is shown, because the button does not exist in that state.

## Assumptions to confirm before coding
These were not explicitly answered. Defaults are used unless you object.

1. Application ID / package: `com.checkmind.app`. Engine package: `com.checkmind.chess`.
2. `minSdk 26`, `compileSdk`/`targetSdk` = latest stable at implementation time. JDK 17.
3. Portrait only.
4. No clocks or timers. No move list panel. No captured-pieces strip. No sound.
5. Threefold repetition and the 50-move rule end the game **automatically** (no claim button).
6. Process death loses the game in progress. The game lives in the ViewModel only (portrait lock means no rotation recreation).
7. Cburnett piece set license (GPL / BSD / GFDL tri-license on Wikimedia Commons) is acceptable for the later upgrade. Verify attribution rules before any public release.

## Implementation status
Implemented per these docs: `chess-core`, `book-builder` (with sample `data/games.pgn`), and the `app` (Home, Game, board, drag/tap, promotion, hints, undo/resign/new game).
Verified by `./gradlew test` (engine perft, SAN, status, book codec, PGN builder, view-model, geometry) and `./gradlew :app:assembleDebug`.
Not verified: on-device rendering and gestures (no emulator was available). Run the app once and check the manual list in 06.

Build and run:

```
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug
./gradlew test                   # all JVM tests
./gradlew :app:generateBook      # rebuild book.bin from book-builder/data/games.pgn
```

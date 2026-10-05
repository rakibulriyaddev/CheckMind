# CheckMind: Design Docs

CheckMind is a two-screen Android chess app (Kotlin, Jetpack Compose). The home screen has **Play as White** and **Play as Black**. The game screen shows a chess.com-style board, with your color at the bottom. Both sides are played by a human on the same device. On your turn a **Hints** button asks a bundled Stockfish for the best moves.

This folder is the complete spec. Implementation starts only after these docs are agreed.

## Document index

| File | Contents |
|---|---|
| [01-requirements.md](01-requirements.md) | Functional / non-functional requirements, non-goals, acceptance criteria |
| [02-architecture.md](02-architecture.md) | Modules, tech stack, package layout, state flow, Gradle wiring |
| [03-chess-core.md](03-chess-core.md) | Rules engine: types, move generation, SAN, game status, draw rules |
| [04-engine-hints.md](04-engine-hints.md) | Stockfish hints: behaviour, JNI bridge, NNUE nets, build, GPL license |
| [05-ui-spec.md](05-ui-spec.md) | Screens, board rendering, interactions, colors, assets |
| [06-testing-and-plan.md](06-testing-and-plan.md) | Test plan, implementation order, definition of done |

## Decisions locked (from the design Q&A)

| Topic | Decision |
|---|---|
| Opponent | Human on the same device. You move both sides. Board orientation is fixed to your chosen color. |
| Hints | Stockfish 17.1, full strength, 3 lines, about 2 s. Shown on my turn only. Tapping a row plays the move (see 04). |
| Rules | chess.com standard: castling, en passant, promotion, check/checkmate/stalemate, draws (threefold, 50-move, insufficient material). |
| Controls | Undo (one ply), New game, Resign. |
| UI toolkit | Jetpack Compose, Material 3, two screens. |
| Piece graphics | v1 draws Unicode chess glyphs (no assets). Open-license vector set (Cburnett) is the planned upgrade, isolated behind `PieceRenderer`. |

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
Implemented per these docs: `chess-core` and the `app` (Home, Game, board, drag/tap, promotion, undo/resign/new game, PGN export, Stockfish hints).
Verified by `./gradlew test` (engine perft, SAN, status, PGN, view-model with a fake engine, UCI parsing, geometry) and `./gradlew :app:assembleDebug`.
Not verified: on-device rendering and gestures (no emulator was available). Run the app once and check the manual list in 06.

Build and run:

```
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug
./gradlew test                   # all JVM tests
```

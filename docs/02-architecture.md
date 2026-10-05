# 02. Architecture

## Tech stack
- Kotlin (latest stable), Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`.
- Android Gradle Plugin latest stable, JDK 17, `minSdk 26`.
- Jetpack Compose + Material 3 (Compose BOM), Navigation Compose, `lifecycle-viewmodel-compose`, Kotlin coroutines.
- Tests: JUnit 4 everywhere. Compose UI tests (`ui-test-junit4`) for the two screens.
- No third-party chess library. The rules engine is written in-house (see 03). Reason: the logic stays fully unit-testable.

## Modules

```
CheckMind/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  docs/                        <- these docs
  chess-core/                  <- Kotlin/JVM library, no Android
  app/                         <- Android application
    src/main/cpp/              <- Stockfish 17.1 (vendored), CMake, JNI bridge
```

Dependency direction: `app -> chess-core`. `chess-core` depends on nothing.

## Package layout

```
chess-core/src/main/kotlin/com/checkmind/chess/
  Color.kt            enum Color { WHITE, BLACK } + opposite()
  Piece.kt            enum PieceType, data class Piece(color, type)
  Square.kt           Int helpers: file(), rank(), name(), parse("e4")
  Move.kt             packed Int move + helpers (from, to, promotion)
  Position.kt         immutable position, play(), legalMoves(), isCheck()
  MoveGen.kt          pseudo-legal + legality filter
  San.kt              toSan(position, move)
  Fen.kt              parse/format (used by tests and repetition key)
  GameStatus.kt       sealed result types
  Game.kt             history, undo, status, resign
  PgnImport.kt        parsePgnMoves(text): lenient PGN movetext -> validated moves

app/src/main/kotlin/com/checkmind/app/
  engine/HintEngine.kt        interface + UCI info parser
  engine/StockfishEngine.kt   process-wide engine over JNI
  engine/StockfishNative.kt   external funs
  MainActivity.kt     setContent { NavHost }
  ui/theme/Theme.kt, Color.kt
  ui/home/HomeScreen.kt
  ui/game/GameScreen.kt
  ui/game/GameViewModel.kt
  ui/game/GameUiState.kt
  ui/game/BoardView.kt        Canvas board + gestures
  ui/game/BoardGeometry.kt    pure math: touch <-> square, per orientation
  ui/game/HintPanel.kt
  ui/game/PromotionDialog.kt
  ui/game/PieceIcons.kt       (Color, PieceType) -> drawable resource id
app/src/main/res/drawable/ic_piece_wk.xml ... ic_piece_bp.xml   (12 vector drawables)
```

## Navigation

Navigation Compose, two routes:

| Route | Screen | Args |
|---|---|---|
| `home` | HomeScreen | none |
| `game/{color}?moves={moves}` | GameScreen | `color` = `white` or `black`; `moves` = optional comma-separated UCI moves already played (from Paste PGN, always `white`) |

`GameViewModel` reads `color` from `SavedStateHandle`. Each navigation to `game/{color}` creates a fresh ViewModel (new back-stack entry), so a new game always starts clean.

## State and data flow

```
 user gesture --> GameScreen --event--> GameViewModel --> Game (chess-core, immutable positions)
                      ^                      |
                      +------ GameUiState <--+   (StateFlow, derived after every event)
```

- `GameViewModel` owns a `Game` (history of positions and moves) and a `HintEngine`.
- After every event the ViewModel rebuilds `GameUiState` from the `Game`. The UI is a pure function of that state.

### GameUiState

```kotlin
data class GameUiState(
    val playerColor: Color,
    val board: List<Piece?>,          // 64 entries, index = Square (a1 = 0 ... h8 = 63)
    val sideToMove: Color,
    val selected: Int?,               // selected square
    val legalTargets: Set<Int>,       // targets of selected piece
    val captureTargets: Set<Int>,     // subset of legalTargets that capture
    val lastMove: Pair<Int, Int>?,    // from, to
    val checkSquare: Int?,            // king square when in check
    val statusText: String,
    val result: GameStatus,           // Ongoing or final
    val canUndo: Boolean,
    val hintAvailable: Boolean,
    val hintsOpen: Boolean,
    val hintsThinking: Boolean,
    val hintsFailed: Boolean,
    val hints: List<HintRow>,         // empty unless hintsOpen and the engine answered
    val pendingPromotion: PendingPromotion?,
    val confirm: Confirm?,            // Resign / NewGame / null
    val gameOverDialogVisible: Boolean,
)
data class HintRow(val move: Move, val san: String, val eval: String)
data class PendingPromotion(val from: Int, val to: Int, val color: Color)
enum class Confirm { RESIGN, NEW_GAME }
```

### Events (ViewModel public API)

```
onSquareTap(square)
onDrop(from, to)                // drag completed
onPromotionChosen(type) / onPromotionCancelled()
onUndo()
onHintsClick()                  // toggle panel, starts a search when opening
onHintRowClick(move)
onResignClick() / onNewGameClick()
onConfirm() / onDismissConfirm()
onGameOverDismiss()
```

## Threading
- Engine: Stockfish runs on its own native threads. `StockfishEngine.analyse` suspends on `Dispatchers.IO`, one search at a time, and the ViewModel cancels it when the position changes.
- Everything else is synchronous on the main thread inside the ViewModel (microseconds per call).

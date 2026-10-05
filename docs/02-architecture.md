# 02. Architecture

## Tech stack
- Kotlin (latest stable), Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`.
- Android Gradle Plugin latest stable, JDK 17, `minSdk 26`.
- Jetpack Compose + Material 3 (Compose BOM), Navigation Compose, `lifecycle-viewmodel-compose`, Kotlin coroutines.
- Tests: JUnit 4 everywhere. Compose UI tests (`ui-test-junit4`) for the two screens.
- No third-party chess library. The rules engine is written in-house (see 03). Reason: the same engine is reused by the book builder to validate PGN, and the logic stays fully unit-testable.

## Modules

```
CheckMind/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  docs/                        <- these docs
  chess-core/                  <- Kotlin/JVM library, no Android
  book-builder/                <- Kotlin/JVM app: PGN -> book.bin
    data/games.pgn             <- source of truth (committed)
  app/                         <- Android application
```

Dependency direction: `app -> chess-core`, `book-builder -> chess-core`. `chess-core` depends on nothing.

The binary format is defined once, in `chess-core` (`BookCodec`), and used by both the builder (write) and the app (read), so the two can never drift.

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
  book/
    BookNode.kt       in-memory trie
    BookCodec.kt      read/write binary format
    OpeningBook.kt    hints(color, moves)

book-builder/src/main/kotlin/com/checkmind/book/
  PgnParser.kt        tokenizer -> List<PgnGame>
  BookBuilder.kt      games -> tries
  BuildBook.kt        main(args): input.pgn output.bin

app/src/main/kotlin/com/checkmind/app/
  CheckMindApp.kt     Application: starts book loading
  MainActivity.kt     setContent { NavHost }
  data/BookRepository.kt   loads assets/book.bin once, exposes StateFlow<OpeningBook?>
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
| `game/{color}` | GameScreen | `color` = `white` or `black` |

`GameViewModel` reads `color` from `SavedStateHandle`. Each navigation to `game/{color}` creates a fresh ViewModel (new back-stack entry), so a new game always starts clean.

## State and data flow

```
assets/book.bin --(IO, once)--> BookRepository --StateFlow<OpeningBook?>--+
                                                                         v
 user gesture --> GameScreen --event--> GameViewModel --> Game (chess-core, immutable positions)
                      ^                      |
                      +------ GameUiState <--+   (StateFlow, derived after every event)
```

- `GameViewModel` owns a `Game` (history of positions and moves) and the current `OpeningBook?` snapshot.
- After every event the ViewModel rebuilds `GameUiState` from the `Game`. The UI is a pure function of that state.
- Hint availability is computed in the ViewModel: `status == Ongoing && sideToMove == playerColor && book.hints(playerColor, game.moves) != null`.
- If the book finishes loading while a game is on screen, the ViewModel collects `BookRepository.book` and recomputes state.

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
    val hints: List<HintRow>,         // empty unless hintsOpen
    val pendingPromotion: PendingPromotion?,
    val confirm: Confirm?,            // Resign / NewGame / null
    val gameOverDialogVisible: Boolean,
)
data class HintRow(val move: Int, val san: String, val wins: Int)
data class PendingPromotion(val from: Int, val to: Int, val color: Color)
enum class Confirm { RESIGN, NEW_GAME }
```

### Events (ViewModel public API)

```
onSquareTap(square)
onDrop(from, to)                // drag completed
onPromotionChosen(type) / onPromotionCancelled()
onUndo()
onHintsClick()                  // toggle panel
onHintRowClick(move)
onResignClick() / onNewGameClick()
onConfirm() / onDismissConfirm()
onGameOverDismiss()
```

## Threading
- Book load: `Dispatchers.IO` in `BookRepository`, started from `Application.onCreate`.
- Everything else is synchronous on the main thread inside the ViewModel (microseconds per call).

## Gradle wiring for the book (sketch)

`book-builder` is `kotlin("jvm")` with `application { mainClass = "com.checkmind.book.BuildBookKt" }`.

In `app/build.gradle.kts`:

```kotlin
val bookOutDir = layout.buildDirectory.dir("generated/book/assets")

val generateBook = tasks.register<JavaExec>("generateBook") {
    val builder = project(":book-builder")
    classpath = builder.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("com.checkmind.book.BuildBookKt")
    val pgn = rootProject.file("book-builder/data/games.pgn")
    val out = bookOutDir.map { it.file("book.bin") }
    inputs.file(pgn)
    outputs.file(out)
    args(pgn.absolutePath, out.get().asFile.absolutePath)
    dependsOn(":book-builder:classes")
}

android { sourceSets["main"].assets.srcDir(bookOutDir) }

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(generateBook)
}
```

Notes:
- The task is incremental: it re-runs only when `games.pgn` or builder code changes.
- If this wiring fights the AGP version, the cleaner alternative is `androidComponents.onVariants { it.sources.assets?.addGeneratedSourceDirectory(...) }` with a custom task class. Behavior stays identical.
- `.gitignore` must contain `book.bin` and `app/build/`.

## Error handling summary
- Builder: fail the build on a malformed or illegal-move game (reports game index and token). Non-decisive games are skipped with a warning (see 04).
- App: if `book.bin` is missing or corrupt, `BookRepository` emits an empty book. The app works, Hints never appear. The error is logged.

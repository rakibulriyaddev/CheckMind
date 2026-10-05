# 03. Chess Core (rules engine)

Module `chess-core`, package `com.checkmind.chess`. Pure Kotlin/JVM. Performance target is "obviously fast enough" (immutable positions, copy per move). No bitboards.

## Basic types

### Square
`Int` 0..63. `a1 = 0, b1 = 1, ... h1 = 7, a2 = 8, ... h8 = 63`.
`file = sq % 8` (0 = a), `rank = sq / 8` (0 = rank 1). Helpers: `squareName(sq)` gives `"e4"`, `parseSquare("e4")`.

### Color, Piece
```kotlin
enum class Color { WHITE, BLACK; val opposite: Color }
enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }
data class Piece(val color: Color, val type: PieceType)
```

### Move
Packed `Int` (value class `Move(val bits: Int)`):

```
bits 0..5   from
bits 6..11  to
bits 12..14 promotion: 0 none, 1 knight, 2 bishop, 3 rook, 4 queen
```

Castling is encoded as the **king moving two files** (`e1g1`, `e1c1`, `e8g8`, `e8c8`). En passant is a pawn moving diagonally to the empty en-passant square. No extra flag bits are needed, because `Position.play` recognizes both from context. The 15-bit value is also the book encoding (see 04), so the same move always has the same code.

## Position

Immutable.

```kotlin
class Position(
    val board: Array<Piece?>,        // 64, treat as read-only
    val sideToMove: Color,
    val castling: Int,               // bits: WK=1, WQ=2, BK=4, BQ=8
    val epSquare: Int,               // -1 or square a pawn can capture onto
    val halfmoveClock: Int,          // plies since capture or pawn move
    val fullmoveNumber: Int,
) {
    companion object { val START: Position }
    fun legalMoves(): List<Move>
    fun legalMovesFrom(sq: Int): List<Move>
    fun isLegal(move: Move): Boolean
    fun play(move: Move): Position           // requires a legal move
    fun isInCheck(color: Color = sideToMove): Boolean
    fun kingSquare(color: Color): Int
    fun repetitionKey(): String              // placement + side + castling + legal ep
    fun toFen(): String
}
fun Position.Companion.fromFen(fen: String): Position
```

### Move generation
1. Generate pseudo-legal moves per piece (sliders along rays, knights, king, pawns with pushes, double push from start rank, captures, promotions to N/B/R/Q, en passant, castling).
2. Filter: apply the move and reject if own king is attacked.
3. Castling extra rules: king and rook unmoved (castling bits), squares between empty, king not in check, king does not pass through or land on an attacked square.

### `play(move)` details
- Move piece. Handle capture. Remove the pawn on en passant. Move the rook on castling. Replace the pawn on promotion.
- Update castling rights: any move from or to `e1/e8/a1/h1/a8/h8` clears the matching bits (covers king moves, rook moves, and rook captured on its home square).
- Set `epSquare` only after a double pawn push. For the repetition key it counts only if an enemy pawn could legally capture there.
- `halfmoveClock = 0` on capture or pawn move, else +1. `fullmoveNumber` increments after Black moves.

## SAN

`fun Position.toSan(move: Move): String` (computed against the position **before** the move).

Rules:
1. Castling: `O-O` (king side), `O-O-O` (queen side). Letter O, not zero.
2. Pawn move: destination square (`e4`). Pawn capture: origin file + `x` + destination (`exd5`, also en passant). Promotion: append `=Q` / `=R` / `=B` / `=N` (`e8=Q`, `exd8=N`).
3. Piece move: letter (`N B R Q K`) + disambiguation + optional `x` + destination.
4. Disambiguation (only when another piece of the same type can legally move to the same square): add the origin **file** if it uniquely identifies; else the origin **rank**; else both.
5. Suffix `+` if the move gives check, `#` if checkmate (determine by playing the move and checking the resulting position's legal moves).

This is the display format for hints. The book itself stores moves as codes, not SAN.

## Game status

```kotlin
sealed interface GameStatus {
    object Ongoing : GameStatus
    data class Checkmate(val winner: Color) : GameStatus
    object Stalemate : GameStatus
    object DrawInsufficientMaterial : GameStatus
    object DrawThreefold : GameStatus
    object DrawFiftyMove : GameStatus
    data class Resigned(val winner: Color) : GameStatus
}
```

Evaluation order after each move (first match wins):
1. No legal moves: `Checkmate(winner = opposite of side to move)` if in check, else `Stalemate`.
2. Insufficient material.
3. Threefold repetition: current `repetitionKey` appears 3 or more times in the game's position history.
4. `halfmoveClock >= 100`: fifty-move draw.
5. Otherwise `Ongoing`.

### Insufficient material
Draw when remaining material is exactly one of:
- King vs king.
- King + one knight vs king.
- King + one bishop vs king.
- King + bishop vs king + bishop with both bishops on same-colored squares.

Any pawn, rook, or queen on the board means sufficient material.

### Repetition key
`"<piece placement>|<side to move>|<castling bits>|<ep square or ->"` where the ep square counts only if a legal en-passant capture exists. This matches the FIDE definition of "same position".

## Game

```kotlin
class Game(val start: Position = Position.START) {
    val position: Position              // current
    val moves: List<Move>               // played moves, in order
    val lastMove: Move?
    val status: GameStatus
    fun play(move: Move)                // throws if illegal or game over
    fun undo(): Boolean                 // pops one ply; false if none or resigned
    fun resign(color: Color)            // sets Resigned(winner = color.opposite)
    fun legalMoves(): List<Move>        // empty if game over
}
```

Internals: `positions: List<Position>` (index 0 = start) and `moves: List<Move>`. Undo pops both lists, so castling rights, en-passant square, halfmove clock, and repetition history come back exactly (they live in the stored position). `status` is derived from `positions` and `resigned` flag. `undo()` returns `false` after a resignation.

## Required unit-test coverage (details in 06)
Perft counts, SAN edge cases, each status type, undo round-trips.

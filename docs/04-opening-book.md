# 04. Opening Book (database, trie, hints)

## Concept

The "database" is a set of **won games**. Each game is a move sequence from the standard start position. For each color we build one trie:

- **White trie**: made from games White won.
- **Black trie**: made from games Black won.

A trie edge is a move. Each edge stores `wins`, the number of won games that pass through that edge. The trie is keyed by the **exact move sequence** from move 1 (both colors' moves). Transpositions are not matched.

Hint query for a player of color `C` at a given point of the game:
1. Walk the `C` trie from the root following the played moves, in order.
2. If any move is missing, the game is **out of book**: no hints, the button is hidden.
3. Otherwise the node reached has child edges. Each child is a move that appears after this exact sequence in at least one game `C` won. Because the trie holds only `C`'s wins, every child already counts as a winning move.
4. If the node has no children (a won game ended here), also treat as out of book.

Hints are asked only on `C`'s turn, so every child edge there is a move by the player.

## Why a PGN file (storage recommendation)

| Option | Verdict |
|---|---|
| **PGN file (source) + binary (shipped)** | Chosen. Your games already come as PGN from chess.com / lichess. Paste, commit, rebuild. Human-readable and diffable. |
| Markdown | Poor. No standard for moves, needs a custom parser. |
| YAML | Works but verbose, needs a library, and you would re-type data you already have as PGN. |
| SQLite | Overkill for about 100 games. Adds a dependency and does not give trie lookups anyway. |

The app never reads PGN. A Gradle task compiles it to a small binary so startup is instant and the app has no PGN parser.

## Source file

`book-builder/data/games.pgn`: plain multi-game PGN. Typical chess.com export:

```
[Event "Live Chess"]
[White "me"]
[Black "opponent"]
[Result "1-0"]
...

1. e4 {[%clk 0:09:58]} e5 {[%clk 0:09:57]} 2. Nf3 Nc6 3. Bb5 a6 ... 1-0
```

### Inclusion rules
| Case | Behavior |
|---|---|
| `1-0` (White won) | Added to White trie |
| `0-1` (Black won) | Added to Black trie |
| `1/2-1/2` or `*` | Skipped with a warning (you said only wins are stored, so this should be rare) |
| `[SetUp "1"]` or `[FEN ...]` tag | **Build error**. Only standard start positions are supported |
| `[Variant]` present and not `Standard` | Skipped with a warning (Chess960 etc.) |
| Illegal or unparsable move | **Build error** with game number (1-based) and the offending token |

The result is read from the movetext termination marker. If it is missing, the `[Result]` tag is used. If neither exists, the game is skipped with a warning. If both exist and disagree, **build error**.

## PGN parser (`PgnParser.kt`)

State machine over the whole text. Must handle:
- Tag pairs `[Key "Value"]`, including escaped quotes.
- `{ ... }` comments (chess.com clock annotations `{[%clk ...]}`), `; ...` line comments.
- `( ... )` variations: skip nested content entirely.
- `$n` NAG codes, `!`, `?`, `!!`, `??`, `!?`, `?!` suffixes: stripped.
- Move numbers `12.` and `12...`: skipped.
- Termination tokens: `1-0`, `0-1`, `1/2-1/2`, `*`.
- `0-0` / `0-0-0` normalized to `O-O` / `O-O-O`.
- Games are separated by the next tag section or end of file.

Output: `PgnGame(index, tags, sanMoves: List<String>, result: String?)`.

## SAN to move (builder only)

For each SAN token, the builder finds the single legal move in the current `Position` whose SAN (from `Position.toSan`, with `+ # ! ?` stripped from both sides before comparing) equals the token. Exactly one match is required. Zero matches means illegal move (build error). The result is the 15-bit move code.

This reuses the engine, so a bad PGN fails at build time and not in the app.

## Trie (in memory)

```kotlin
class BookNode(
    val moves: IntArray,          // move codes, sorted ascending
    val wins: IntArray,           // parallel to moves
    val children: Array<BookNode>,// parallel to moves
)
class OpeningBook(val white: BookNode, val black: BookNode, val whiteGames: Int, val blackGames: Int) {
    /** null = out of book. List is sorted: wins desc, then SAN asc (SAN sort done by caller). */
    fun hints(color: Color, moves: List<Move>): List<BookEdge>?
}
data class BookEdge(val move: Move, val wins: Int)
```

Lookup is a loop of binary searches. Cost is at most one search per ply. Fine for tens of plies per query.

The builder uses a mutable trie (`HashMap<Int, MutableEdge>`) while inserting games, then freezes to the array form.

### Insert
```
node = root(winnerColor)
for move in game.moves:
    edge = node.children.getOrCreate(move)
    edge.wins++
    node = edge.child
```
Whole games are inserted (all plies), not a depth cap. With 100 games the file stays tiny (a few KB).

## Binary format (`book.bin`, version 1)

All integers **big-endian**.

```
offset  size  field
0       4     magic "CMBK"
4       1     version = 1
5       2     whiteGames (u16)
7       2     blackGames (u16)
9       ...   white trie, pre-order
...     ...   black trie, pre-order
```

Trie node encoding, recursive:

```
node  := childCount (u8)   then childCount * edge
edge  := move (u16) wins (u16) node
```

- Max children per node is 218 legal moves, so `u8` is enough.
- Children are written sorted by move code ascending.
- `wins` must be at most 65535. Builder fails otherwise.
- A leaf is `childCount = 0`.
- Reader must reject: wrong magic, unknown version, truncated data, trailing bytes. A reader failure makes the app run with an empty book.
- Reader may be recursive (a game is at most a few hundred plies deep). Use an iterative reader if a safeguard is wanted (cap depth at 1024 and fail past it).

### Example
Games: `1.e4 e5` (White won), `1.e4 c5` (White won), `1.d4 d5` (Black won). White trie: root has one child `e2e4` with 2 wins, whose node has two children `e7e5` (1) and `c7c5` (1). Black trie: root has `d2d4` (1) then `d7d5` (1).

## Build task

CLI: `BuildBookKt.main(args)`: `args[0]` is input PGN, `args[1]` is output file.

Steps:
1. Parse PGN. Apply inclusion rules.
2. Convert SAN to move codes with the engine.
3. Insert into tries.
4. Serialize with `BookCodec`.
5. Print summary to stdout: games read, skipped (with reasons), white games, black games, node count per trie, deepest line in plies, output size in bytes.
6. Exit code non-zero on any build error. Gradle fails the build.

## Loading in the app

`BookRepository` (singleton created in `CheckMindApp`):
```kotlin
val book: StateFlow<OpeningBook?>   // null while loading
```
Loads `assets/book.bin` on `Dispatchers.IO` once. On failure logs and emits an empty `OpeningBook` (two empty root nodes) so the app keeps working.

## Hint rows in the UI

For each `BookEdge`: `san = position.toSan(move)`, `wins = edge.wins`. Label `"$san · $wins win(s)"` (singular for 1). Sorted by wins descending, then SAN ascending.

## Known limitations
- A single deviation by the opponent (including a different move order that reaches the same position) takes you out of book for the rest of the game. This is the cost of the pure sequence trie you chose.
- With about 100 games, depth beyond the first 6 to 10 plies will be sparse. Expect hints mostly in the opening.
- Undo can bring you back into book, because the lookup is recomputed from the move list each time.


## Update: bulk data, compact format v2 (current implementation)

This section supersedes the trie and binary-format details above where they differ.

### Input files
`book-builder/data/` may hold any number of `.pgn` files. The build reads them all.
- `games.pgn` and any other file: **strict**. A bad game fails the build.
- Files whose name starts with `bulk` (for example `bulk_chesscom.pgn`): **lenient**. A bad game is skipped and counted in the summary.

`book-builder/tools/fetch_chesscom.py` fills `bulk_chesscom.pgn` from the chess.com public API: rated rapid / blitz / daily games of leaderboard players and titled GMs, both players rated 2300 or more, decisive by checkmate or resignation, won within 40 moves (80 plies), at most 200 wins per colour per player. It is resumable (`bulk_chesscom.pgn.done` lists finished players). Run: `python tools/fetch_chesscom.py data/bulk_chesscom.pgn 65535`.

### In-memory trie
`BookTrie` is a breadth-first, flat-array trie: 5 bytes per edge (move u16, wins u16, child count u8) plus a tiny index. Children of a node are contiguous and sorted by move code, so lookup is a binary search per ply. `BookTrieBuilder` collects whole games, sorts them, and merges shared prefixes level by level.

### Binary format v2 (big-endian)
```
"CMBK" | version u8 = 2 | whiteGames u16 | blackGames u16 | white trie | black trie
trie := edgeCount u32 | rootCount u8 | moves u16[E] | wins u16[E] | childCount u8[E]
```
First child of edge `e` = `rootCount + sum(childCount[0 until e])`. The reader checks that child counts add up and that every block is sorted.

### Limits
- Header game counts and each edge's win count are 16-bit and **saturate at 65,535**. This is why 65,535 games per colour is the natural size.
- Memory: about 5 bytes per edge on the phone. Roughly 50 distinct edges per game, so 65,535 games per colour is a few million edges (tens of MB).
- Build time grows with the data. The build task runs only when a `.pgn` file changes.

## Update 2: position-keyed table, format v3 (current implementation)

This section supersedes the trie and the v2 binary format above. The PGN inputs, inclusion rules, and build task are unchanged.

### Why
The trie was keyed by the exact move sequence, so a different move order that reached the same position left the book. The table is keyed by **position**, so transpositions reach the same hints.

### Model
Per colour, one sorted table of `(position, move) -> wins`. A game won by colour `C` contributes one entry for each ply `C` played, keyed by the position **before** that move. The opponent's moves are not stored (hints are asked only on `C`'s turn). The hint query is: hash the current position, binary-search the table, return every move recorded for it (sorted by wins, then move code). No entry for the position means no hints. Entries that are illegal in the position (a hash collision) are dropped.

### Position key (`PositionKey`)
47-bit Zobrist-style hash of piece placement, side to move, castling rights, and the en passant file only when an own pawn is next to the target square. Move counters are ignored. The random table comes from a fixed SplitMix64 seed, so the builder and the app agree. A golden-value test (`keyIsStableAcrossRuns`) pins the start-position key: if it fails, bump `BookCodec.VERSION` and rebuild.

47 bits leave a false-hit chance of about 1e-8 per lookup at 1.5M positions. A false hit would only suggest an illegal move, which is filtered out.

### Binary format v3 (big-endian)
```
"CMBK" | version u8 = 3 | whiteGames u16 | blackGames u16 | white table | black table
table := entryCount u32 | packed u64[E] | wins u16[E]
packed = positionKey(47 bits) << 16 | moveCode(15 bits)
```
`packed` is strictly ascending, so entries for one position are adjacent. `wins` is at least 1 and saturates at 65,535. The reader rejects bad magic, other versions (v2 files included), truncation, trailing bytes, unsorted or negative entries, and zero wins. On any failure the app runs with an empty book.

### Size
10 bytes per entry. The current 131,101 games (65,558 white wins, 65,543 black wins) give about 3.0M entries and a 30 MB `book.bin`. Almost every position is unique to one game, so merging transpositions saves little at this size and matters more as data grows.

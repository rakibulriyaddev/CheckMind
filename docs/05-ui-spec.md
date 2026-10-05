# 05. UI Spec

Portrait only. Dark app background, chess.com-like green board.

## Theme tokens

| Token | Value | Use |
|---|---|---|
| `appBackground` | `#302E2B` | Screen background |
| `surface` | `#262522` | Cards, dialogs, panel |
| `onSurface` | `#FFFFFF` | Text |
| `primary` | `#81B64C` | Primary buttons |
| `boardLight` | `#EBECD0` | Light squares |
| `boardDark` | `#739552` | Dark squares |
| `highlightYellow` | `#F6F669` at 50% alpha | Last move squares, selected square (selected uses 80% alpha) |
| `moveDot` | black at 14% alpha | Legal-move dot / capture ring |
| `checkGlow` | red `#FF0000` radial gradient, 70% alpha at centre to transparent at edge | King in check |

Material 3 dark color scheme with these as overrides. Font: system default.

## Screen 1: Home

Centered column on `appBackground`:
1. App title **CheckMind**, large, bold, with the white king piece icon above it (reuse `ic_piece_wk`).
2. Button **Play as White**: full width up to 360 dp, height 64 dp, light button (`#FFFFFF` bg, dark text), leading white-king icon.
3. Button **Play as Black**: same size, dark button (`#1F1E1B` bg, white text, thin border), leading black-king icon.
4. 16 dp gap between buttons.

Test tags: `home_play_white`, `home_play_black`.

## Screen 2: Game

Top to bottom:

| Region | Content |
|---|---|
| Top app bar | Back arrow, title `Playing as White` / `Playing as Black` |
| Status line | `White to move`, `Black to move`, or `Check`. At game end: `Checkmate, White wins`, `Stalemate, draw`, `Draw by repetition`, `Draw by fifty-move rule`, `Draw: insufficient material`, `White resigned, Black wins` |
| Board | Square, full width, 16 dp side margins |
| Action row | `Undo`, `Hints` (only on my turn), `Resign`, `New game`. Equal-weight buttons in one row, icon above label |

The page scrolls vertically if the screen is too short. The board never scrolls and keeps a 1:1 aspect ratio.

Test tags: `board`, `btn_undo`, `btn_hints`, `btn_resign`, `btn_new_game`, `status_text`.

## Board rendering (`BoardView`)

One `Canvas`, size = width, height = width. Square size `s = width / 8`.

### Orientation (`BoardGeometry`, pure and unit-tested)
- White player: display row `r = 7 - rank`, display col `c = file`. So `a1` is bottom-left.
- Black player: display row `r = rank`, display col `c = 7 - file`. So `a1` is top-right, `h8` bottom-left.
- `squareAt(x, y, orientation, boardSize): Int?` and `squareCenter(sq, orientation, boardSize): Offset` are the only places that know the mapping.

### Draw order per frame
1. Squares: `(file + rank) % 2 == 0` is a dark square (so `a1` is dark), else light.
2. Last-move highlight on from and to squares.
3. Selected-square highlight.
4. Check glow on the king square.
5. Coordinates.
6. Legal-move indicators: dot (radius `0.16 * s`) on empty targets, ring (outer radius `0.46 * s`, stroke `0.09 * s`) on capture targets.
7. Pieces. The piece being dragged is drawn last, centered on the finger, 1.15x scale, and its origin square shows empty.

### Coordinates (chess.com style)
- Rank numbers `1..8` drawn small and bold in the **top-left corner** of the squares of the leftmost display column.
- File letters `a..h` drawn in the **bottom-right corner** of the squares of the bottom display row.
- Text color is the opposite square color (dark text on light squares, light text on dark squares).
- Under Black orientation the labels follow the squares (left column shows `8..1` top to bottom, bottom row shows `h..a` left to right).

## Interactions

### Tap
1. Tap own-side-to-move piece: select it. Legal targets appear.
2. Tap a legal target: play the move (promotion chooser first if needed).
3. Tap another piece of the side to move: switch selection.
4. Tap anywhere else: clear selection.
5. Taps are ignored while a dialog is open, the game is over, or a promotion is pending.

### Drag
- Touch down on a piece of the side to move: select it and start dragging after a touch-slop movement. The piece follows the finger.
- Release on a legal target: play the move. Otherwise the piece snaps back and the selection stays.
- A plain tap (no drag) behaves as Tap above.
- Implementation: one `pointerInput` with `detectDragGestures` plus `detectTapGestures`, mapping coordinates through `BoardGeometry`.

### Promotion
`PromotionDialog`: a small card centered over the board with four buttons (queen, rook, bishop, knight) in the mover's color. Tapping outside cancels the move. The pending move is stored in `GameUiState.pendingPromotion`.

## Hints UI

- `Hints` button is shown when `hintAvailable` is true (game ongoing, either side to move).
- Tap **Hints**: the panel opens below the action row. Tapping again closes it.
- Panel: surface card, title `Stockfish suggests`, then `Thinking…` with a spinner, then one row per move: SAN on the left, score on the right (`+0.32`, `M3`). Whole row is tappable and plays the move. Test tags: `hint_panel`, `hint_thinking`, `hint_failed`, `hint_row_<san>`.
- The panel closes automatically on any move, undo, resign, new game, or when the game ends.

## Dialogs

| Dialog | Trigger | Buttons |
|---|---|---|
| Resign confirm | `Resign` tap | `Resign` (destructive), `Cancel` |
| New game confirm | `New game` tap with at least 1 move played | `New game`, `Cancel` (no dialog if the board is at the start position) |
| Game over | Status becomes final | `New game`, `Home`, `View board` (closes the dialog, board stays locked, `Undo` still enabled except after resignation) |

Back button on Game goes to Home immediately (no confirmation in v1).

## Button states

| Button | Enabled when |
|---|---|
| Undo | `moves.isNotEmpty()` and not resigned |
| Hints | Present only when `hintAvailable` (game ongoing) |
| Resign | Game ongoing |
| New game | Always |

## Piece assets

**As implemented (v1):** pieces are drawn from the Unicode chess glyphs (filled shapes, white fill + dark outline for White, dark gray fill + black outline for Black) by `PieceRenderer.kt`. No image assets, no license questions. `PieceRenderer.draw` and `PieceRenderer.glyph` are the only places that know how a piece looks. Glyph shapes depend on the device font, so the look can vary slightly between phones.

**Planned upgrade:** swap in an open-license vector set (Cburnett from Wikimedia Commons, or similar) as 12 vector drawables `ic_piece_{w|b}{k|q|r|b|n|p}.xml`, converted with Android Studio **Vector Asset** import, and add `docs/CREDITS.md` (author, license, source URL). Only `PieceRenderer` changes. This needs the SVG files downloaded, which was not done without your approval.

## Accessibility
- Buttons have text labels, status line is a live region.
- The board `Canvas` exposes `testTag("board")` and a content description such as `Chess board, you play White`.
- Square-level semantics are not required in v1.

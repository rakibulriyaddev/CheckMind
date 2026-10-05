# 01. Requirements

## Functional requirements

### Navigation
- **FR-1** The app has exactly two screens: **Home** and **Game**.
- **FR-2** Home shows two buttons: **Play as White** and **Play as Black**.
- **FR-3** Tapping a button opens Game with that color. System back or the top-bar back arrow returns to Home. The game in progress is discarded.

### Board and orientation
- **FR-4** The board looks like chess.com (green theme, coordinates inside edge squares, highlighted last move, legal-move dots).
- **FR-5** Playing White: White pieces are at the bottom, `a1` is bottom-left. Playing Black: Black pieces are at the bottom, `a1` is top-right. Orientation never changes during a game.
- **FR-6** Game starts from the standard initial position with White to move.

### Playing moves
- **FR-7** Only legal moves can be played. Illegal drops snap the piece back.
- **FR-8** Move input: tap piece then tap target, **or** drag and drop.
- **FR-9** Only pieces of the side to move can be selected. Both colors are controlled by the same person.
- **FR-10** Selecting a piece shows its legal targets (dot on empty squares, ring on capture squares).
- **FR-11** Pawn promotion shows a chooser (Queen, Rook, Bishop, Knight) before the move is made.
- **FR-12** Castling, en passant, check, checkmate, stalemate work as in standard chess.
- **FR-13** Automatic draws: threefold repetition, 50-move rule (100 half-moves), insufficient material (K v K, K+minor v K, K+B v K+B same-colored bishops).
- **FR-14** A status line shows whose turn it is, `Check`, or the final result.

### Controls
- **FR-15** **Undo** takes back one ply (one half-move). Disabled when no moves exist or after resignation.
- **FR-16** **Resign** asks for confirmation. Resigning gives the win to the opponent of the chosen color, locks the board, and shows the result.
- **FR-17** **New game** restarts with the same color after the user confirms (no confirmation needed if no moves were played).
- **FR-18** At game end a dialog shows the result with **New game**, **Home**, and **View board** (dismiss).

### Hints
- **FR-19** The **Hints** button appears only when the game is ongoing and it is the chosen color's turn.
- **FR-20** Tapping it shows `Thinking…`, then up to three Stockfish moves, best first. Each row shows the move in SAN and its score for the side to move (`Nf3 · +0.32`, `M3`).
- **FR-21** Tapping a row plays that move and closes the panel. Any move, undo, resign, new game, or tapping **Hints** again also closes it and cancels the search.
- **FR-22** If the engine cannot start, the panel says so and the app keeps working.

## Non-functional requirements
- **NFR-1** App cold start to Home under 1 s on a mid-range device.
- **NFR-2** Move generation and legality checks never block the UI (they take microseconds at this scale, but they run in the ViewModel, not in composables).
- **NFR-3** The rules engine is a plain Kotlin/JVM module with no Android dependency, so they run in fast unit tests.
- **NFR-4** Minimum SDK 26.

## Non-goals (v1)
Online play, engine/AI opponent, clocks, move list panel, captured pieces, sound, haptics, themes, saving/resuming games, draw offers, Chess960, board flip button, tablets/landscape layouts, bot opponent.

## Acceptance criteria
1. Launch shows Home with two buttons. Each opens Game with the right board orientation.
2. Every legal move in a normal game can be played by tap and by drag. No illegal move can be played.
3. Fool's mate (`1.f3 e5 2.g4 Qh4#`) ends the game with a Black-win dialog.
4. A known stalemate position ends in a draw dialog. Threefold, 50-move, and insufficient-material draws trigger.
5. Castling (both sides), en passant, and all four promotions work. Castling through or out of check is refused.
6. Undo restores the exact previous position including castling rights and en-passant rights. Repetition history is correct after undo.
7. `./gradlew test` passes (engine perft, SAN, status, PGN, ViewModel tests).

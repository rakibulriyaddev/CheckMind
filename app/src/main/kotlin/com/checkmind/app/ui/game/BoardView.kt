package com.checkmind.app.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.checkmind.app.ui.theme.CheckMindColors
import com.checkmind.chess.Piece
import com.checkmind.chess.PieceType
import com.checkmind.chess.Squares
import kotlin.math.abs

@Composable
fun BoardView(
    state: GameUiState,
    onSquareTap: (Int) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val tap by rememberUpdatedState(onSquareTap)
    val dragStart by rememberUpdatedState(onDragStart)
    val drop by rememberUpdatedState(onDrop)

    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    // Slide animation for the piece(s) that just moved. Drag-drops are not animated: the piece is
    // already under the finger, so it just lands.
    val slide = remember { Animatable(1f) }
    var sliding by remember { mutableStateOf(emptyList<Slide>()) }
    var prevBoard by remember { mutableStateOf(state.board) }
    var lastDrop by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(state.board) {
        sliding = emptyList() // a previous slide may have been cancelled mid-flight
        val before = prevBoard
        prevBoard = state.board
        val dropped = lastDrop
        lastDrop = null
        val move = state.lastMove ?: return@LaunchedEffect
        if (dropped == move) return@LaunchedEffect
        val slides = slidesFor(before, state.board, move)
        if (slides.isEmpty()) return@LaunchedEffect
        sliding = slides
        slide.snapTo(0f)
        slide.animateTo(1f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
        sliding = emptyList()
    }

    val piecePaints = remember { PiecePaints() }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    }

    val description = "Chess board, you play ${if (state.playerColor == com.checkmind.chess.Color.WHITE) "White" else "Black"}"

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("board")
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastDrop = null
                    val st = currentState
                    val boardSize = size.width.toFloat()
                    val fromSq = BoardGeometry.squareAt(down.position.x, down.position.y, boardSize, st.playerColor)
                    val canDrag = st.boardEnabled && fromSq != null &&
                        st.board[fromSq]?.color == st.sideToMove
                    var dragging = false
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        if (!dragging && canDrag &&
                            (last - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
                            dragging = true
                            dragFrom = fromSq
                            dragStart(fromSq!!)
                        }
                        if (dragging) {
                            dragPos = last
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    if (dragging) {
                        dragFrom = null
                        val to = BoardGeometry.squareAt(last.x, last.y, boardSize, st.playerColor)
                        if (to != null) {
                            lastDrop = fromSq!! to to
                            drop(fromSq, to)
                        }
                    } else if (fromSq != null) {
                        tap(fromSq)
                    }
                }
            },
    ) {
        val st = state
        val boardSize = size.width
        val s = boardSize / 8f
        val player = st.playerColor
        val nc = drawContext.canvas.nativeCanvas

        // 1. squares
        for (row in 0..7) for (col in 0..7) {
            val sq = BoardGeometry.squareFromDisplay(row, col, player)
            drawRect(
                color = if (Squares.isDark(sq)) CheckMindColors.BoardDark else CheckMindColors.BoardLight,
                topLeft = Offset(col * s, row * s),
                size = Size(s, s),
            )
        }

        fun highlight(sq: Int, alpha: Float) {
            val (x, y) = BoardGeometry.squareOrigin(sq, player, boardSize)
            drawRect(
                color = CheckMindColors.HighlightYellow.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(s, s),
            )
        }

        // 2-3. last move and selection
        st.lastMove?.let {
            highlight(it.first, 0.5f)
            highlight(it.second, 0.5f)
        }
        st.selected?.let { highlight(it, 0.8f) }

        // 4. check glow
        st.checkSquare?.let { ks ->
            val (cx, cy) = BoardGeometry.squareCenter(ks, player, boardSize)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CheckMindColors.CheckGlow, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = s * 0.6f,
                ),
                radius = s * 0.6f,
                center = Offset(cx, cy),
            )
        }

        // 5. coordinates
        labelPaint.textSize = s * 0.2f
        for (i in 0..7) {
            // rank numbers: left column, top-left corner of the square
            val rankSq = BoardGeometry.squareFromDisplay(i, 0, player)
            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.color = coordinateColor(Squares.isDark(rankSq))
            nc.drawText("${Squares.rank(rankSq) + 1}", s * 0.06f, i * s + s * 0.24f, labelPaint)

            // file letters: bottom row, bottom-right corner of the square
            val fileSq = BoardGeometry.squareFromDisplay(7, i, player)
            labelPaint.textAlign = Paint.Align.RIGHT
            labelPaint.color = coordinateColor(Squares.isDark(fileSq))
            nc.drawText("${'a' + Squares.file(fileSq)}", (i + 1) * s - s * 0.06f, boardSize - s * 0.07f, labelPaint)
        }

        // 6. legal move indicators
        for (target in st.legalTargets) {
            val (cx, cy) = BoardGeometry.squareCenter(target, player, boardSize)
            if (target in st.captureTargets) {
                drawCircle(
                    color = CheckMindColors.MoveDot,
                    radius = s * 0.46f - s * 0.045f,
                    center = Offset(cx, cy),
                    style = Stroke(width = s * 0.09f),
                )
            } else {
                drawCircle(color = CheckMindColors.MoveDot, radius = s * 0.16f, center = Offset(cx, cy))
            }
        }

        // 7. pieces
        val moving = sliding
        for (sq in 0..63) {
            val piece = st.board[sq] ?: continue
            if (sq == dragFrom || moving.any { it.to == sq }) continue
            val (cx, cy) = BoardGeometry.squareCenter(sq, player, boardSize)
            PieceRenderer.draw(nc, piece, cx, cy, s, piecePaints)
        }
        for (m in moving) {
            val piece = st.board[m.to] ?: continue
            val (fx, fy) = BoardGeometry.squareCenter(m.from, player, boardSize)
            val (tx, ty) = BoardGeometry.squareCenter(m.to, player, boardSize)
            val p = slide.value
            PieceRenderer.draw(nc, piece, fx + (tx - fx) * p, fy + (ty - fy) * p, s, piecePaints)
        }
        val dragged = dragFrom
        if (dragged != null) {
            st.board[dragged]?.let {
                PieceRenderer.draw(nc, it, dragPos.x, dragPos.y, s, piecePaints, scale = 1.15f)
            }
        }
    }
}

private data class Slide(val from: Int, val to: Int)

/**
 * Pieces that visibly travelled between [before] and [after] for [move] (the king, plus the rook
 * when castling). Empty when [after] is not the board that [move] produced from [before], e.g. undo.
 */
private fun slidesFor(before: List<Piece?>, after: List<Piece?>, move: Pair<Int, Int>): List<Slide> {
    val (from, to) = move
    val mover = before[from] ?: return emptyList()
    if (after[to] == null || before[to] == after[to] || after[from] != null) return emptyList()
    val slides = mutableListOf(Slide(from, to))
    if (mover.type == PieceType.KING && abs(Squares.file(to) - Squares.file(from)) == 2) {
        val rank = Squares.rank(from)
        val kingside = Squares.file(to) > Squares.file(from)
        val rookFrom = rank * 8 + if (kingside) 7 else 0
        val rookTo = rank * 8 + if (kingside) 5 else 3
        if (before[rookFrom] != null && after[rookTo] != null) slides += Slide(rookFrom, rookTo)
    }
    return slides
}

private fun coordinateColor(onDarkSquare: Boolean): Int =
    (if (onDarkSquare) CheckMindColors.BoardLight else CheckMindColors.BoardDark).toArgb()

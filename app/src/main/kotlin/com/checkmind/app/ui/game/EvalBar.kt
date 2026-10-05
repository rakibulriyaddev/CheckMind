package com.checkmind.app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.checkmind.app.ui.theme.CheckMindColors
import com.checkmind.chess.Color as ChessColor

private val BarWhite = Color(0xFFF2F1EC)
private val BarBlack = Color(0xFF3A3835)

/**
 * Vertical bar beside the board that shows who is ahead: the white part grows with White's advantage,
 * and a line marks where the two sides meet. The player's own colour sits at the bottom, like the board.
 */
@Composable
fun EvalBar(
    evaluation: Evaluation?,
    playerColor: ChessColor,
    modifier: Modifier = Modifier,
) {
    val share by animateFloatAsState(
        targetValue = evaluation?.whiteShare ?: 0.5f,
        animationSpec = tween(durationMillis = 400),
        label = "evalShare",
    )
    val description = "Evaluation ${evaluation?.label ?: "unknown"}"
    Canvas(
        modifier = modifier
            .width(BarWidth)
            .fillMaxHeight()
            .testTag("eval_bar")
            .semantics { contentDescription = description },
    ) {
        val whiteAtBottom = playerColor == ChessColor.WHITE
        val whiteHeight = size.height * share
        val whiteTop = if (whiteAtBottom) size.height - whiteHeight else 0f
        drawRect(BarBlack, size = size)
        drawRect(BarWhite, topLeft = Offset(0f, whiteTop), size = Size(size.width, whiteHeight))

        // The indicator line sits on the boundary, nudged in so it stays visible at 0% and 100%.
        val stroke = 2.dp.toPx()
        val boundary = (if (whiteAtBottom) whiteTop else whiteHeight).coerceIn(stroke / 2, size.height - stroke / 2)
        drawLine(
            color = CheckMindColors.Primary,
            start = Offset(0f, boundary),
            end = Offset(size.width, boundary),
            strokeWidth = stroke,
        )
        // Even-game midpoint tick.
        drawLine(
            color = CheckMindColors.Outline,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width * 0.35f, size.height / 2),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

val BarWidth = 12.dp

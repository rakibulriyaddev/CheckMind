package com.checkmind.app.ui.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.checkmind.chess.Color
import com.checkmind.chess.Piece
import com.checkmind.chess.PieceType

/**
 * Draws pieces from the Unicode chess glyphs (filled shapes, with a contrasting outline).
 * This is the single place that knows how a piece looks: swapping in a vector-drawable set
 * later means changing only [draw] and [glyph].
 */
class PiecePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        strokeJoin = Paint.Join.ROUND
        typeface = Typeface.DEFAULT
    }
}

object PieceRenderer {
    // ︎ asks for the text (not emoji) presentation of the glyph.
    fun glyph(type: PieceType): String = when (type) {
        PieceType.KING -> "♚︎"
        PieceType.QUEEN -> "♛︎"
        PieceType.ROOK -> "♜︎"
        PieceType.BISHOP -> "♝︎"
        PieceType.KNIGHT -> "♞︎"
        PieceType.PAWN -> "♟︎"
    }

    /** Draws [piece] centred at ([cx], [cy]) inside a square of side [size]. */
    fun draw(canvas: Canvas, piece: Piece, cx: Float, cy: Float, size: Float, paints: PiecePaints, scale: Float = 1f) {
        val textSize = size * 0.9f * scale
        val white = piece.color == Color.WHITE
        paints.fill.textSize = textSize
        paints.stroke.textSize = textSize
        paints.fill.color = if (white) 0xFFFFFFFF.toInt() else 0xFF3B3B3B.toInt()
        paints.stroke.color = if (white) 0xFF1E1E1E.toInt() else 0xFF000000.toInt()
        paints.stroke.strokeWidth = textSize * 0.05f
        val fm = paints.fill.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        val g = glyph(piece.type)
        canvas.drawText(g, cx, baseline, paints.stroke)
        canvas.drawText(g, cx, baseline, paints.fill)
    }
}

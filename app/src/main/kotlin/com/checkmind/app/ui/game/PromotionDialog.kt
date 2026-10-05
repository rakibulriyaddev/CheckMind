package com.checkmind.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.checkmind.app.ui.theme.CheckMindColors
import com.checkmind.chess.Color
import com.checkmind.chess.Piece
import com.checkmind.chess.PieceType

private val CHOICES = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

@Composable
fun PromotionDialog(
    color: Color,
    onChoose: (PieceType) -> Unit,
    onCancel: () -> Unit,
) {
    val paints = remember { PiecePaints() }
    Dialog(onDismissRequest = onCancel) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Promote to", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in CHOICES) {
                    Canvas(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CheckMindColors.BoardDark)
                            .testTag("promote_${type.name.lowercase()}")
                            .semantics { contentDescription = "Promote to ${type.name.lowercase()}" }
                            .clickable(role = Role.Button) { onChoose(type) },
                    ) {
                        PieceRenderer.draw(
                            drawContext.canvas.nativeCanvas,
                            Piece(color, type),
                            size.width / 2f,
                            size.height / 2f,
                            size.width,
                            paints,
                        )
                    }
                }
            }
        }
    }
}

package com.checkmind.app.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.checkmind.app.ui.theme.CheckMindColors
import com.checkmind.chess.Move

@Composable
fun HintPanel(
    thinking: Boolean,
    failed: Boolean,
    hints: List<HintRow>,
    onHintClick: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hint_panel"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CheckMindColors.SurfaceRaised),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Stockfish suggests",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Score is for the side to move. Tap a move to play it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CheckMindColors.OnSurfaceMuted,
                )
            }
            when {
                thinking -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("hint_thinking"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
                }
                failed -> Text(
                    text = "The engine could not start.",
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("hint_failed"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CheckMindColors.OnSurfaceMuted,
                )
                else -> hints.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = CheckMindColors.SurfaceRaised)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hint_row_${row.san}")
                            .clickable(onClickLabel = "Play ${row.san}", role = Role.Button) { onHintClick(row.move) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.san,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = row.eval,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CheckMindColors.Primary,
                        )
                    }
                }
            }
        }
    }
}

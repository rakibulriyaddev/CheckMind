package com.checkmind.app.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
                    text = "Winning moves from your games",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tap a move to play it",
                    style = MaterialTheme.typography.bodySmall,
                    color = CheckMindColors.OnSurfaceMuted,
                )
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 264.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                hints.forEachIndexed { index, row ->
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
                            text = "${row.wins} ${if (row.wins == 1) "win" else "wins"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CheckMindColors.Primary,
                        )
                    }
                }
            }
        }
    }
}

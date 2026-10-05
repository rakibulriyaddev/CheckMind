package com.checkmind.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.checkmind.app.ui.theme.CheckMindColors
import com.checkmind.chess.Color as ChessColor

@Composable
fun HomeScreen(onPlay: (ChessColor) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CheckMindColors.AppBackground)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Decorative glyph; the app name below carries the meaning.
        Text(
            text = "♚︎",
            fontSize = 88.sp,
            color = CheckMindColors.Primary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = "CheckMind",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "Play moves that have won before",
            style = MaterialTheme.typography.bodyLarge,
            color = CheckMindColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
        )

        Text(
            text = "Choose your side",
            style = MaterialTheme.typography.labelLarge,
            color = CheckMindColors.OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Button(
            onClick = { onPlay(ChessColor.WHITE) },
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(64.dp)
                .testTag("home_play_white"),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1E1B)),
            border = BorderStroke(1.dp, CheckMindColors.Outline),
        ) {
            Text("Play as White", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onPlay(ChessColor.BLACK) },
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(64.dp)
                .testTag("home_play_black"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1E1B), contentColor = Color.White),
            border = BorderStroke(1.dp, CheckMindColors.Outline),
        ) {
            Text("Play as Black", style = MaterialTheme.typography.titleMedium)
        }
    }
}

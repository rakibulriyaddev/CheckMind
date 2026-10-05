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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.checkmind.chess.Move
import com.checkmind.chess.PgnParseException
import com.checkmind.chess.parsePgnMoves

@Composable
fun HomeScreen(onPlay: (ChessColor) -> Unit, onLoadPgn: (List<Move>) -> Unit) {
    var pasteOpen by remember { mutableStateOf(false) }

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
            text = "Play chess with Stockfish hints",
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

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = { pasteOpen = true },
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(56.dp)
                .testTag("home_paste_pgn"),
            border = BorderStroke(1.dp, CheckMindColors.Outline),
        ) {
            Text("Paste PGN", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (pasteOpen) {
        PastePgnDialog(
            onDismiss = { pasteOpen = false },
            onNext = { moves ->
                pasteOpen = false
                onLoadPgn(moves)
            },
        )
    }
}

@Composable
private fun PastePgnDialog(onDismiss: () -> Unit, onNext: (List<Move>) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste PGN") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pgn_input"),
                placeholder = { Text("1. e4 e5 2. Nf3 Nc6 …") },
                minLines = 6,
                maxLines = 10,
                isError = error != null,
                supportingText = error?.let { message ->
                    { Text(message, modifier = Modifier.testTag("pgn_error")) }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val moves = parsePgnMoves(text)
                        if (moves.isEmpty()) error = "No moves found in the text." else onNext(moves)
                    } catch (e: PgnParseException) {
                        error = e.message
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("pgn_next"),
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.checkmind.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkmind.app.ui.theme.CheckMindColors
import com.checkmind.chess.Color
import com.checkmind.chess.GameStatus

private val ActionPadding = PaddingValues(horizontal = 4.dp)
private val ActionHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = if (state.playerColor == Color.WHITE) "Playing as White" else "Playing as Black"

    // Leaving discards the game, so ask first once there is something to lose.
    val gameInProgress = state.canUndo && state.result is GameStatus.Ongoing
    var confirmLeave by remember { mutableStateOf(false) }
    val requestBack = { if (gameInProgress) confirmLeave = true else onBack() }
    BackHandler(enabled = gameInProgress) { confirmLeave = true }

    val hintsBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(state.hintsOpen) {
        if (state.hintsOpen) hintsBringIntoView.bringIntoView()
    }

    Scaffold(
        containerColor = CheckMindColors.AppBackground,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CheckMindColors.AppBackground,
                    titleContentColor = CheckMindColors.OnSurface,
                    navigationIconContentColor = CheckMindColors.OnSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusPill(state)

            BoardView(
                state = state,
                onSquareTap = viewModel::onSquareTap,
                onDragStart = viewModel::onDragStart,
                onDrop = viewModel::onDrop,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton("Undo", enabled = state.canUndo, tag = "btn_undo", onClick = viewModel::onUndo)
                if (state.hintAvailable) {
                    Button(
                        onClick = viewModel::onHintsClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(ActionHeight)
                            .testTag("btn_hints"),
                        contentPadding = ActionPadding,
                    ) { Text("Hints", maxLines = 1) }
                }
                ResignButton(
                    enabled = state.result is GameStatus.Ongoing,
                    onClick = viewModel::onResignClick,
                )
                ActionButton("New game", enabled = true, tag = "btn_new_game", onClick = viewModel::onNewGameClick)
            }

            if (state.hintsOpen && state.hints.isNotEmpty()) {
                HintPanel(
                    hints = state.hints,
                    onHintClick = viewModel::onHintRowClick,
                    modifier = Modifier.bringIntoViewRequester(hintsBringIntoView),
                )
            }
        }
    }

    state.pendingPromotion?.let {
        PromotionDialog(
            color = it.color,
            onChoose = viewModel::onPromotionChosen,
            onCancel = viewModel::onPromotionCancelled,
        )
    }

    when (state.confirm) {
        Confirm.RESIGN -> ConfirmDialog(
            title = "Resign?",
            text = "You will lose this game.",
            confirmLabel = "Resign",
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDismissConfirm,
        )
        Confirm.NEW_GAME -> ConfirmDialog(
            title = "Start a new game?",
            text = "The current game will be discarded.",
            confirmLabel = "New game",
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDismissConfirm,
        )
        null -> {}
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave game?",
            text = "The current game will be discarded.",
            confirmLabel = "Leave",
            onConfirm = {
                confirmLeave = false
                onBack()
            },
            onDismiss = { confirmLeave = false },
        )
    }

    if (state.gameOverDialogVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onGameOverDismiss,
            title = { Text(gameOverTitle(state.result)) },
            text = { Text(gameOverText(state.result, state.statusText)) },
            confirmButton = {
                Button(onClick = viewModel::onNewGameConfirmedFromDialog) { Text("New game") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onBack) { Text("Home") }
                    TextButton(onClick = viewModel::onGameOverDismiss) { Text("View board") }
                }
            },
        )
    }
}

/** Who is to move (or that the game ended) at a glance, with check called out in red. */
@Composable
private fun StatusPill(state: GameUiState) {
    val ongoing = state.result is GameStatus.Ongoing
    val inCheck = ongoing && state.checkSquare != null
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        border = BorderStroke(1.dp, CheckMindColors.SurfaceRaised),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ongoing) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (state.sideToMove == Color.WHITE) UiColor.White else UiColor(0xFF1A1A1A))
                        .border(1.dp, CheckMindColors.Outline, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = state.statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (ongoing) FontWeight.Medium else FontWeight.Bold,
                color = if (inCheck) CheckMindColors.Danger else CheckMindColors.OnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag("status_text")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(ActionHeight)
            .testTag(tag),
        contentPadding = ActionPadding,
    ) { Text(label, maxLines = 1) }
}

/** Destructive action: outlined in red so it is not mistaken for a neighbouring button. */
@Composable
private fun RowScope.ResignButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(ActionHeight)
            .testTag("btn_resign"),
        contentPadding = ActionPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CheckMindColors.Danger),
        border = BorderStroke(
            1.dp,
            if (enabled) CheckMindColors.Danger.copy(alpha = 0.7f) else CheckMindColors.Outline.copy(alpha = 0.4f),
        ),
    ) { Text("Resign", maxLines = 1) }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = CheckMindColors.Danger),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun gameOverTitle(result: GameStatus): String = when (result) {
    is GameStatus.Checkmate -> "Checkmate"
    is GameStatus.Resigned -> "Resignation"
    else -> "Draw"
}

private fun gameOverText(result: GameStatus, statusText: String): String = when (result) {
    is GameStatus.Checkmate -> "${if (result.winner == Color.WHITE) "White" else "Black"} wins"
    else -> statusText
}

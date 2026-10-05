package com.checkmind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.checkmind.app.engine.StockfishEngine
import com.checkmind.app.ui.game.GameScreen
import com.checkmind.app.ui.game.GameViewModel
import com.checkmind.app.ui.home.HomeScreen
import com.checkmind.app.ui.theme.CheckMindTheme
import com.checkmind.chess.Color
import com.checkmind.chess.Move

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark; force light status/nav icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            CheckMindTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onPlay = { color -> nav.navigate("game/${color.name.lowercase()}") },
                            // White is always at the bottom for a pasted game. The moves travel as UCI, comma separated.
                            onLoadPgn = { moves -> nav.navigate("game/white?moves=" + moves.joinToString(",") { it.uci() }) },
                        )
                    }
                    composable(
                        route = "game/{color}?moves={moves}",
                        arguments = listOf(
                            navArgument("color") { type = NavType.StringType },
                            navArgument("moves") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        val color = if (entry.arguments?.getString("color") == "black") Color.BLACK else Color.WHITE
                        val initialMoves = entry.arguments?.getString("moves").orEmpty()
                            .split(',').filter { it.isNotEmpty() }.map { Move.fromUci(it) }
                        val vm: GameViewModel = viewModel(
                            factory = GameViewModel.factory(color, StockfishEngine.get(applicationContext), initialMoves),
                        )
                        GameScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

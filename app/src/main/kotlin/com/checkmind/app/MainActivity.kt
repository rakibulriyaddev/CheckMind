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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light; force dark status/nav icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            CheckMindTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(onPlay = { color -> nav.navigate("game/${color.name.lowercase()}") })
                    }
                    composable(
                        route = "game/{color}",
                        arguments = listOf(navArgument("color") { type = NavType.StringType }),
                    ) { entry ->
                        val color = if (entry.arguments?.getString("color") == "black") Color.BLACK else Color.WHITE
                        val vm: GameViewModel = viewModel(
                            factory = GameViewModel.factory(color, StockfishEngine.get(applicationContext)),
                        )
                        GameScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

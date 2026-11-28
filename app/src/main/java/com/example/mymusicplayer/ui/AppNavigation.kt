package com.example.mymusicplayer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mymusicplayer.PlayerViewModel

@Composable
fun AppNavigation(viewModel: PlayerViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onMiniPlayerClick = { navController.navigate("player") }
            )
        }
        composable("player") {
            PlayerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

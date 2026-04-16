package com.puzzle.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.puzzle.android.ui.screens.PuzzleScreen
import com.puzzle.android.ui.screens.SetupScreen
import com.puzzle.android.ui.theme.PuzzleAndroidTheme
import com.puzzle.android.viewmodel.PuzzleViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuzzleAndroidTheme {
                val navController = rememberNavController()
                val vm: PuzzleViewModel = viewModel()

                NavHost(navController = navController, startDestination = "setup") {
                    composable("setup") {
                        SetupScreen(navController = navController, vm = vm)
                    }
                    composable("puzzle") {
                        PuzzleScreen(navController = navController, vm = vm)
                    }
                }
            }
        }
    }
}

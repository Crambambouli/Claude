package com.puzzle.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.puzzle.android.ui.screens.StartScreen
import com.puzzle.android.ui.theme.PuzzleAndroidTheme
import com.puzzle.android.viewmodel.MainViewModel
import com.puzzle.android.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as PuzzleApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuzzleAndroidTheme {
                StartScreen(viewModel = viewModel)
            }
        }
    }
}

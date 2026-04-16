package com.puzzle.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.puzzle.android.ui.screens.PuzzleScreen
import com.puzzle.android.ui.theme.PuzzleAndroidTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuzzleAndroidTheme {
                PuzzleScreen()
            }
        }
    }
}

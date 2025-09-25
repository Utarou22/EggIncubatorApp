package com.falsespring.eggincubatorapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// No direct import of NavHost or Scaffold needed here anymore if they are in AppNavigation
import com.falsespring.eggincubatorapp.ui.theme.EggIncubatorAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EggIncubatorAppTheme {
                AppNavigation()
            }
        }
    }
}
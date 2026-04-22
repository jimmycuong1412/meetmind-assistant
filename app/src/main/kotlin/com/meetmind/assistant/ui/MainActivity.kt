// Entry point activity — hosts the Compose nav graph
// spec 009 — T024: @AndroidEntryPoint; application param removed from AppNavGraph call
package com.meetmind.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.meetmind.assistant.ui.navigation.AppNavGraph
import com.meetmind.assistant.ui.theme.MeetMindTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeetMindTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }
}

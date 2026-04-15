// Entry point activity — hosts the Compose nav graph
package com.meetmind.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.meetmind.assistant.MeetMindApplication
import com.meetmind.assistant.ui.navigation.AppNavGraph
import com.meetmind.assistant.ui.theme.MeetMindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MeetMindApplication
        setContent {
            MeetMindTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController, application = app)
            }
        }
    }
}

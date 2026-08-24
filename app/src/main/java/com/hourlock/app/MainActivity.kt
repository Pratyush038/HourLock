package com.hourlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hourlock.app.ui.theme.HourLockTheme

/**
 * MainActivity
 * ─────────────
 * Single-Activity host. Navigation is handled via Compose Navigation between
 * two destinations: "home" and "settings".
 *
 * We use a single-activity architecture intentionally:
 *  - Simpler process lifecycle management.
 *  - Compose Navigation handles back-stack automatically.
 *  - BlockedActivity is kept separate because it needs to overlay OTHER apps
 *    with FLAG_ACTIVITY_NEW_TASK, which requires its own Activity context.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HourLockTheme {
                HourLockNavHost()
            }
        }
    }
}

@Composable
fun HourLockNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

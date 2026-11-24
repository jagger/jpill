package com.jagger.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jagger.timer.add_pill.ui.PillFormScreen
import com.jagger.timer.archive.ui.ArchivedScreen
import com.jagger.timer.history.ui.HistoryScreen
import com.jagger.timer.home.ui.HomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        checkNotificationPermission()

        setContent {
            TimerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onAddPillClick = { navController.navigate("pill_form") },
                            onEditPillClick = { pillId -> navController.navigate("pill_form?pillId=$pillId") },
                            onHistoryClick = { navController.navigate("history") },
                            onArchivedClick = { navController.navigate("archived") }
                        )
                    }
                    composable("history") {
                        HistoryScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable("archived") {
                        ArchivedScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "pill_form?pillId={pillId}",
                        arguments = listOf(
                            navArgument("pillId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        )
                    ) {
                        PillFormScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun TimerTheme(content: @Composable () -> Unit) {
    // Basic theme wrapper
    androidx.compose.material3.MaterialTheme(
        content = content
    )
}


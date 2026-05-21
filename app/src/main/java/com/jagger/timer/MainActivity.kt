package com.jagger.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jagger.timer.add_pill.ui.PillFormScreen
import com.jagger.timer.history.ui.HistoryScreen
import com.jagger.timer.home.ui.HomeScreen
import com.jagger.timer.timers.ui.AddTimerScreen
import com.jagger.timer.timers.ui.TimersScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkNotificationPermission()

        setContent {
            TimerTheme {
                MainApp()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf("medications", "timers")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Medication, contentDescription = null) },
                        label = { Text("Meds") },
                        selected = currentDestination?.hierarchy?.any { it.route == "medications" } == true,
                        onClick = {
                            navController.navigate("medications") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        label = { Text("Timers") },
                        selected = currentDestination?.hierarchy?.any { it.route == "timers" } == true,
                        onClick = {
                            navController.navigate("timers") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "medications", modifier = Modifier.padding(innerPadding)) {
            composable("medications") {
                HomeScreen(
                    onAddPillClick = { navController.navigate("pill_form") },
                    onEditPillClick = { pillId -> navController.navigate("pill_form?pillId=$pillId") },
                    onHistoryClick = { navController.navigate("history") }
                )
            }
            composable("history") {
                HistoryScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = "pill_form?pillId={pillId}",
                arguments = listOf(navArgument("pillId") { type = NavType.LongType; defaultValue = -1L })
            ) {
                PillFormScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("timers") {
                TimersScreen(onAddTimerClick = { navController.navigate("add_timer") })
            }
            composable("add_timer") {
                AddTimerScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun TimerTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(content = content)
}

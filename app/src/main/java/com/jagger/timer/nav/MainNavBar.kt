package com.jagger.timer.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

object Routes {
    const val HOME = "home"
    const val TIMERS = "timers"
    const val HISTORY = "history"
}

/** Bottom navigation shared by the three top-level screens: Medicines | Timers | History. */
@Composable
fun MainNavBar(current: String, onNavigate: (String) -> Unit) {
    NavigationBar {
        listOf(
            Triple(Routes.HOME, "Medicines", Icons.Default.Medication),
            Triple(Routes.TIMERS, "Timers", Icons.Default.Timer),
            Triple(Routes.HISTORY, "History", Icons.Default.History)
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { if (current != route) onNavigate(route) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) }
            )
        }
    }
}

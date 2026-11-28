// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.jagger.timer.core.settings.AppSettings
import com.jagger.timer.core.util.AppLockPolicy
import com.jagger.timer.settings.ui.AboutScreen
import com.jagger.timer.settings.ui.AppearanceScreen
import com.jagger.timer.settings.ui.LicenseScreen
import com.jagger.timer.settings.ui.PrivacyScreen
import com.jagger.timer.settings.ui.SettingsHomeScreen
import javax.inject.Inject
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
import com.jagger.timer.nav.Routes
import com.jagger.timer.timers.ui.TimersScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var scheduleManager: com.jagger.timer.core.util.ScheduleManager

    /** Starts locked, so a cold start always asks (when the lock is on). */
    private var locked by mutableStateOf(true)
    private var backgroundedAt: Long? = null
    private var prompting = false

    /** Set by a widget tap: open the early-dose confirmation for this as-needed medicine. */
    private var confirmPillId by mutableStateOf<Long?>(null)

    /** Set by an NFC scan of a tag this phone does not know: open Add Medicine with the tag (and its name) filled in. */
    private var prefill by mutableStateOf<Pair<String, String?>?>(null)

    /** Release notes the user hasn't seen yet (after an update); cleared when they tap "Got it". */
    private var whatsNew by mutableStateOf<List<com.jagger.timer.whatsnew.ReleaseNotes>>(emptyList())

    companion object {
        const val EXTRA_CONFIRM_PILL_ID = "confirm_pill_id"
        const val EXTRA_PREFILL_TAG = "prefill_tag"
        const val EXTRA_PREFILL_NAME = "prefill_name"
    }


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readConfirmRequest(intent)
        // Don't let the recent-apps card show medicines while the lock is on.
        setRecentsScreenshotEnabled(!appSettings.lockEnabled)

        checkNotificationPermission()
        loadWhatsNew()
        com.jagger.timer.reminderlog.ReminderLog.log("APP OPEN", "version=${currentVersionCode()} " + com.jagger.timer.core.receiver.ReminderNotifier.deliveryState(this))
        lifecycleScope.launch { scheduleManager.rearmMissing("app-open") }

        setContent {
            val themeMode by appSettings.themeModeFlow.collectAsState()
            com.jagger.timer.ui.theme.JPillTheme(
                darkTheme = when (themeMode) {
                    com.jagger.timer.core.settings.ThemeMode.LIGHT -> false
                    com.jagger.timer.core.settings.ThemeMode.DARK -> true
                    com.jagger.timer.core.settings.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                val navController = rememberNavController()
                androidx.compose.runtime.LaunchedEffect(prefill, locked) {
                    val p = prefill
                    if (p != null && !locked) {
                        prefill = null
                        // MainActivity is exported, so these extras may come from any app: only a well-formed tag id is used,
                        // and both values are encoded so they can't add route arguments of their own.
                        if (com.jagger.timer.nfc.NfcTagUri.isValidId(p.first)) {
                            navController.navigate("pill_form?pillId=-1&tag=${android.net.Uri.encode(p.first)}&name=${android.net.Uri.encode(p.second.orEmpty().take(com.jagger.timer.nfc.NfcPayload.MAX_LABEL))}")
                        }
                    }
                }
                val goTop: (String) -> Unit = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                if (!(locked && appSettings.lockEnabled) && whatsNew.isNotEmpty()) {
                    com.jagger.timer.whatsnew.WhatsNewDialog(whatsNew, onDismiss = {
                        appSettings.lastSeenVersionCode = currentVersionCode()
                        whatsNew = emptyList()
                    })
                }
                if (locked && appSettings.lockEnabled) {
                    LockScreen(onUnlock = { promptUnlock() })
                } else NavHost(
                    navController = navController,
                    startDestination = if (intent?.getStringExtra("route") == Routes.TIMERS) Routes.TIMERS else Routes.HOME
                ) {
                    composable(Routes.TIMERS) { TimersScreen(onNavigate = goTop) }
                    composable("home") {
                        HomeScreen(
                            onAddPillClick = { navController.navigate("pill_form") },
                            onEditPillClick = { pillId -> navController.navigate("pill_form?pillId=$pillId") },
                            onNavigate = goTop,
                            onArchivedClick = { navController.navigate("archived") },
                            onSettingsClick = { navController.navigate("settings") },
                            onReliabilityClick = { navController.navigate("settings/reliability") },
                            confirmPillId = confirmPillId,
                            onConfirmHandled = { confirmPillId = null }
                        )
                    }
                    composable("history") {
                        HistoryScreen(onNavigate = goTop)
                    }
                    composable("settings") {
                        SettingsHomeScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onAppearance = { navController.navigate("settings/appearance") },
                            onPrivacy = { navController.navigate("settings/privacy") },
                            onReliability = { navController.navigate("settings/reliability") },
                            onDeveloper = { navController.navigate("settings/developer") },
                            onAbout = { navController.navigate("settings/about") }
                        )
                    }
                    composable("settings/appearance") {
                        AppearanceScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable("settings/developer") {
                        com.jagger.timer.settings.ui.DeveloperToolsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onReminderLog = { navController.navigate("settings/reminderlog") },
                            onBeta = { navController.navigate("settings/beta") }
                        )
                    }
                    composable("settings/reminderlog") {
                        com.jagger.timer.reminderlog.ReminderLogScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable("settings/reliability") {
                        com.jagger.timer.reliability.ReliabilityScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable("settings/beta") {
                        com.jagger.timer.settings.ui.BetaScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable("settings/about") {
                        AboutScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onLicense = { navController.navigate("settings/about/license") }
                        )
                    }
                    composable("settings/about/license") {
                        LicenseScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable("settings/privacy") {
                        PrivacyScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onDataErased = { navController.popBackStack(Routes.HOME, inclusive = false) }
                        )
                    }
                    composable("archived") {
                        ArchivedScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "pill_form?pillId={pillId}&tag={tag}&name={name}",
                        arguments = listOf(
                            navArgument("pillId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }
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

    private fun currentVersionCode(): Int = packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()

    /** After an update, queue the changelog entries newer than the version last seen (nothing on a fresh install). */
    private fun loadWhatsNew() {
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            val current = info.longVersionCode.toInt()
            val fresh = info.lastUpdateTime - info.firstInstallTime < 10_000L
            val releases = com.jagger.timer.whatsnew.loadBundledReleases(this)
            val pending = com.jagger.timer.whatsnew.WhatsNew.pending(releases, appSettings.lastSeenVersionCode, current, fresh)
            if (pending.isEmpty()) appSettings.lastSeenVersionCode = current else whatsNew = pending
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readConfirmRequest(intent)
    }

    private fun readConfirmRequest(intent: android.content.Intent?) {
        val id = intent?.getLongExtra(EXTRA_CONFIRM_PILL_ID, -1L) ?: -1L
        if (id != -1L) confirmPillId = id
        intent?.getStringExtra(EXTRA_PREFILL_TAG)?.let { prefill = it to intent.getStringExtra(EXTRA_PREFILL_NAME) }
    }

    override fun onStart() {
        super.onStart()
        setRecentsScreenshotEnabled(!appSettings.lockEnabled)
        if (!appSettings.lockEnabled) { locked = false; return }
        if (AppLockPolicy.shouldLock(true, backgroundedAt, System.currentTimeMillis(), appSettings.lockTimeoutSeconds)) {
            locked = true
            promptUnlock()
        } else {
            locked = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (!locked) backgroundedAt = System.currentTimeMillis()
    }

    private fun promptUnlock() {
        if (prompting) return
        val allowed = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        when (BiometricManager.from(this).canAuthenticate(allowed)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {}
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // Screen lock was removed since the lock was turned on: don't lock the user out of their medicines.
                appSettings.lockEnabled = false
                locked = false
                return
            }
            // Any other state (temporarily unavailable, security update pending, unknown) stays locked: fail closed.
            else -> return
        }
        prompting = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false; locked = false
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock jPill")
                .setAllowedAuthenticators(allowed)
                .build()
        )
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
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("jPill is locked", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) { Text("Unlock") }
        }
    }
}

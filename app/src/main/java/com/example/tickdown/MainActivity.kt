package com.example.tickdown

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.tickdown.data.preferences.ThemeMode
import com.example.tickdown.system.scheduler.ReminderScheduler
import com.example.tickdown.theme.TickdownTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_VIEW_EVENT = "com.example.tickdown.ACTION_VIEW_EVENT"
        const val ACTION_CREATE_EVENT = "com.example.tickdown.ACTION_CREATE_EVENT"
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    private val pendingDestination = mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()

        val app = application as TickdownApplication

        setContent {
            val themeMode by app.preferencesRepository.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            var showExactAlarmDialog by remember { mutableStateOf(false) }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                // After notification permission handled, verify exact alarm permission
                if (!ReminderScheduler.canScheduleExactAlarms(this)) {
                    showExactAlarmDialog = true
                }
            }

            // Request necessary permissions at app launch
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!ReminderScheduler.canScheduleExactAlarms(this@MainActivity)) {
                        showExactAlarmDialog = true
                    }
                } else if (!ReminderScheduler.canScheduleExactAlarms(this@MainActivity)) {
                    showExactAlarmDialog = true
                }
            }

            TickdownTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        pendingDestination = pendingDestination.value,
                        onClearPending = { pendingDestination.value = null }
                    )

                    if (showExactAlarmDialog) {
                        AlertDialog(
                            onDismissRequest = { showExactAlarmDialog = false },
                            title = { Text("Exact Alarms & Alerts") },
                            text = {
                                Text("To ensure your countdown reminders and deadline alerts ring precisely on time without battery optimization delays, please enable exact alarm permission.")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showExactAlarmDialog = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:$packageName")
                                            )
                                            startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:$packageName")
                                            )
                                            startActivity(intent)
                                        }
                                    }
                                }) {
                                    Text("GRANT IN SETTINGS")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExactAlarmDialog = false }) {
                                    Text("LATER")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_VIEW_EVENT -> {
                val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                if (eventId > 0) {
                    pendingDestination.value = EventDetailNavKey(eventId)
                }
            }
            ACTION_CREATE_EVENT -> {
                pendingDestination.value = CreateEditNavKey(0L)
            }
        }
    }
}

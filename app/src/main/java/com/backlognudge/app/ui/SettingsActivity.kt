package com.backlognudge.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.backlognudge.app.detection.ForegroundWatcherService
import com.backlognudge.app.detection.UsageTracker
import com.backlognudge.app.detection.WatchedApps
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.prefs.SecurePrefs
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPrefs(this)
        val securePrefs = SecurePrefs(this)

        setContent {
            BacklogNudgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(prefs, securePrefs, onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(prefs: AppPrefs, securePrefs: SecurePrefs, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val usageTracker = remember { UsageTracker(context) }

    val watcherEnabled by prefs.watcherEnabled.collectAsState(initial = false)
    val threshold by prefs.thresholdMinutes.collectAsState(initial = AppPrefs.DEFAULT_THRESHOLD_MINUTES)
    val ttsEnabled by prefs.ttsConfirmEnabled.collectAsState(initial = true)

    var apiKeyInput by remember { mutableStateOf(securePrefs.claudeApiKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    var hasUsageAccess by remember { mutableStateOf(usageTracker.hasUsageAccess()) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = usageTracker.hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
        ) {
            SectionLabel("Scroll-session watching")
            SettingsRow(
                title = "Watch for long Instagram sessions",
                subtitle = if (hasUsageAccess) "Usage access granted" else "Usage access needed — tap to grant",
            ) {
                Switch(
                    checked = watcherEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            if (enabled && !hasUsageAccess) {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                            prefs.setWatcherEnabled(enabled)
                            if (enabled) ForegroundWatcherService.start(context) else ForegroundWatcherService.stop(context)
                        }
                    }
                )
            }
            if (!hasUsageAccess) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Open usage-access settings")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Watching: ${WatchedApps.friendlyName(WatchedApps.INSTAGRAM)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Nudge timing")
            Text("Nudge after $threshold min of continuous scrolling", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = threshold.toFloat(),
                onValueChange = { scope.launch { prefs.setThresholdMinutes(it.toInt()) } },
                valueRange = 5f..60f,
                steps = 10
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Battery")
            OutlinedButton(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                Text("Exempt from battery optimization")
            }
            Text(
                "If nudges stop arriving after a while, your device may be killing the background watcher to save power. Exempting the app keeps it reliable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Voice capture")
            SettingsRow(title = "Speak confirmation back after capture", subtitle = null) {
                Switch(checked = ttsEnabled, onCheckedChange = { scope.launch { prefs.setTtsConfirmEnabled(it) } })
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Claude API key")
            Text(
                "Used to structure your spoken backlog items and to write nudge copy. Stored encrypted on-device, never logged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { securePrefs.claudeApiKey = apiKeyInput.trim().ifBlank { null } }) {
                    Text("Save key")
                }
            }
        }
    }
}

private fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

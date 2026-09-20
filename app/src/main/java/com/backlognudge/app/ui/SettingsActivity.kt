package com.backlognudge.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlognudge.app.detection.ForegroundWatcherService
import com.backlognudge.app.detection.UsageTracker
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import com.backlognudge.app.ui.theme.BucketStyle
import com.backlognudge.app.ui.theme.PosterTitle
import com.backlognudge.app.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPrefs(this)

        setContent {
            BacklogNudgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(prefs, onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val usageTracker = remember { UsageTracker(context) }

    val watcherEnabled by prefs.watcherEnabled.collectAsState(initial = false)
    val threshold by prefs.thresholdMinutes.collectAsState(initial = AppPrefs.DEFAULT_THRESHOLD_MINUTES)
    val dailyLimit by prefs.dailyLimitMinutes.collectAsState(initial = AppPrefs.DEFAULT_DAILY_LIMIT_MINUTES)
    val ttsEnabled by prefs.ttsConfirmEnabled.collectAsState(initial = true)

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "SETTINGS",
                    style = PosterTitle.copy(fontSize = 28.sp, lineHeight = 28.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel("Watching")
            SettingsRow(
                title = "Watch Instagram",
                subtitle = if (hasUsageAccess) null else "Needs permission — tap to grant"
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
                    },
                    // A toggle isn't "going", so it stays achromatic: an on
                    // switch reads as ink-on-bone, not as an accent.
                    colors = quietSwitchColors()
                )
            }
            if (!hasUsageAccess) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Turn on", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("Timing")
            Text(
                "Nudge after $threshold min of scrolling",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            QuietSlider(
                value = threshold.toFloat(),
                onValueChange = { scope.launch { prefs.setThresholdMinutes(it.toInt()) } },
                valueRange = 5f..60f,
                steps = 10
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "After $dailyLimit min today, nudge right away",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            QuietSlider(
                value = dailyLimit.toFloat(),
                onValueChange = { scope.launch { prefs.setDailyLimitMinutes(it.toInt()) } },
                valueRange = 15f..180f,
                steps = 10
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel("Reliability")
            SettingsRow(
                title = "Keep nudges on time",
                subtitle = "Stops your phone sleeping the app."
            ) {
                OutlinedButton(
                    onClick = { requestIgnoreBatteryOptimizations(context) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LocalExtraColors.current.hairline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Text("Allow")
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("Voice")
            SettingsRow(title = "Read it back to me", subtitle = null) {
                Switch(
                    checked = ttsEnabled,
                    onCheckedChange = { scope.launch { prefs.setTtsConfirmEnabled(it) } },
                    // A toggle isn't "going", so it stays achromatic: an on
                    // switch reads as ink-on-bone, not as an accent.
                    colors = quietSwitchColors()
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun quietSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.background,
    checkedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
    checkedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline
)

/**
 * The slider's track is chrome, not a "go" action, so the filled portion is a
 * grey; only the thumb picks up the accent to show where you are.
 *
 * Shape follows the mockup's `.trk`: a 4px hairline with a small round thumb
 * and no tick marks. M3's stock slider draws a thick bar with a dot per step,
 * which reads as a completely different control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    val activeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.onSurface

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        // No stepping dots: the mockup's track is unbroken.
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(thumbColor, CircleShape)
            )
        },
        track = { state ->
            val fraction = if (state.valueRange.endInclusive > state.valueRange.start) {
                ((state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(inactiveColor, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(activeColor, RoundedCornerShape(2.dp))
                )
            }
        }
    )
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
    Text(
        text.uppercase(),
        // Mockup `.sg`: the settings section labels are the one place the
        // data voice carries the go colour.
        style = BucketStyle,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

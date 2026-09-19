package com.backlognudge.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.detection.ForegroundWatcherService
import com.backlognudge.app.detection.UsageTracker
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import kotlinx.coroutines.launch

private data class OnboardStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val actionLabel: String
)

class OnboardingActivity : ComponentActivity() {

    private lateinit var usageTracker: UsageTracker
    private lateinit var prefs: AppPrefs

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { advance() }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { advance() }

    private var advanceStep: (() -> Unit)? = null
    private fun advance() { advanceStep?.invoke() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usageTracker = UsageTracker(this)
        prefs = AppPrefs(this)

        val steps = buildList {
            add(
                OnboardStep(
                    Icons.Filled.Bolt,
                    "Let's set you up",
                    "Backlog Nudge needs a few permissions to work: to notice when you've been scrolling, to hear your voice, and to nudge you reliably. This takes under a minute — we'll explain each one before asking.",
                    "Let's go"
                )
            )
            add(
                OnboardStep(
                    Icons.Filled.Visibility,
                    "See what app is in front",
                    "Android calls this “Usage access.” It only tells us which app is currently open — never what's on screen, never your messages or photos. That's how we notice a long Instagram session.",
                    "Grant usage access"
                )
            )
            add(
                OnboardStep(
                    Icons.Filled.BatteryChargingFull,
                    "Stay awake in the background",
                    "Some phones aggressively kill background apps to save battery, which would silently stop your nudges. Exempting Backlog Nudge keeps the watcher running reliably.",
                    "Allow in battery settings"
                )
            )
            add(
                OnboardStep(
                    Icons.Filled.Mic,
                    "Hear you out",
                    "Tap the Quick Settings tile anytime to speak a backlog item — speech-to-text happens on your device, and only the resulting text is sent off-device for structuring.",
                    "Allow microphone"
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    OnboardStep(
                        Icons.Filled.Notifications,
                        "Let nudges reach you",
                        "Nudges arrive as a notification (or a floating bubble, on devices that support it) so you see them right when it matters.",
                        "Allow notifications"
                    )
                )
            }
            add(
                OnboardStep(
                    Icons.Filled.CheckCircle,
                    "You're set",
                    "Add a Claude API key later in Settings for automatic voice parsing and warmer nudge copy — the app works without one too, it'll just save your words as-is.",
                    "Start using Backlog Nudge"
                )
            )
        }

        setContent {
            var stepIndex by remember { mutableStateOf(0) }
            advanceStep = { if (stepIndex < steps.lastIndex) stepIndex++ else finishOnboarding() }

            BacklogNudgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        step = steps[stepIndex],
                        stepIndex = stepIndex,
                        totalSteps = steps.size,
                        onAction = { handleStepAction(stepIndex, steps.size) },
                        onSkip = { advance() }
                    )
                }
            }
        }
    }

    private fun handleStepAction(stepIndex: Int, totalSteps: Int) {
        // Step order matches the list built in onCreate.
        when (stepIndex) {
            0 -> advance()
            1 -> {
                if (usageTracker.hasUsageAccess()) {
                    advance()
                } else {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    advance() // user returns via back button; MainActivity/Settings can re-prompt if still missing
                }
            }
            2 -> {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                }
                advance()
            }
            3 -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    advance()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && stepIndex == 4) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        advance()
                    } else {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    finishOnboarding()
                }
            }
        }
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            prefs.setOnboardingComplete(true)
            if (usageTracker.hasUsageAccess()) {
                prefs.setWatcherEnabled(true)
                ForegroundWatcherService.start(this@OnboardingActivity)
            }
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }
}

@Composable
private fun OnboardingScreen(
    step: OnboardStep,
    stepIndex: Int,
    totalSteps: Int,
    onAction: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = (stepIndex + 1) / totalSteps.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        AnimatedContent(targetState = step, label = "onboard-step") { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    current.icon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
            Text(step.actionLabel)
        }
        if (stepIndex in 1..3) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) { Text("Skip for now") }
        }
    }
}

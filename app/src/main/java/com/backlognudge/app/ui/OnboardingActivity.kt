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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.capture.VoiceCaptureActivity
import com.backlognudge.app.detection.ForegroundWatcherService
import com.backlognudge.app.detection.UsageTracker
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import com.backlognudge.app.ui.theme.BucketStyle
import com.backlognudge.app.ui.theme.HeadlineSerif
import com.backlognudge.app.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

/**
 * Nobody installs an app to learn what "Usage access" is. Each screen says
 * what it does for the person in one line; the one privacy fact worth stating
 * gets its own quiet caption instead of a paragraph of Android internals.
 */
private data class OnboardStep(
    val title: String,
    val body: String,
    val caption: String? = null,
    val actionLabel: String
)

class OnboardingActivity : ComponentActivity() {

    private lateinit var usageTracker: UsageTracker
    private lateinit var prefs: AppPrefs

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
                    title = "Things you meant to get to",
                    body = "Say them once. We'll bring them back when you're deep in a scroll.",
                    actionLabel = "Start"
                )
            )
            add(
                OnboardStep(
                    title = "Notice the long scrolls",
                    body = "So we can tell when Instagram has been open a while.",
                    caption = "Only which app is open — never what's on it.",
                    actionLabel = "Allow"
                )
            )
            add(
                OnboardStep(
                    title = "Don't let nudges get lost",
                    body = "Some phones shut us off to save battery.",
                    actionLabel = "Allow"
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    OnboardStep(
                        title = "So we can reach you",
                        body = "Nudges arrive as notifications.",
                        actionLabel = "Allow"
                    )
                )
            }
            add(
                OnboardStep(
                    title = "Add your first one",
                    body = "Hold the button and say what you've been putting off.",
                    actionLabel = "Speak it"
                )
            )
        }

        setContent {
            var stepIndex by remember { mutableStateOf(0) }
            advanceStep = { if (stepIndex < steps.lastIndex) stepIndex++ else finishOnboarding() }

            BacklogNudgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
        val lastIndex = totalSteps - 1
        when {
            stepIndex == 0 -> advance()
            stepIndex == 1 -> {
                if (usageTracker.hasUsageAccess()) {
                    advance()
                } else {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    advance() // user returns via back button; Settings can re-prompt if still missing
                }
            }
            stepIndex == 2 -> {
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
            stepIndex < lastIndex && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    advance()
                } else {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // Final screen: "Speak it" means speak it, not "close this screen".
            else -> finishOnboarding(startCapture = true)
        }
    }

    private fun finishOnboarding(startCapture: Boolean = false) {
        lifecycleScope.launch {
            prefs.setOnboardingComplete(true)
            if (usageTracker.hasUsageAccess()) {
                prefs.setWatcherEnabled(true)
                ForegroundWatcherService.start(this@OnboardingActivity)
            }
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            if (startCapture) {
                startActivity(Intent(this@OnboardingActivity, VoiceCaptureActivity::class.java))
            }
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
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp)
    ) {
        // A quiet counter, not a coloured progress bar — nothing here is "going".
        Text(
            "STEP ${stepIndex + 1} OF $totalSteps",
            style = BucketStyle,
            color = extras.faint
        )
        Spacer(Modifier.weight(1f))
        AnimatedContent(targetState = step, label = "onboard-step") { current ->
            Column {
                Text(
                    current.title,
                    style = HeadlineSerif,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (current.caption != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        current.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.faint
                    )
                }
            }
        }
        Spacer(Modifier.weight(1.3f))
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(step.actionLabel, style = MaterialTheme.typography.labelLarge)
        }
        if (stepIndex in 1 until totalSteps - 1) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onSkip) {
                    Text("Not now", color = extras.faint)
                }
            }
        }
    }
}

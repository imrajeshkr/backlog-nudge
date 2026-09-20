package com.backlognudge.app.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import com.backlognudge.app.ui.theme.RowTitle
import com.backlognudge.app.ui.theme.RingCycleMs
import com.backlognudge.app.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

private enum class CaptureState { REQUESTING_PERMISSION, LISTENING, PROCESSING, DONE, ERROR }

class VoiceCaptureActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else showErrorAndClose("Microphone permission is needed to capture by voice.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stateHolder = mutableStateOf(CaptureState.REQUESTING_PERMISSION)
        val messageHolder = mutableStateOf("Listening…")
        val partialHolder = mutableStateOf("")

        setContent {
            VoiceCaptureScreen(
                state = stateHolder.value,
                message = messageHolder.value,
                partial = partialHolder.value,
                onCancel = { finish() }
            )
        }

        this.state = stateHolder
        this.message = messageHolder
        this.partial = partialHolder

        if (ContextCompatCheck(this)) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        tts = TextToSpeech(this) { }
    }

    private lateinit var state: MutableState<CaptureState>
    private lateinit var message: MutableState<String>
    private lateinit var partial: MutableState<String>

    private fun ContextCompatCheck(activity: ComponentActivity): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showErrorAndClose("Speech recognition isn't available on this device.")
            return
        }
        state.value = CaptureState.LISTENING
        message.value = "Listening…"

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                state.value = CaptureState.PROCESSING
                message.value = "Structuring that…"
            }

            override fun onError(error: Int) {
                val text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that - tap the tile to try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything - tap the tile to try again."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network hiccup during recognition - tap the tile to try again."
                    else -> "Couldn't capture that - tap the tile to try again."
                }
                showErrorAndClose(text)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull().orEmpty()
                handleTranscript(transcript)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial.value = matches?.firstOrNull().orEmpty()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    private fun handleTranscript(transcript: String) {
        state.value = CaptureState.PROCESSING
        message.value = "Saving that…"

        lifecycleScope.launch {
            val item = TranscriptParser().parse(transcript)
            val db = (application as BacklogNudgeApp).database
            db.backlogDao().insert(item)

            val confirmation = "Got it - added “${item.title}”."
            state.value = CaptureState.DONE
            message.value = confirmation

            val ttsEnabled = AppPrefs(applicationContext).ttsConfirmEnabled.first()
            // Best-effort spoken confirmation; ignore failures.
            runCatching {
                if (ttsEnabled) {
                    tts?.speak(confirmation, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }

            delay(1400)
            finish()
        }
    }

    private fun showErrorAndClose(text: String) {
        state.value = CaptureState.ERROR
        message.value = text
        lifecycleScope.launch {
            delay(2000)
            finish()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun VoiceCaptureScreen(
    state: CaptureState,
    message: String,
    partial: String,
    onCancel: () -> Unit
) {
    BacklogNudgeTheme {
        Dialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The mic is "going" — this is the one green thing on the sheet.
                    PulsingMicIcon(active = state == CaptureState.LISTENING)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (partial.isNotBlank() && state != CaptureState.ERROR) {
                        Spacer(Modifier.height(10.dp))
                        // What the user said, echoed back in the UI sans.
                        Text(
                            text = partial,
                            style = RowTitle.copy(fontSize = 19.sp, lineHeight = 24.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    if (state == CaptureState.PROCESSING) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingMicIcon(active: Boolean) {
    // Mockup `@keyframes ring`: two rings expand from scale(.76) to scale(1.4)
    // while fading .9 -> 0 over 2.6s, the second delayed half a cycle. The mic
    // itself does not throb — it sits still and the room moves around it.
    val transition = rememberInfiniteTransition(label = "mic-halo")
    val go = MaterialTheme.colorScheme.primary
    val reduced = reduceMotion()

    // Both rings are always composed — gating the animateFloat call itself
    // would change the composition shape between recompositions.
    val r1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RingCycleMs, easing = LinearEasing)
        ),
        label = "ring-a"
    )
    val r2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RingCycleMs, easing = LinearEasing),
            initialStartOffset = StartOffset(RingCycleMs / 2)
        ),
        label = "ring-b"
    )

    val show = active && !reduced
    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        listOf(r1, r2).forEach { p ->
            val scale = 0.76f + (1.4f - 0.76f) * p
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (show) 0.9f * (1f - p) else 0f
                    }
                    .border(1.5.dp, go, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(go, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

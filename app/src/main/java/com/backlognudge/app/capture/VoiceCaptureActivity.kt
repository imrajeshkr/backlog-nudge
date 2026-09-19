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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.prefs.SecurePrefs
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
        message.value = "Structuring that…"

        lifecycleScope.launch {
            val securePrefs = SecurePrefs(applicationContext)
            val parser = TranscriptParser { securePrefs.claudeApiKey }
            val result = parser.parse(transcript)
            val db = (application as BacklogNudgeApp).database

            val confirmation: String = when (result) {
                is ParseResult.Parsed -> {
                    db.backlogDao().insertAll(result.items)
                    if (result.items.size == 1) {
                        "Got it - added “${result.items.first().title}”."
                    } else {
                        "Added ${result.items.size} items to your backlog."
                    }
                }
                is ParseResult.Fallback -> {
                    db.backlogDao().insert(result.item)
                    result.reason
                }
            }

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
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnClickOutside = true)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1C1730),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PulsingMicIcon(active = state == CaptureState.LISTENING)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = message,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (state == CaptureState.LISTENING && partial.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "“$partial”",
                        color = Color(0xFFB8AEEA),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                if (state == CaptureState.PROCESSING) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(color = Color(0xFF9C8CFF), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun PulsingMicIcon(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.25f else 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color(0xFF5B4FE9), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White)
    }
}

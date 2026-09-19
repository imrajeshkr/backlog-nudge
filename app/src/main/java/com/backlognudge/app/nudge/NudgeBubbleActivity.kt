package com.backlognudge.app.nudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.NudgeResponse
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import kotlinx.coroutines.launch

/**
 * Content shown when the bubble is expanded (or, on devices/settings where
 * bubbles fall back to a plain notification, when the user taps it).
 */
class NudgeBubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val db = (application as BacklogNudgeApp).database

        setContent {
            var item by remember { mutableStateOf<BacklogItem?>(null) }
            LaunchedEffect(itemId) { item = db.backlogDao().getById(itemId) }

            BacklogNudgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val current = item
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        BubbleContent(
                            item = current,
                            onDone = { respond(itemId, eventId, NudgeResponse.DID_IT) },
                            onSnooze = { respond(itemId, eventId, NudgeResponse.SNOOZED) },
                            onDismiss = { respond(itemId, eventId, NudgeResponse.DISMISSED) }
                        )
                    }
                }
            }
        }
    }

    private fun respond(itemId: Long, eventId: Long, response: NudgeResponse) {
        val db = (application as BacklogNudgeApp).database
        lifecycleScope.launch {
            when (response) {
                NudgeResponse.DID_IT -> db.backlogDao().markDone(itemId)
                NudgeResponse.SNOOZED -> db.backlogDao().snooze(itemId, System.currentTimeMillis() + 30 * 60 * 1000L)
                NudgeResponse.DISMISSED -> db.backlogDao().recordDismiss(itemId)
                else -> {}
            }
            if (eventId >= 0) {
                db.nudgeDao().getById(eventId)?.let { db.nudgeDao().update(it.copy(response = response)) }
            }
            NudgeNotifier.cancel(this@NudgeBubbleActivity, itemId)
            finish()
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}

@Composable
private fun BubbleContent(
    item: BacklogItem,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Got a minute?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "${item.estimatedMinutes.label} · ${item.energy.label} · ${item.category.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Do it now")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Snooze 30 min")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Not today")
        }
    }
}

package com.backlognudge.app.nudge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.NudgeResponse
import com.backlognudge.app.detection.WatchedApps
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import com.backlognudge.app.ui.theme.KickerStyle
import com.backlognudge.app.ui.theme.PosterTitle
import com.backlognudge.app.ui.theme.LocalExtraColors
import com.backlognudge.app.ui.theme.MetaStyle
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
        val sessionMinutes = intent.getIntExtra(EXTRA_SESSION_MINUTES, 0)
        val watchedPackage = intent.getStringExtra(EXTRA_WATCHED_PACKAGE) ?: WatchedApps.INSTAGRAM
        val db = (application as BacklogNudgeApp).database

        setContent {
            var item by remember { mutableStateOf<BacklogItem?>(null) }
            LaunchedEffect(itemId) { item = db.backlogDao().getById(itemId) }

            BacklogNudgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val current = item
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        BubbleContent(
                            item = current,
                            sessionMinutes = sessionMinutes,
                            appName = WatchedApps.friendlyName(watchedPackage),
                            onLeave = { respond(itemId, eventId, NudgeResponse.LEFT_APP, goHome = true) },
                            onDone = { respond(itemId, eventId, NudgeResponse.DID_IT) },
                            onSnooze = { respond(itemId, eventId, NudgeResponse.SNOOZED) },
                            onDismiss = { respond(itemId, eventId, NudgeResponse.DISMISSED) }
                        )
                    }
                }
            }
        }
    }

    private fun respond(itemId: Long, eventId: Long, response: NudgeResponse, goHome: Boolean = false) {
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
            if (goHome) {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_SESSION_MINUTES = "extra_session_minutes"
        const val EXTRA_WATCHED_PACKAGE = "extra_watched_package"
    }
}

@Composable
private fun BubbleContent(
    item: BacklogItem,
    sessionMinutes: Int,
    appName: String,
    onLeave: () -> Unit,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (sessionMinutes > 0) {
            // The one factual line about right now, in the "go" colour because
            // it's the reason the nudge exists.
            Text(
                "$sessionMinutes MIN ON ${appName.uppercase()}",
                style = KickerStyle,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(14.dp))
        }
        // The user's own words.
        Text(
            item.title.uppercase(),
            style = PosterTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "TAKES ${item.estimatedMinutes.minutes} MIN",
            style = MetaStyle,
            color = extras.faint
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onLeave,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Let's go", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onDone,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, extras.hairline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Already done", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSnooze,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, extras.hairline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Snooze 30m", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onDismiss) {
                Text("Not today", color = extras.faint)
            }
        }
    }
}

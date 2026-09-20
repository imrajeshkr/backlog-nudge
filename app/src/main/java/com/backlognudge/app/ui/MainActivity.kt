package com.backlognudge.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.ItemStatus
import com.backlognudge.app.data.NudgeEvent
import com.backlognudge.app.data.NudgeResponse
import com.backlognudge.app.data.TimeEstimate
import com.backlognudge.app.detection.UsageTracker
import com.backlognudge.app.detection.WatchedApps
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.components.ItemEditDialog
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import com.backlognudge.app.ui.theme.BucketStyle
import com.backlognudge.app.ui.theme.LocalExtraColors
import com.backlognudge.app.ui.theme.MetaStyle
import com.backlognudge.app.ui.theme.PosterHeadline
import com.backlognudge.app.ui.theme.PosterTitle
import com.backlognudge.app.ui.theme.RowTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPrefs(this)

        lifecycleScope.launch {
            val onboardingDone = prefs.onboardingComplete.first()
            if (!onboardingDone) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                finish()
                return@launch
            }
        }

        setContent {
            BacklogNudgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(prefs = prefs)
                }
            }
        }
    }
}

/**
 * Time buckets, not recency. Mid-scroll the question is always "what fits in
 * this gap" — never "what's oldest".
 */
private enum class TimeBucket(val header: String, val estimates: Set<TimeEstimate>) {
    QUICK("Got five minutes", setOf(TimeEstimate.MIN_5)),
    HALF_HOUR("Got half an hour", setOf(TimeEstimate.MIN_15, TimeEstimate.MIN_30)),
    SITTING("Needs a real sitting", setOf(TimeEstimate.MIN_60, TimeEstimate.MIN_120))
}

@Composable
fun MainScreen(prefs: AppPrefs) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { (context.applicationContext as BacklogNudgeApp).database }
    val scope = rememberCoroutineScope()

    val items by db.backlogDao().observeAll().collectAsState(initial = emptyList())
    val watcherEnabled by prefs.watcherEnabled.collectAsState(initial = false)
    val watchedPackages by prefs.watchedPackages.collectAsState(initial = WatchedApps.DEFAULT_PACKAGES)
    val recentNudges by db.nudgeDao().observeRecent().collectAsState(initial = emptyList())
    val lastHeartbeat by prefs.lastHeartbeat.collectAsState(initial = 0L)

    val usageTracker = remember { UsageTracker(context) }
    val hasUsageAccess = remember { mutableStateOf(usageTracker.hasUsageAccess()) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasUsageAccess.value = usageTracker.hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ticks so the stale-heartbeat check (and item ages) re-evaluate against
    // "now" even though nothing in the datastore itself is changing.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }
    val watcherLooksDead = watcherEnabled && hasUsageAccess.value &&
        AppPrefs.isHeartbeatStale(lastHeartbeat, now)
    val noWatchedAppInstalled = watcherEnabled && WatchedApps.noneInstalled(context, watchedPackages)

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BacklogItem?>(null) }
    var showNudgeHistory by remember { mutableStateOf(false) }
    var notInstalledDismissed by remember { mutableStateOf(false) }
    val launchVoiceCapture = {
        context.startActivity(Intent(context, com.backlognudge.app.capture.VoiceCaptureActivity::class.java))
    }
    val openSettings = {
        context.startActivity(Intent(context, SettingsActivity::class.java))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Wordmark(
                onHistory = { showNudgeHistory = !showNudgeHistory },
                onSettings = openSettings
            )
        },
        floatingActionButton = {
            // One FAB: the mic. Typing stays reachable from the empty state and
            // by tapping a row to edit it.
            FloatingActionButton(
                onClick = launchVoiceCapture,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Speak a backlog item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            AnimatedVisibility(visible = watcherEnabled && hasUsageAccess.value && watcherLooksDead) {
                StatusBanner(
                    text = "Nudges paused — your phone put us to sleep.",
                    actionLabel = "Fix",
                    onAction = openSettings
                )
            }
            AnimatedVisibility(visible = watcherEnabled && !hasUsageAccess.value) {
                StatusBanner(
                    text = "Nudges are off — permission was turned off.",
                    actionLabel = "Turn on",
                    onAction = openSettings
                )
            }
            AnimatedVisibility(visible = noWatchedAppInstalled && !notInstalledDismissed) {
                StatusBanner(
                    text = "${WatchedApps.friendlyName(WatchedApps.INSTAGRAM)} isn't installed.",
                    actionLabel = "Dismiss",
                    onAction = { notInstalledDismissed = true }
                )
            }
            AnimatedVisibility(visible = !watcherEnabled) {
                StatusBanner(
                    text = "Watching is off.",
                    actionLabel = "Turn on",
                    onAction = openSettings
                )
            }
            AnimatedVisibility(visible = showNudgeHistory) {
                NudgeHistorySection(recentNudges = recentNudges, items = items)
            }

            val openItems = items.filter { it.status == ItemStatus.OPEN }

            if (openItems.isEmpty()) {
                EmptyState(onType = { showAddDialog = true })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp)
                ) {
                    TimeBucket.entries.forEach { bucket ->
                        val bucketItems = openItems.filter { it.estimatedMinutes in bucket.estimates }
                        // A bucket with nothing in it doesn't exist.
                        if (bucketItems.isEmpty()) return@forEach

                        item(key = "header_${bucket.name}") {
                            BucketHeader(bucket.header, modifier = Modifier.animateItem())
                        }
                        items(bucketItems, key = { it.id }) { item ->
                            BacklogItemRow(
                                item = item,
                                now = now,
                                modifier = Modifier.animateItem(),
                                onClick = { editingItem = item },
                                onDone = { scope.launch { db.backlogDao().markDone(item.id) } },
                                onSnooze = {
                                    scope.launch {
                                        db.backlogDao().snooze(item.id, System.currentTimeMillis() + 30 * 60 * 1000L)
                                    }
                                }
                            )
                        }
                    }
                    // Typing stays reachable once the list is no longer empty -
                    // otherwise the only way in is voice.
                    item(key = "type_instead") {
                        TextButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Type one instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ItemEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { newItem ->
                scope.launch { db.backlogDao().insert(newItem) }
                showAddDialog = false
            }
        )
    }

    editingItem?.let { item ->
        ItemEditDialog(
            initial = item,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                scope.launch { db.backlogDao().update(updated) }
                editingItem = null
            },
            onDelete = {
                scope.launch { db.backlogDao().delete(item.id) }
                editingItem = null
            }
        )
    }
}

/**
 * Plain background, no filled colour bar. The wordmark is the poster face,
 * uppercase, in ordinary ink — green is reserved for going and finishing.
 */
@Composable
private fun Wordmark(onHistory: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "BACKLOG",
            style = PosterTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        CircleIconButton(onClick = onHistory) {
            Icon(Icons.Filled.History, contentDescription = "Nudge history", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val extras = LocalExtraColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .border(1.dp, extras.hairline, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            content()
        }
    }
}

@Composable
private fun BucketHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = BucketStyle,
        // Section labels are chrome, so they're the faint grey — never green.
        color = LocalExtraColors.current.faint,
        modifier = modifier.padding(top = 26.dp, bottom = 10.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BacklogItemRow(
    item: BacklogItem,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onSnooze: () -> Unit
) {
    val extras = LocalExtraColors.current
    // Tapping the tick plays the completion beat locally before the row leaves.
    var completing by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(completing) {
        if (completing) {
            delay(380)
            onDone()
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { completing = true; false }
                // Snooze isn't a "go" action, so it also isn't a removal — the
                // row snaps back and simply won't be nudge-eligible for 30 min.
                SwipeToDismissBoxValue.EndToStart -> { onSnooze(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val goingRight = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val bg = if (goingRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (goingRight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (goingRight) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Text(if (goingRight) "DONE" else "SNOOZE 30M", style = MetaStyle, color = fg)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.Top
        ) {
            CompletionTick(completing = completing, onTap = { completing = true })
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // The user's own words, in the user's voice.
                Text(
                    item.title,
                    style = RowTitle,
                    color = if (completing) extras.faint else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (completing) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    metaLine(item, now),
                    style = MetaStyle,
                    color = extras.faint
                )
            }
        }
    }
}

/** The completion tick — the second and last thing green is spent on. */
@Composable
private fun CompletionTick(completing: Boolean, onTap: () -> Unit) {
    val extras = LocalExtraColors.current
    val go = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(
        targetValue = if (completing) go else Color.Transparent,
        animationSpec = tween(180),
        label = "tick-fill"
    )
    val ring by animateColorAsState(
        targetValue = if (completing) go else extras.faint,
        animationSpec = tween(180),
        label = "tick-ring"
    )
    val scale by animateFloatAsState(
        targetValue = if (completing) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tick-scale"
    )
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ring, CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        if (completing) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/** One meta line only, e.g. "15 MIN · 3 DAYS OLD". */
private fun metaLine(item: BacklogItem, now: Long): String {
    val days = ((now - item.createdAt) / 86_400_000L).toInt()
    val age = when {
        days <= 0 -> "TODAY"
        days == 1 -> "1 DAY OLD"
        else -> "$days DAYS OLD"
    }
    return "${item.estimatedMinutes.label.uppercase()} · $age"
}

@Composable
private fun StatusBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No red, no warning icon: a permanent alarm just teaches people to
            // ignore alarms. Plain surface, and the way out is the only accent.
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NudgeHistorySection(recentNudges: List<NudgeEvent>, items: List<BacklogItem>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "RECENT NUDGES",
                style = BucketStyle,
                color = LocalExtraColors.current.faint
            )
            Spacer(Modifier.height(10.dp))
            if (recentNudges.isEmpty()) {
                Text(
                    "Nothing yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recentNudges.take(8).forEach { event ->
                    val title = items.firstOrNull { it.id == event.itemId }?.title ?: "(deleted item)"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = RowTitle.copy(fontSize = 16.sp, lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            responseLabel(event.response),
                            style = MetaStyle,
                            color = LocalExtraColors.current.faint
                        )
                    }
                }
            }
        }
    }
}

private fun responseLabel(response: NudgeResponse): String = when (response) {
    NudgeResponse.DID_IT -> "DONE"
    NudgeResponse.SNOOZED -> "SNOOZED"
    NudgeResponse.DISMISSED -> "NOT TODAY"
    NudgeResponse.REMOVED -> "REMOVED"
    NudgeResponse.PENDING -> "PENDING"
    NudgeResponse.LEFT_APP -> "LEFT APP"
}

@Composable
private fun EmptyState(onType: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "NOTHING WAITING",
            style = PosterHeadline,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Tap the mic and say what you've been meaning to do.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onType) {
            Text("Type it instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // No second primary button here: the mic FAB is already the one green
        // "go" on this screen, and two of them would cancel each other out.
    }
}

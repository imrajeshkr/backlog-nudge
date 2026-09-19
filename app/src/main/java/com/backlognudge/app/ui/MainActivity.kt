package com.backlognudge.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.R
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.EnergyLevel
import com.backlognudge.app.data.ItemCategory
import com.backlognudge.app.data.ItemStatus
import com.backlognudge.app.data.NudgeEvent
import com.backlognudge.app.data.NudgeResponse
import com.backlognudge.app.data.TimeEstimate
import com.backlognudge.app.detection.UsageTracker
import com.backlognudge.app.detection.WatchedApps
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.components.ItemEditDialog
import com.backlognudge.app.ui.theme.BacklogNudgeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as BacklogNudgeApp).database
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(prefs = prefs)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Re-check whenever the screen resumes (e.g. coming back from Settings).
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

    // Ticks so the stale-heartbeat check below re-evaluates against "now" even
    // though nothing in the datastore itself is changing - otherwise a dead
    // background service would stay silently unreported.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showNudgeHistory = !showNudgeHistory }) {
                        Icon(Icons.Filled.History, contentDescription = "Nudge history")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            AnimatedVisibility(visible = watcherEnabled && !hasUsageAccess.value) {
                StatusBanner(
                    text = "Usage-access permission was turned off, so Backlog Nudge can't watch for scroll sessions anymore.",
                    actionLabel = "Fix in Settings"
                ) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }
            AnimatedVisibility(visible = watcherEnabled && hasUsageAccess.value && watcherLooksDead) {
                StatusBanner(
                    text = "The background watcher seems to have stopped — likely your phone's battery saver killed it. Exempt Backlog Nudge in Settings to keep nudges reliable.",
                    actionLabel = "Fix in Settings"
                ) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }
            AnimatedVisibility(visible = noWatchedAppInstalled) {
                StatusBanner(
                    text = "${WatchedApps.friendlyName(WatchedApps.INSTAGRAM)} isn't installed here, so this device won't get nudges.",
                    actionLabel = "Dismiss",
                    onAction = {}
                )
            }
            AnimatedVisibility(visible = !watcherEnabled) {
                StatusBanner(
                    text = "Scroll-session watching is off — you'll only get nudges you trigger yourself.",
                    actionLabel = "Turn on"
                ) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }
            AnimatedVisibility(visible = showNudgeHistory) {
                NudgeHistorySection(recentNudges = recentNudges, items = items)
            }

            val openItems = items.filter { it.status == ItemStatus.OPEN }

            if (openItems.isEmpty()) {
                EmptyState(onAddManually = { showAddDialog = true })
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(openItems, key = { it.id }) { item ->
                        BacklogItemRow(
                            item = item,
                            modifier = Modifier.animateItemPlacement(),
                            onClick = { editingItem = item },
                            onDone = { scope.launch { db.backlogDao().markDone(item.id) } },
                            onDelete = { scope.launch { db.backlogDao().delete(item.id) } }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
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
            }
        )
    }
}

@Composable
private fun StatusBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun NudgeHistorySection(recentNudges: List<NudgeEvent>, items: List<BacklogItem>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Recent nudges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (recentNudges.isEmpty()) {
                Text(
                    "No nudges yet — once you've got backlog items and spend a while in a watched app, they'll show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recentNudges.take(8).forEach { event ->
                    val title = items.firstOrNull { it.id == event.itemId }?.title ?: "(deleted item)"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            responseLabel(event.response),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun responseLabel(response: NudgeResponse): String = when (response) {
    NudgeResponse.DID_IT -> "Done"
    NudgeResponse.SNOOZED -> "Snoozed"
    NudgeResponse.DISMISSED -> "Not today"
    NudgeResponse.REMOVED -> "Removed"
    NudgeResponse.PENDING -> "Pending"
}

@Composable
private fun EmptyState(onAddManually: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Your backlog is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pull down Quick Settings and tap the “Capture item” tile from anywhere — say what's on your mind and it lands here automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onAddManually) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Or add one by typing")
        }
    }
}

@Composable
private fun BacklogItemRow(
    item: BacklogItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (item.needsReview) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Needs a quick look — captured without full details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(6.dp))
                AssistRowChips(item)
            }
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Mark done", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AssistRowChips(item: BacklogItem) {
    Row {
        Chip(item.estimatedMinutes.label)
        Spacer(Modifier.width(6.dp))
        Chip(item.energy.label)
        Spacer(Modifier.width(6.dp))
        Chip(item.category.label)
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

package com.backlognudge.app.detection

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.R
import com.backlognudge.app.nudge.NudgeManager
import com.backlognudge.app.prefs.AppPrefs
import com.backlognudge.app.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that periodically polls UsageStatsManager to find out
 * whether a watched app (Instagram, for now) has been continuously in the
 * foreground for the configured threshold. Runs with a low-priority
 * "status" notification as required for reliable background polling.
 */
class ForegroundWatcherService : LifecycleService() {

    private lateinit var prefs: AppPrefs
    private lateinit var usageTracker: UsageTracker
    private lateinit var nudgeManager: NudgeManager
    private var pollJob: Job? = null

    // In-memory continuous-session tracking.
    private var sessionPackage: String? = null
    private var sessionStartTs: Long = 0L
    private var lastSeenWatchedTs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        usageTracker = UsageTracker(this)
        nudgeManager = NudgeManager(this)
        startForeground(NOTIFICATION_ID, buildStatusNotification(idleText()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (pollJob == null || pollJob?.isActive != true) {
            pollJob = lifecycleScope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (true) {
            runCatching { pollOnce() }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollOnce() {
        prefs.recordHeartbeat()

        if (!usageTracker.hasUsageAccess()) {
            updateNotification("Usage-access permission was revoked — nudges are paused. Tap to fix.")
            return
        }

        val watched = prefs.watchedPackages.first()
        val thresholdMs = prefs.thresholdMinutes.first() * 60_000L
        val now = System.currentTimeMillis()
        val fg = usageTracker.currentForegroundPackage()

        if (fg != null && fg in watched) {
            if (sessionPackage != fg) {
                sessionPackage = fg
                sessionStartTs = now
            }
            lastSeenWatchedTs = now
        } else if (sessionPackage != null) {
            val gap = now - lastSeenWatchedTs
            if (gap > GRACE_PERIOD_MS) {
                // Real interruption (e.g. switched to another app) - session ends.
                sessionPackage = null
                sessionStartTs = 0L
            }
            // else: brief interruption (notification shade, quick switch) - keep the timer running.
        }

        val pkg = sessionPackage
        if (pkg == null) {
            updateNotification(idleText())
            return
        }

        val elapsed = now - sessionStartTs
        val remainingMin = ((thresholdMs - elapsed) / 60_000L).coerceAtLeast(0)
        val name = WatchedApps.friendlyName(pkg)

        if (elapsed >= thresholdMs) {
            updateNotification("Nudging you about $name now…")
            nudgeManager.maybeTriggerNudge(pkg)
            // Re-arm: next nudge fires after another full threshold of continuous use.
            sessionStartTs = now
        } else {
            updateNotification("Watching $name — nudge in ${remainingMin + 1} min if it keeps going")
        }
    }

    private fun idleText(): String = "Watching for long scroll sessions"

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildStatusNotification(text))
    }

    private fun buildStatusNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, BacklogNudgeApp.CHANNEL_WATCHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Backlog Nudge")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent)
            .build()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 5_000L
        private const val GRACE_PERIOD_MS = 20_000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, ForegroundWatcherService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ForegroundWatcherService::class.java))
        }
    }
}

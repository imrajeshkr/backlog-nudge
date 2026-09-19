package com.backlognudge.app.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.backlognudge.app.detection.WatchedApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_prefs")

class AppPrefs(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val WATCHER_ENABLED = booleanPreferencesKey("watcher_enabled")
        val THRESHOLD_MINUTES = intPreferencesKey("threshold_minutes")
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val WATCHED_PACKAGES = stringSetPreferencesKey("watched_packages")
        val LAST_HEARTBEAT = longPreferencesKey("last_heartbeat")
        val TTS_CONFIRM = booleanPreferencesKey("tts_confirm_enabled")
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = done }
    }

    val watcherEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WATCHER_ENABLED] ?: false }

    suspend fun setWatcherEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WATCHER_ENABLED] = enabled }
    }

    val thresholdMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.THRESHOLD_MINUTES] ?: DEFAULT_THRESHOLD_MINUTES }

    suspend fun setThresholdMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.THRESHOLD_MINUTES] = minutes }
    }

    /** Once today's cumulative time in a watched app crosses this, re-opening it nudges almost immediately (see OVER_LIMIT_THRESHOLD_MS) instead of waiting for the full continuous threshold again. */
    val dailyLimitMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_DAILY_LIMIT_MINUTES }

    suspend fun setDailyLimitMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DAILY_LIMIT_MINUTES] = minutes }
    }

    val watchedPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.WATCHED_PACKAGES] ?: WatchedApps.DEFAULT_PACKAGES }

    suspend fun setWatchedPackages(packages: Set<String>) {
        context.dataStore.edit { it[Keys.WATCHED_PACKAGES] = packages }
    }

    val ttsConfirmEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.TTS_CONFIRM] ?: true }

    suspend fun setTtsConfirmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TTS_CONFIRM] = enabled }
    }

    val lastHeartbeat: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_HEARTBEAT] ?: 0L }

    suspend fun recordHeartbeat(ts: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.LAST_HEARTBEAT] = ts }
    }

    companion object {
        const val DEFAULT_THRESHOLD_MINUTES = 15
        const val DEFAULT_DAILY_LIMIT_MINUTES = 60

        /** Fast re-nudge delay once today's usage is already past the daily limit - deliberately not a full continuous session, since the point is "you're already over budget, don't let it restart quietly." */
        const val OVER_LIMIT_THRESHOLD_MS = 30_000L

        /**
         * The watcher polls every ~5s (see ForegroundWatcherService.POLL_INTERVAL_MS).
         * If we haven't seen a heartbeat in several multiples of that, the OS has
         * almost certainly killed the background service (a battery-optimization
         * side effect) rather than it just being mid-poll - worth surfacing to the
         * user instead of nudges silently stopping.
         */
        const val HEARTBEAT_STALE_MS = 60_000L

        fun isHeartbeatStale(lastHeartbeat: Long, now: Long = System.currentTimeMillis()): Boolean =
            lastHeartbeat > 0L && (now - lastHeartbeat) > HEARTBEAT_STALE_MS
    }
}

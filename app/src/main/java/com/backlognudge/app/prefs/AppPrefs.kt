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
    }
}

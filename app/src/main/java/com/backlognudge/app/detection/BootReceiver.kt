package com.backlognudge.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.backlognudge.app.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Also fires on ACTION_MY_PACKAGE_REPLACED (an app update/reinstall kills any
        // running foreground service with nothing to restart it otherwise - leaving
        // the watcher silently dead until the next full device reboot).
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = AppPrefs(context).watcherEnabled.first()
                if (enabled) {
                    ForegroundWatcherService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

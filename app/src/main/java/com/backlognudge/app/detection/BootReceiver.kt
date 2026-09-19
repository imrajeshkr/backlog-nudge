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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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

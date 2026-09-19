package com.backlognudge.app.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.NudgeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NudgeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (itemId < 0) return

        val pendingResult = goAsync()
        val db = (context.applicationContext as BacklogNudgeApp).database

        if (intent.action == ACTION_LEAVE) {
            // Just back out to the home screen - deliberately does NOT touch the item's
            // status. Leaving the watched app is a separate decision from having actually
            // finished the task, which is why this is a distinct action from ACTION_DONE.
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_LEAVE -> setEventResponse(context, eventId, NudgeResponse.LEFT_APP)
                    ACTION_DONE -> {
                        db.backlogDao().markDone(itemId)
                        setEventResponse(context, eventId, NudgeResponse.DID_IT)
                    }
                    ACTION_SNOOZE -> {
                        val until = System.currentTimeMillis() + SNOOZE_DURATION_MS
                        db.backlogDao().snooze(itemId, until)
                        setEventResponse(context, eventId, NudgeResponse.SNOOZED)
                    }
                    ACTION_DISMISS -> {
                        db.backlogDao().recordDismiss(itemId)
                        setEventResponse(context, eventId, NudgeResponse.DISMISSED)
                    }
                }
                NudgeNotifier.cancel(context, itemId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun setEventResponse(context: Context, eventId: Long, response: NudgeResponse) {
        if (eventId < 0) return
        val db = (context.applicationContext as BacklogNudgeApp).database
        val event = db.nudgeDao().getById(eventId) ?: return
        db.nudgeDao().update(event.copy(response = response))
    }

    companion object {
        const val ACTION_LEAVE = "com.backlognudge.app.action.NUDGE_LEAVE"
        const val ACTION_DONE = "com.backlognudge.app.action.NUDGE_DONE"
        const val ACTION_SNOOZE = "com.backlognudge.app.action.NUDGE_SNOOZE"
        const val ACTION_DISMISS = "com.backlognudge.app.action.NUDGE_DISMISS"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_EVENT_ID = "extra_event_id"
        private const val SNOOZE_DURATION_MS = 30 * 60 * 1000L
    }
}

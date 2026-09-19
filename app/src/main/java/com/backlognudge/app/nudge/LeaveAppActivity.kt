package com.backlognudge.app.nudge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.NudgeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for the notification's "Let's go" action. A
 * BroadcastReceiver can't legally send another app's task to the background
 * on its own behalf - Android blocks that as a background activity launch
 * (see ActivityTaskManager's BAL checks). An Activity started from a
 * notification tap can, since for that instant it IS the foreground
 * activity. This finishes itself immediately after handing off to home.
 */
class LeaveAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)

        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        if (eventId >= 0) {
            val db = (application as BacklogNudgeApp).database
            CoroutineScope(Dispatchers.IO).launch {
                db.nudgeDao().getById(eventId)?.let { db.nudgeDao().update(it.copy(response = NudgeResponse.LEFT_APP)) }
            }
        }
        if (itemId >= 0) NudgeNotifier.cancel(this, itemId)
        finish()
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}

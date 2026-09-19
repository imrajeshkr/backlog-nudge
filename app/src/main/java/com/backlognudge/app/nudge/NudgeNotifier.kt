package com.backlognudge.app.nudge

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.R
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.ui.MainActivity

/**
 * Posts the nudge. Prefers a Bubbles-style conversation notification (the
 * "floating, pull-open-from-anywhere" affordance) but never depends on it:
 * the same Builder call produces a perfectly normal heads-up notification
 * with the same title/copy/actions on any device/OS-version/user-setting
 * where bubbles aren't available - Android degrades bubble notifications to
 * plain ones automatically, we just avoid crashing on old APIs while
 * building the metadata.
 */
object NudgeNotifier {

    fun postNudge(context: Context, item: BacklogItem, eventId: Long, watchedPackage: String, nudgeCopy: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notificationId = NOTIFICATION_ID_BASE + (item.id % 1000).toInt()

        val shortcutId = "nudge_${item.id}"
        val person = Person.Builder()
            .setName("Backlog Nudge")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_notification))
            .setImportant(true)
            .build()

        publishConversationShortcut(context, shortcutId, person)

        val contentPendingIntent = bubbleContentIntent(context, item.id, eventId, watchedPackage, notificationId)

        val builder = NotificationCompat.Builder(context, BacklogNudgeApp.CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(itemHeadline(item))
            .setContentText(nudgeCopy)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudgeCopy))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShortcutId(shortcutId)
            .setContentIntent(contentPendingIntent)
            // Capped at 3 quick actions - most launchers only render three on a collapsed
            // heads-up/notification-shade view regardless of how many are added. "Not today"
            // is still reachable one tap deeper, inside the expanded bubble/sheet.
            .addAction(action(context, "Leave", R.drawable.ic_action_close, NudgeActionReceiver.ACTION_LEAVE, item.id, eventId))
            .addAction(action(context, "Done", R.drawable.ic_action_check, NudgeActionReceiver.ACTION_DONE, item.id, eventId))
            .addAction(action(context, "Snooze 30m", R.drawable.ic_action_snooze, NudgeActionReceiver.ACTION_SNOOZE, item.id, eventId))

        val bubblesReady = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bubblesAllowed(context, nm)
        if (bubblesReady) {
            runCatching {
                val bubbleIntent = PendingIntent.getActivity(
                    context, notificationId,
                    Intent(context, NudgeBubbleActivity::class.java).apply {
                        putExtra(NudgeBubbleActivity.EXTRA_ITEM_ID, item.id)
                        putExtra(NudgeBubbleActivity.EXTRA_EVENT_ID, eventId)
                    },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val bubbleIcon = Icon.createWithResource(context, R.drawable.ic_notification)
                val metadata = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, IconCompat.createFromIcon(context, bubbleIcon)!!)
                    .setDesiredHeight(600)
                    // Pop straight open as a floating window over whatever app is in front
                    // (Instagram keeps running underneath - this doesn't close it) instead
                    // of waiting for the user to notice and tap a small chat-head icon.
                    .setAutoExpandBubble(true)
                    .setSuppressNotification(true)
                    .build()
                builder.setBubbleMetadata(metadata)
            }
        } else {
            // No bubble support/permission on this device - the next best thing to
            // "pop open automatically" is a full-screen-intent notification, which
            // brings the nudge screen to the front immediately without a tap. This
            // does bring our app forward (Instagram goes to the background, not
            // closed - it's still there on the back stack), which is the accepted
            // fallback when an overlay-on-top-of-Instagram isn't available.
            runCatching {
                builder.setFullScreenIntent(contentPendingIntent, true)
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancel(context: Context, itemId: Long) {
        val notificationId = NOTIFICATION_ID_BASE + (itemId % 1000).toInt()
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    // The title names the item so it's identifiable at a glance; the body (templateCopy /
    // the LLM-generated line) carries the "why now" framing - keeping these distinct
    // avoids the title and body both asking the same "got N min?" question.
    private fun itemHeadline(item: BacklogItem): String = item.title

    private fun bubblesAllowed(context: Context, nm: NotificationManager): Boolean =
        runCatching { nm.areBubblesAllowed() }.getOrDefault(false)

    private fun publishConversationShortcut(context: Context, shortcutId: String, person: Person) {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setLongLived(true)
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setShortLabel("Backlog Nudge")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_notification))
                .setPerson(person)
                .setCategories(setOf("android.shortcut.conversation"))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    private fun bubbleContentIntent(
        context: Context,
        itemId: Long,
        eventId: Long,
        watchedPackage: String,
        notificationId: Int
    ): PendingIntent = PendingIntent.getActivity(
        context, notificationId,
        Intent(context, NudgeBubbleActivity::class.java).apply {
            putExtra(NudgeBubbleActivity.EXTRA_ITEM_ID, itemId)
            putExtra(NudgeBubbleActivity.EXTRA_EVENT_ID, eventId)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(
        context: Context,
        label: String,
        icon: Int,
        action: String,
        itemId: Long,
        eventId: Long
    ): NotificationCompat.Action {
        val intent = Intent(context, NudgeActionReceiver::class.java).apply {
            this.action = action
            putExtra(NudgeActionReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(NudgeActionReceiver.EXTRA_EVENT_ID, eventId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (action + itemId).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private const val NOTIFICATION_ID_BASE = 5000
}

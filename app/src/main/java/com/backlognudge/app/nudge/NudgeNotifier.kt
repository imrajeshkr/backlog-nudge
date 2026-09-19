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
            .addAction(action(context, "Do it now", R.drawable.ic_action_check, NudgeActionReceiver.ACTION_DONE, item.id, eventId))
            .addAction(action(context, "Snooze 30m", R.drawable.ic_action_snooze, NudgeActionReceiver.ACTION_SNOOZE, item.id, eventId))
            .addAction(action(context, "Not today", R.drawable.ic_action_close, NudgeActionReceiver.ACTION_DISMISS, item.id, eventId))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bubblesAllowed(context, nm)) {
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
                    .setAutoExpandBubble(false)
                    .setSuppressNotification(false)
                    .build()
                builder.setBubbleMetadata(metadata)
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancel(context: Context, itemId: Long) {
        val notificationId = NOTIFICATION_ID_BASE + (itemId % 1000).toInt()
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun itemHeadline(item: BacklogItem): String =
        "Got ${item.estimatedMinutes.label}?"

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

package io.github.qymartin.reinminder.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.qymartin.reinminder.R
import android.app.Notification
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Lang
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.data.TopicStore
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.logic.isOverdue
import io.github.qymartin.reinminder.logic.pinnedAt
import io.github.qymartin.reinminder.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime

object Notifier {
    private const val CH_REMIND = "reminders"
    private const val CH_ALARM = "alarms"
    const val EXTRA_OPEN_ID = "openEventId"
    private const val TEST_ID = 1

    /** Channel names follow the app language; calling again after a language change renames them. */
    fun ensureChannels(context: Context) {
        val c = Lang.wrap(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, c.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            },
        )
        // 1.0/1.1 had a separate full-screen "alarms" channel; alarm-style now repeats normal reminders.
        nm.deleteNotificationChannel(CH_ALARM)
    }

    fun notificationId(id: Long): Int = (id xor (id ushr 32)).toInt().let { if (it == TEST_ID) it + 1 else it }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun actionIntent(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, notificationId(id),
            Intent(context, ActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("reinminder://action/$action/$id")
                putExtra(Alarms.EXTRA_ID, id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** A reminder is due: notify with sound. Important tasks whose time has come stay pinned. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, e: Event) {
        if (!canNotify(context)) return
        ensureChannels(context)
        // A minute of slack: an alarm can fire a moment before the event time it was set for.
        val pinned = e.pinnedAt(LocalDateTime.now().plusMinutes(1))
        NotificationManagerCompat.from(context).notify(notificationId(e.id), build(context, e, pinned, silent = false))
    }

    /**
     * Pins every important task that is overdue or happening now (one notification each, silent),
     * and unpins the ones that were completed, deleted or moved later. Runs after every change.
     */
    @SuppressLint("MissingPermission")
    fun syncPinned(context: Context) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val now = LocalDateTime.now().plusMinutes(1)
        val events = EventStore.get(context).all()
        val due = events.filter { it.pinnedAt(now) }
        val dueIds = due.map { notificationId(it.id) }.toSet()
        val active = try { nm.activeNotifications.toList() } catch (e: Exception) { emptyList() }
        // Unpin what no longer qualifies.
        active.filter { (it.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 && it.id !in dueIds }
            .forEach { nm.cancel(it.id) }
        if (due.isEmpty()) return
        ensureChannels(context)
        val pinnedNow = active.filter { (it.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 }.map { it.id }.toSet()
        due.forEach { e ->
            val nid = notificationId(e.id)
            // Post if missing, or turn a plain (dismissible) reminder into a pinned one. Silent either way.
            if (nid !in pinnedNow) NotificationManagerCompat.from(context).notify(nid, build(context, e, pinned = true, silent = true))
        }
    }

    private fun build(context: Context, e: Event, pinned: Boolean, silent: Boolean): Notification {
        val c = Lang.wrap(context)
        val en = Lang.isEnglish(context)
        val nid = notificationId(e.id)
        val now = LocalDateTime.now()
        val text = buildList {
            if (e.isOverdue(now)) add(c.getString(R.string.overdue))
            add(Fmt.moment(e, LocalDate.now(), en))
            TopicStore.get(context).name(e.topic)?.let { add(it) }
        }.joinToString(" · ")

        val open = PendingIntent.getActivity(
            c, nid,
            Intent(c, MainActivity::class.java).apply {
                data = Uri.parse("reinminder://open/${e.id}")
                putExtra(EXTRA_OPEN_ID, e.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(c, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setColor(0xFFA8C7FA.toInt())
            .setContentTitle((if (e.important) "★ " else "") + e.title.ifBlank { c.getString(R.string.untitled) })
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setSilent(silent)
        if (e.repeat == Repeat.NONE) {
            b.addAction(0, c.getString(R.string.action_archive), actionIntent(c, ActionReceiver.ACTION_ARCHIVE, e.id))
            b.addAction(0, c.getString(R.string.action_delete_done), actionIntent(c, ActionReceiver.ACTION_DELETE_DONE, e.id))
        } else {
            b.addAction(0, c.getString(R.string.action_done_once), actionIntent(c, ActionReceiver.ACTION_ARCHIVE, e.id))
        }
        if (pinned) {
            // "Clear all" never removes it. On Android 14+ an unlocked swipe can, so it is put back.
            b.setOngoing(true).setAutoCancel(false)
                .setDeleteIntent(actionIntent(c, ActionReceiver.ACTION_REPIN, e.id))
        } else {
            val snooze = Prefs(context).snoozeMinutes
            b.setAutoCancel(true)
            b.addAction(0, c.getString(R.string.action_snooze, snooze), actionIntent(c, ActionReceiver.ACTION_SNOOZE, e.id))
        }
        if (e.notes.isNotBlank()) {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + e.notes))
        }
        return b.build()
    }

    fun cancel(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(id))
    }

    @SuppressLint("MissingPermission")
    fun test(context: Context): Boolean {
        if (!canNotify(context)) return false
        ensureChannels(context)
        val c = Lang.wrap(context)
        val n = NotificationCompat.Builder(c, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setColor(0xFFA8C7FA.toInt())
            .setContentTitle(c.getString(R.string.test_title))
            .setContentText(c.getString(R.string.test_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(TEST_ID, n)
        return true
    }
}

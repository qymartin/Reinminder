package io.github.qymartin.reinminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Prefs

/** A reminder alarm went off: show its notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        val e = EventStore.get(context).get(id) ?: return
        if (e.done) return
        Notifier.show(context, e)
        // Alarm-style events need their next 10-minute reminder set.
        if (e.alarmStyle) Alarms.rescheduleAll(context)
    }
}

/** Buttons on a reminder notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        if (id < 0) return
        when (intent.action) {
            ACTION_ARCHIVE -> Repo.archive(context, id)
            ACTION_DELETE_DONE -> Repo.completeAndDelete(context, id)
            ACTION_SNOOZE -> {
                Notifier.cancel(context, id)
                Alarms.snooze(context, id, Prefs(context).snoozeMinutes)
            }
        }
    }

    companion object {
        const val ACTION_ARCHIVE = "io.github.qymartin.reinminder.ARCHIVE"
        const val ACTION_DELETE_DONE = "io.github.qymartin.reinminder.DELETE_DONE"
        const val ACTION_SNOOZE = "io.github.qymartin.reinminder.SNOOZE"
    }
}

/**
 * Alarms are cleared by a reboot and can go stale after an update or a clock change: set them again.
 * Also woken just after midnight, when rescheduling clears yesterday's "完成并删除" events.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.rescheduleAll(context)
    }
}

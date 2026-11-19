package io.github.qymartin.reinminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.logic.reminderMoments
import io.github.qymartin.reinminder.ui.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns every upcoming reminder into an exact alarm. Called after any change, after boot, after an
 * app update and when the time or time zone changes. Each alarm is keyed "<eventId>/<index>".
 */
object Alarms {
    const val EXTRA_ID = "eventId"
    private const val ACTION_REMIND = "io.github.qymartin.reinminder.REMIND"

    private fun intent(context: Context, key: String, id: Long) =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("reinminder://reminder/$key")
            putExtra(EXTRA_ID, id)
        }

    private fun pending(context: Context, key: String, id: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, key.hashCode(), intent(context, key, id), flags or PendingIntent.FLAG_IMMUTABLE)

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    @Synchronized
    fun rescheduleAll(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = Prefs(ctx)
        for (key in prefs.scheduled) {
            val id = key.substringBefore('/').toLongOrNull() ?: continue
            pending(ctx, key, id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        }
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val keys = HashSet<String>()
        for (e in EventStore.get(ctx).all()) {
            if (e.done) continue
            e.reminderMoments().forEachIndexed { i, t ->
                if (t.isAfter(now)) {
                    val key = "${e.id}/$i"
                    val p = pending(ctx, key, e.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEachIndexed
                    set(ctx, am, t.atZone(zone).toInstant().toEpochMilli(), p, e.alarmStyle)
                    keys += key
                }
            }
        }
        prefs.scheduled = keys
    }

    /** Rings again in [minutes]. Kept apart from the regular alarms so a reschedule does not drop it. */
    fun snooze(context: Context, id: Long, minutes: Int) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pending(ctx, "$id/snooze", id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val alarmStyle = EventStore.get(ctx).get(id)?.alarmStyle == true
        set(ctx, am, System.currentTimeMillis() + minutes * 60_000L, p, alarmStyle)
    }

    fun cancelSnooze(context: Context, id: Long) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        pending(ctx, "$id/snooze", id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    private fun set(context: Context, am: AlarmManager, at: Long, p: PendingIntent, alarmStyle: Boolean) {
        val exact = canExact(context)
        when {
            alarmStyle && exact -> {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p)
            }
            exact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }
}

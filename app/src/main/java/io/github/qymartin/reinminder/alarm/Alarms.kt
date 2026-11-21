package io.github.qymartin.reinminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.logic.moment
import io.github.qymartin.reinminder.logic.nextNag
import io.github.qymartin.reinminder.logic.reminderMoments
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns every upcoming reminder into an exact alarm. Called after any change, after boot, after an
 * app update and when the time or time zone changes. Each alarm is keyed "<eventId>/<index>".
 */
object Alarms {
    const val EXTRA_ID = "eventId"
    private const val ACTION_REMIND = "io.github.qymartin.reinminder.REMIND"

    const val EXTRA_PIN = "pinOnly"

    private fun intent(context: Context, key: String, id: Long, pinOnly: Boolean) =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("reinminder://reminder/$key")
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_PIN, pinOnly)
        }

    private fun pending(context: Context, key: String, id: Long, flags: Int, pinOnly: Boolean = false): PendingIntent? =
        PendingIntent.getBroadcast(context, key.hashCode(), intent(context, key, id, pinOnly), flags or PendingIntent.FLAG_IMMUTABLE)

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    @Synchronized
    fun rescheduleAll(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = Prefs(ctx)
        val store = EventStore.get(ctx)
        store.purge(LocalDate.now())
        for (key in prefs.scheduled) {
            val id = key.substringBefore('/').toLongOrNull() ?: continue
            pending(ctx, key, id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        }
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val keys = HashSet<String>()
        for (e in store.all()) {
            if (e.done) continue
            val moments = e.reminderMoments().filter { it.isAfter(now) }
            moments.forEachIndexed { i, t ->
                val key = "${e.id}/$i"
                val p = pending(ctx, key, e.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEachIndexed
                set(ctx, am, t.atZone(zone).toInstant().toEpochMilli(), p)
                keys += key
            }
            // Alarm-style: from the event time, again every 10 minutes. Only the next one is set;
            // when it fires, this runs again and sets the one after.
            val nag = e.nextNag(now)
            if (nag != null && nag !in moments) {
                val key = "${e.id}/nag"
                pending(ctx, key, e.id, PendingIntent.FLAG_UPDATE_CURRENT)?.let {
                    set(ctx, am, nag.atZone(zone).toInstant().toEpochMilli(), it)
                    keys += key
                }
            }
            // Important: pin it to the lock screen when its time comes, even if its only reminder was earlier.
            val at = e.moment()
            if (e.important && at.isAfter(now) && at !in moments && at != nag) {
                val key = "${e.id}/pin"
                pending(ctx, key, e.id, PendingIntent.FLAG_UPDATE_CURRENT, pinOnly = true)?.let {
                    set(ctx, am, at.atZone(zone).toInstant().toEpochMilli(), it)
                    keys += key
                }
            }
        }
        prefs.scheduled = keys
        scheduleMidnight(ctx, am)
        Notifier.syncPinned(ctx)
    }

    /** Wakes [BootReceiver] just after midnight, which clears yesterday's "完成并删除" events. */
    private fun scheduleMidnight(ctx: Context, am: AlarmManager) {
        val at = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val p = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, BootReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, p)
    }

    const val ACTION_MIDNIGHT = "io.github.qymartin.reinminder.MIDNIGHT"

    /** Rings again in [minutes]. Kept apart from the regular alarms so a reschedule does not drop it. */
    fun snooze(context: Context, id: Long, minutes: Int) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pending(ctx, "$id/snooze", id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        set(ctx, am, System.currentTimeMillis() + minutes * 60_000L, p)
    }

    fun cancelSnooze(context: Context, id: Long) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        pending(ctx, "$id/snooze", id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    private fun set(context: Context, am: AlarmManager, at: Long, p: PendingIntent) {
        if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }
}

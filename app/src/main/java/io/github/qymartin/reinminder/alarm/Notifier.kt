package io.github.qymartin.reinminder.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.qymartin.reinminder.R
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Lang
import io.github.qymartin.reinminder.data.Prefs
import io.github.qymartin.reinminder.data.TopicStore
import io.github.qymartin.reinminder.logic.Fmt
import io.github.qymartin.reinminder.ui.AlarmActivity
import io.github.qymartin.reinminder.ui.MainActivity
import java.time.LocalDate

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
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, c.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600)
            },
        )
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

    @SuppressLint("MissingPermission")
    fun show(context: Context, e: Event) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val c = Lang.wrap(context)
        val en = Lang.isEnglish(context)
        val nid = notificationId(e.id)
        val text = buildList {
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
        val snooze = Prefs(context).snoozeMinutes
        val b = NotificationCompat.Builder(c, if (e.alarmStyle) CH_ALARM else CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setColor(0xFFA8C7FA.toInt())
            .setContentTitle(e.title.ifBlank { c.getString(R.string.untitled) })
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (e.alarmStyle) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, c.getString(R.string.action_done), actionIntent(c, ActionReceiver.ACTION_DONE, e.id))
            .addAction(0, c.getString(R.string.action_snooze, snooze), actionIntent(c, ActionReceiver.ACTION_SNOOZE, e.id))
        if (e.notes.isNotBlank()) {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + e.notes))
        }
        if (e.alarmStyle) {
            val full = PendingIntent.getActivity(
                c, nid,
                Intent(c, AlarmActivity::class.java).apply {
                    data = Uri.parse("reinminder://alarm/${e.id}")
                    putExtra(Alarms.EXTRA_ID, e.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.setFullScreenIntent(full, true)
            b.setOngoing(true)
            b.setAutoCancel(false)
        }
        val n = b.build()
        if (e.alarmStyle) n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(c).notify(nid, n)
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

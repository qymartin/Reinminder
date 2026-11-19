package io.github.qymartin.reinminder.alarm

import android.content.Context
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.logic.completed
import io.github.qymartin.reinminder.logic.postponed
import io.github.qymartin.reinminder.logic.postponedToTomorrow
import io.github.qymartin.reinminder.logic.uncompleted
import java.time.LocalDate
import java.time.LocalDateTime

/** Every change to events goes through here, so alarms and notifications always follow the data. */
object Repo {
    private fun store(c: Context) = EventStore.get(c)

    private fun after(c: Context, id: Long) {
        Alarms.cancelSnooze(c, id)
        Notifier.cancel(c, id)
        Alarms.rescheduleAll(c)
    }

    fun save(c: Context, e: Event) {
        store(c).upsert(e)
        Alarms.rescheduleAll(c)
    }

    fun delete(c: Context, id: Long) {
        store(c).delete(id)
        after(c, id)
    }

    fun complete(c: Context, id: Long) {
        store(c).update(id) { it.completed(LocalDate.now()) }
        after(c, id)
    }

    fun uncomplete(c: Context, id: Long) {
        store(c).update(id) { it.uncompleted() }
        Alarms.rescheduleAll(c)
    }

    fun postponeToTomorrow(c: Context, id: Long) {
        store(c).update(id) { it.postponedToTomorrow(LocalDateTime.now()) }
        after(c, id)
    }

    fun postpone(c: Context, id: Long, to: LocalDateTime) {
        store(c).update(id) { it.postponed(to) }
        after(c, id)
    }

    fun replaceAll(c: Context, list: List<Event>) {
        val old = store(c).all()
        store(c).replaceAll(list)
        old.forEach { Alarms.cancelSnooze(c, it.id); Notifier.cancel(c, it.id) }
        Alarms.rescheduleAll(c)
    }
}

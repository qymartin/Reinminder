package io.github.qymartin.reinminder.alarm

import android.content.Context
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.EventStore
import io.github.qymartin.reinminder.data.Topic
import io.github.qymartin.reinminder.data.TopicStore
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.advanced
import io.github.qymartin.reinminder.logic.archived
import io.github.qymartin.reinminder.logic.doneAndDeleted
import io.github.qymartin.reinminder.logic.occurrenceRecord
import io.github.qymartin.reinminder.logic.postponed
import io.github.qymartin.reinminder.logic.postponedToTomorrow
import io.github.qymartin.reinminder.logic.restored
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

    /** Deletes a task and its subtasks. */
    fun delete(c: Context, id: Long) {
        val children = store(c).all().filter { it.parentId == id }.map { it.id }
        (children + id).forEach { store(c).delete(it); Alarms.cancelSnooze(c, it); Notifier.cancel(c, it) }
        Alarms.rescheduleAll(c)
    }

    /**
     * "完成并存档". A repeating event completes only this occurrence: a completed copy goes to 已完成
     * and the series moves to its next date.
     */
    fun archive(c: Context, id: Long) {
        val today = LocalDate.now()
        val e = store(c).get(id) ?: return
        if (e.repeat == Repeat.NONE) {
            store(c).upsert(e.archived(today))
        } else {
            store(c).upsert(e.occurrenceRecord(store(c).newId(), today))
            store(c).upsert(e.advanced(today))
        }
        after(c, id)
    }

    /** "完成并删除": struck through today, deleted after midnight. Repeating events: same as [archive]. */
    fun completeAndDelete(c: Context, id: Long) {
        val e = store(c).get(id) ?: return
        if (e.repeat != Repeat.NONE) return archive(c, id)
        store(c).upsert(e.doneAndDeleted(LocalDate.now()))
        after(c, id)
    }

    /** Back to a to-do: from 已完成, or undoing "完成并删除" the same day. */
    fun restore(c: Context, id: Long) {
        store(c).update(id) { it.restored() }
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

    /** Restore a backup. [topics] is null for 1.0 backups: the current topics are kept. */
    fun replaceAll(c: Context, list: List<Event>, topics: List<Topic>?) {
        val old = store(c).all()
        if (topics != null) TopicStore.get(c).replaceAll(topics)
        store(c).replaceAll(list)
        old.forEach { Alarms.cancelSnooze(c, it.id); Notifier.cancel(c, it.id) }
        Alarms.rescheduleAll(c)
    }

    /** Delete a topic; its events stay, without a topic. */
    fun deleteTopic(c: Context, id: String) {
        TopicStore.get(c).remove(id)
        val events = store(c)
        events.replaceAll(events.all().map { if (it.topic == id) it.copy(topic = null) else it })
    }
}

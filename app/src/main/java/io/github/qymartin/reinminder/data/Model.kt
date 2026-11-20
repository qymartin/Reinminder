package io.github.qymartin.reinminder.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Repeat(val key: String) {
    NONE("none"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly"), YEARLY("yearly");

    companion object {
        fun of(key: String?): Repeat = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** A user-defined label ("主题"). [id] never changes, so renaming keeps events attached. */
data class Topic(val id: String, val name: String)

/**
 * One event. A repeating event is one record: [date] is its current occurrence and moves forward
 * when that occurrence is completed. [anchor] is the series' first date, so monthly/yearly dates
 * never drift (Jan 31 -> Feb 28 -> Mar 31).
 */
data class Event(
    val id: Long,
    val title: String,
    /** Id of a [Topic], or null. */
    val topic: String? = null,
    val anchor: LocalDate,
    val date: LocalDate,
    /** null = all day. */
    val time: LocalTime? = null,
    val repeat: Repeat = Repeat.NONE,
    /** Every [interval] days/weeks/months/years ("每 2 周"). */
    val interval: Int = 1,
    /** Minutes before the reference time (event time, or 09:00 on the day for all-day events). */
    val reminders: List<Int> = emptyList(),
    val important: Boolean = false,
    /** Alarm-style: after the event time, remind again every 10 minutes until done or moved. */
    val alarmStyle: Boolean = false,
    val notes: String = "",
    /**
     * Completed (non-repeating events only; a repeating event moves to its next date and leaves a
     * completed one-off copy behind). Completed events are archived ("已完成") unless [purge].
     */
    val done: Boolean = false,
    /** "完成并删除": shown struck through on the day it was completed, deleted after midnight. */
    val purge: Boolean = false,
    /** Day it was completed. */
    val lastDoneOn: LocalDate? = null,
    /** Time of the completed occurrence (kept for old data). */
    val lastDoneTime: LocalTime? = null,
    /** This occurrence only, moved to another moment ("postpone" or "only this time" edits). */
    val postponedTo: LocalDateTime? = null,
    val createdAt: Long = 0,
)

const val MAX_REMINDERS = 8

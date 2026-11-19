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
    /** Minutes before the reference time (event time, or 09:00 on the day for all-day events). */
    val reminders: List<Int> = emptyList(),
    val important: Boolean = false,
    /** Full-screen, ringing until turned off. */
    val alarmStyle: Boolean = false,
    val notes: String = "",
    /** Only used by non-repeating events. */
    val done: Boolean = false,
    /** Day the user last completed an occurrence, so it still shows (ticked) on Today. */
    val lastDoneOn: LocalDate? = null,
    /** Date the completed occurrence belonged to, with its time, for the ticked row. */
    val lastDoneTime: LocalTime? = null,
    /** This occurrence only, moved to another moment ("postpone" or "only this time" edits). */
    val postponedTo: LocalDateTime? = null,
    val createdAt: Long = 0,
)

const val MAX_REMINDERS = 8

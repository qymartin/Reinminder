package io.github.qymartin.reinminder.logic

import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Reminders of all-day events are counted from this time on the day. */
val ALL_DAY_REF: LocalTime = LocalTime.of(9, 0)

/** The day this occurrence happens on (after any postponing). */
fun Event.shownDate(): LocalDate = postponedTo?.toLocalDate() ?: date

/** The time this occurrence happens at, or null for all day. */
fun Event.shownTime(): LocalTime? = postponedTo?.toLocalTime() ?: time

fun Event.isAllDay(): Boolean = shownTime() == null

/** The moment used for sorting and for "overdue". */
fun Event.moment(): LocalDateTime = postponedTo ?: date.atTime(time ?: ALL_DAY_REF)

/** Moments at which this occurrence notifies, sorted, without duplicates. */
fun Event.reminderMoments(): List<LocalDateTime> {
    if (done) return emptyList()
    postponedTo?.let { return listOf(it) }
    val ref = date.atTime(time ?: ALL_DAY_REF)
    return reminders.map { ref.minusMinutes(it.toLong()) }.distinct().sortedWith(compareBy<LocalDateTime> { it })
}

fun Event.isOverdue(now: LocalDateTime): Boolean {
    if (done) return false
    return if (isAllDay()) shownDate().isBefore(now.toLocalDate()) else moment().isBefore(now)
}

/** True when this occurrence was completed today and should still show, ticked, on Today. */
fun Event.completedToday(today: LocalDate): Boolean = lastDoneOn == today

/** Series date number [n] (0 = anchor). */
fun seriesDate(anchor: LocalDate, repeat: Repeat, n: Long): LocalDate = when (repeat) {
    Repeat.NONE -> anchor
    Repeat.DAILY -> anchor.plusDays(n)
    Repeat.WEEKLY -> anchor.plusWeeks(n)
    Repeat.MONTHLY -> anchor.plusMonths(n)
    Repeat.YEARLY -> anchor.plusYears(n)
}

/** First series date strictly after [after], or null for a non-repeating event. */
fun nextSeriesDate(anchor: LocalDate, repeat: Repeat, after: LocalDate): LocalDate? {
    if (repeat == Repeat.NONE) return null
    if (after.isBefore(anchor)) return anchor
    val unit = when (repeat) {
        Repeat.DAILY -> ChronoUnit.DAYS
        Repeat.WEEKLY -> ChronoUnit.WEEKS
        Repeat.MONTHLY -> ChronoUnit.MONTHS
        Repeat.YEARLY -> ChronoUnit.YEARS
        Repeat.NONE -> return null
    }
    var n = maxOf(0L, unit.between(anchor, after) - 1)
    while (true) {
        val d = seriesDate(anchor, repeat, n)
        if (d.isAfter(after)) return d
        n++
    }
}

/**
 * Completes the current occurrence. A repeating event moves to its next date on or after today,
 * so finishing an overdue one does not leave a trail of missed copies.
 */
fun Event.completed(today: LocalDate): Event {
    val doneTime = shownTime()
    if (repeat == Repeat.NONE) {
        return copy(done = true, lastDoneOn = today, lastDoneTime = doneTime)
    }
    val from = later(date, today.minusDays(1))
    val next = nextSeriesDate(anchor, repeat, from) ?: date
    return copy(date = next, postponedTo = null, lastDoneOn = today, lastDoneTime = doneTime)
}

/** Undo a completion made today (only for non-repeating events; repeating ones keep moving). */
fun Event.uncompleted(): Event = copy(done = false, lastDoneOn = null, lastDoneTime = null)

fun Event.postponed(to: LocalDateTime): Event = copy(postponedTo = to, done = false)

/** Postpone to the same time tomorrow (all-day events: tomorrow at 09:00). */
fun Event.postponedToTomorrow(now: LocalDateTime): Event {
    val base = later(shownDate(), now.toLocalDate()).plusDays(1)
    return postponed(base.atTime(shownTime() ?: ALL_DAY_REF))
}

/** Dates on which this event occurs between [from] and [to] (inclusive), starting at its current occurrence. */
fun Event.occurrences(from: LocalDate, to: LocalDate, limit: Int = 400): List<LocalDate> {
    if (done) return emptyList()
    val out = ArrayList<LocalDate>()
    val first = shownDate()
    if (!first.isBefore(from) && !first.isAfter(to)) out.add(first)
    if (repeat == Repeat.NONE) return out
    var d: LocalDate? = nextSeriesDate(anchor, repeat, later(date, from.minusDays(1)))
    while (d != null && !d.isAfter(to) && out.size < limit) {
        if (d != first && !d.isBefore(from)) out.add(d)
        d = nextSeriesDate(anchor, repeat, d)
    }
    return out.sortedBy { it.toEpochDay() }
}

/** Events ordered the way lists show them: overdue/earlier first, all-day before timed on the same day. */
val eventOrder: Comparator<Event> = compareBy<Event>({ it.shownDate() }, { it.shownTime() != null }, { it.shownTime() }, { it.id })

/** The later of two dates (kotlin's maxOf does not accept java.time dates). */
fun later(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b

package io.github.qymartin.reinminder.logic

import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import java.time.Duration
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

/** Series date number [n] (0 = anchor), every [interval] units. */
fun seriesDate(anchor: LocalDate, repeat: Repeat, n: Long, interval: Int = 1): LocalDate {
    val k = n * interval
    return when (repeat) {
        Repeat.NONE -> anchor
        Repeat.DAILY -> anchor.plusDays(k)
        Repeat.WEEKLY -> anchor.plusWeeks(k)
        Repeat.MONTHLY -> anchor.plusMonths(k)
        Repeat.YEARLY -> anchor.plusYears(k)
    }
}

/** First series date strictly after [after], or null for a non-repeating event. */
fun nextSeriesDate(anchor: LocalDate, repeat: Repeat, after: LocalDate, interval: Int = 1): LocalDate? {
    if (repeat == Repeat.NONE) return null
    if (after.isBefore(anchor)) return anchor
    val step = interval.coerceAtLeast(1)
    val unit = when (repeat) {
        Repeat.DAILY -> ChronoUnit.DAYS
        Repeat.WEEKLY -> ChronoUnit.WEEKS
        Repeat.MONTHLY -> ChronoUnit.MONTHS
        Repeat.YEARLY -> ChronoUnit.YEARS
        Repeat.NONE -> return null
    }
    var n = maxOf(0L, unit.between(anchor, after) / step - 1)
    while (true) {
        val d = seriesDate(anchor, repeat, n, step)
        if (d.isAfter(after)) return d
        n++
    }
}

fun Event.nextSeriesDate(after: LocalDate): LocalDate? = nextSeriesDate(anchor, repeat, after, interval)

/** "完成并存档" (non-repeating): kept and listed under 已完成. */
fun Event.archived(today: LocalDate): Event =
    copy(done = true, purge = false, lastDoneOn = today, lastDoneTime = shownTime())

/** "完成并删除" (non-repeating): struck through today, deleted after midnight. */
fun Event.doneAndDeleted(today: LocalDate): Event =
    copy(done = true, purge = true, lastDoneOn = today, lastDoneTime = shownTime())

/** Back to a to-do (from 已完成, or undoing a "完成并删除" the same day). */
fun Event.restored(): Event = copy(done = false, purge = false, lastDoneOn = null, lastDoneTime = null)

/**
 * A repeating event, after its current occurrence is completed: it moves to its next date on or after
 * today, so finishing an overdue one does not leave a trail of missed copies.
 */
fun Event.advanced(today: LocalDate): Event {
    val from = later(date, today.minusDays(1))
    return copy(date = nextSeriesDate(from) ?: date, postponedTo = null)
}

/** Alarm-style: the next "again" reminder (event time, then every 10 minutes) after [now]. */
fun Event.nextNag(now: LocalDateTime): LocalDateTime? {
    if (!alarmStyle || done) return null
    val m = moment()
    if (m.isAfter(now)) return m
    val steps = Duration.between(m, now).toMinutes() / NAG_MINUTES + 1
    return m.plusMinutes(steps * NAG_MINUTES)
}

const val NAG_MINUTES = 10L

/** When this occurrence first reminds: its earliest reminder, or the event time when moved ("postponed"). */
fun Event.firstReminder(): LocalDateTime {
    postponedTo?.let { return it }
    val lead = (reminders.maxOrNull() ?: 0).coerceAtLeast(0)
    return date.atTime(time ?: ALL_DAY_REF).minusMinutes(lead.toLong())
}

/**
 * Important, not yet done, and its first reminder has come: kept as a pinned notification that
 * "clear all" can't remove, until it is completed or moved later.
 */
fun Event.pinnedAt(now: LocalDateTime): Boolean = important && !done && !firstReminder().isAfter(now)

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
    var d: LocalDate? = nextSeriesDate(later(date, from.minusDays(1)))
    while (d != null && !d.isAfter(to) && out.size < limit) {
        if (d != first && !d.isBefore(from)) out.add(d)
        d = nextSeriesDate(d)
    }
    return out.sortedBy { it.toEpochDay() }
}

/** Events ordered the way lists show them: earlier days first; on a day, important first, then by time. */
val eventOrder: Comparator<Event> = compareBy<Event>({ it.shownDate() }, { !it.important }, { it.shownTime() != null }, { it.shownTime() }, { it.id })

/** The later of two dates (kotlin's maxOf does not accept java.time dates). */
fun later(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b

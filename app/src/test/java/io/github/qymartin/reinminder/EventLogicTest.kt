package io.github.qymartin.reinminder

import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.completed
import io.github.qymartin.reinminder.logic.isOverdue
import io.github.qymartin.reinminder.logic.nextSeriesDate
import io.github.qymartin.reinminder.logic.occurrences
import io.github.qymartin.reinminder.logic.postponedToTomorrow
import io.github.qymartin.reinminder.logic.reminderMoments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EventLogicTest {
    private fun ev(date: LocalDate, time: LocalTime?, repeat: Repeat = Repeat.NONE, reminders: List<Int> = emptyList()) =
        Event(id = 1, title = "x", anchor = date, date = date, time = time, repeat = repeat, reminders = reminders)

    @Test fun birthdayReminders() {
        // Birthday 11/5 at 17:00, reminded 1 day before and at the time.
        val e = ev(LocalDate.of(2026, 11, 5), LocalTime.of(17, 0), Repeat.YEARLY, listOf(1440, 0))
        assertEquals(
            listOf(LocalDateTime.of(2026, 11, 4, 17, 0), LocalDateTime.of(2026, 11, 5, 17, 0)),
            e.reminderMoments(),
        )
        val next = e.completed(LocalDate.of(2026, 11, 5))
        assertEquals(LocalDate.of(2027, 11, 5), next.date)
        assertEquals(LocalDate.of(2026, 11, 5), next.lastDoneOn)
        assertFalse(next.done)
    }

    @Test fun allDayRemindersCountFromNine() {
        val e = ev(LocalDate.of(2026, 11, 18), null, reminders = listOf(780, 0))
        assertEquals(
            listOf(LocalDateTime.of(2026, 11, 17, 20, 0), LocalDateTime.of(2026, 11, 18, 9, 0)),
            e.reminderMoments(),
        )
    }

    @Test fun monthlyDoesNotDrift() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), nextSeriesDate(anchor, Repeat.MONTHLY, anchor))
        assertEquals(LocalDate.of(2026, 3, 31), nextSeriesDate(anchor, Repeat.MONTHLY, LocalDate.of(2026, 2, 28)))
        val leap = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), nextSeriesDate(leap, Repeat.YEARLY, leap))
        assertEquals(LocalDate.of(2028, 2, 29), nextSeriesDate(leap, Repeat.YEARLY, LocalDate.of(2027, 2, 28)))
    }

    @Test fun completingOverdueDailyJumpsToToday() {
        val e = ev(LocalDate.of(2026, 11, 10), LocalTime.of(8, 0), Repeat.DAILY)
        assertEquals(LocalDate.of(2026, 11, 18), e.completed(LocalDate.of(2026, 11, 18)).date)
    }

    @Test fun overdueAndPostpone() {
        val now = LocalDateTime.of(2026, 11, 18, 19, 56)
        val bill = ev(LocalDate.of(2026, 11, 17), LocalTime.of(18, 0), Repeat.MONTHLY)
        assertTrue(bill.isOverdue(now))
        val moved = bill.postponedToTomorrow(now)
        assertEquals(LocalDateTime.of(2026, 11, 19, 18, 0), moved.postponedTo)
        assertFalse(moved.isOverdue(now))
        assertEquals(listOf(LocalDateTime.of(2026, 11, 19, 18, 0)), moved.reminderMoments())
        // Completing the postponed occurrence returns to the series: next is Dec 17.
        assertEquals(LocalDate.of(2026, 12, 17), moved.completed(LocalDate.of(2026, 11, 19)).date)
        val allDayToday = ev(LocalDate.of(2026, 11, 18), null)
        assertFalse(allDayToday.isOverdue(now))
    }

    @Test fun occurrencesInMonth() {
        val e = ev(LocalDate.of(2026, 11, 3), null, Repeat.WEEKLY)
        assertEquals(
            listOf(3, 10, 17, 24).map { LocalDate.of(2026, 11, it) },
            e.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
        )
        val once = ev(LocalDate.of(2026, 11, 21), LocalTime.of(15, 0))
        assertEquals(listOf(LocalDate.of(2026, 11, 21)), once.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        assertTrue(once.copy(done = true).occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).isEmpty())
    }
}

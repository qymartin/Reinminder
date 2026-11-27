package io.github.qymartin.reinminder

import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.advanced
import io.github.qymartin.reinminder.logic.archived
import io.github.qymartin.reinminder.logic.doneAndDeleted
import io.github.qymartin.reinminder.logic.nextNag
import io.github.qymartin.reinminder.logic.pinnedAt
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
        val day = LocalDate.of(2026, 11, 5)
        val next = e.advanced(day)
        assertEquals(LocalDate.of(2027, 11, 5), next.date)
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
        assertEquals(LocalDate.of(2026, 11, 18), e.advanced(LocalDate.of(2026, 11, 18)).date)
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
        assertEquals(LocalDate.of(2026, 12, 17), moved.advanced(LocalDate.of(2026, 11, 19)).date)
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

    @Test fun customIntervals() {
        val everyTwoWeeks = ev(LocalDate.of(2026, 11, 6), LocalTime.of(9, 0), Repeat.WEEKLY).copy(interval = 2)
        assertEquals(
            listOf(6, 20).map { LocalDate.of(2026, 11, it) },
            everyTwoWeeks.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
        )
        assertEquals(LocalDate.of(2026, 11, 20), everyTwoWeeks.advanced(LocalDate.of(2026, 11, 6)).date)
        val everyThreeMonths = ev(LocalDate.of(2026, 1, 31), null, Repeat.MONTHLY).copy(interval = 3)
        assertEquals(LocalDate.of(2026, 4, 30), everyThreeMonths.advanced(LocalDate.of(2026, 1, 31)).date)
    }

    @Test fun archiveAndDelete() {
        val today = LocalDate.of(2026, 11, 19)
        val e = ev(today, LocalTime.of(15, 0))
        val kept = e.archived(today)
        assertTrue(kept.done); assertFalse(kept.purge); assertEquals(today, kept.lastDoneOn)
        val gone = e.doneAndDeleted(today)
        assertTrue(gone.done); assertTrue(gone.purge)
        assertTrue(kept.reminderMoments().isEmpty())
    }

    @Test fun alarmStyleRepeatsEveryTenMinutes() {
        val e = ev(LocalDate.of(2026, 11, 19), LocalTime.of(15, 0)).copy(alarmStyle = true)
        assertEquals(LocalDateTime.of(2026, 11, 19, 15, 0), e.nextNag(LocalDateTime.of(2026, 11, 19, 14, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 19, 15, 10), e.nextNag(LocalDateTime.of(2026, 11, 19, 15, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 19, 15, 30), e.nextNag(LocalDateTime.of(2026, 11, 19, 15, 23)))
        assertEquals(null, e.copy(alarmStyle = false).nextNag(LocalDateTime.of(2026, 11, 19, 15, 23)))
        assertEquals(null, e.archived(LocalDate.of(2026, 11, 19)).nextNag(LocalDateTime.of(2026, 11, 19, 15, 23)))
    }

    @Test fun pinnedWhenImportantAndDue() {
        val e = ev(LocalDate.of(2026, 11, 21), LocalTime.of(13, 0)).copy(important = true)
        assertFalse(e.pinnedAt(LocalDateTime.of(2026, 11, 21, 12, 59)))
        assertTrue(e.pinnedAt(LocalDateTime.of(2026, 11, 21, 13, 0)))
        assertTrue(e.pinnedAt(LocalDateTime.of(2026, 11, 25, 9, 0)))
        assertFalse(e.copy(important = false).pinnedAt(LocalDateTime.of(2026, 11, 21, 14, 0)))
        assertFalse(e.archived(LocalDate.of(2026, 11, 21)).pinnedAt(LocalDateTime.of(2026, 11, 21, 14, 0)))
        // Reminded 1 hour before: pinned from then.
        val early = e.copy(reminders = listOf(60))
        assertFalse(early.pinnedAt(LocalDateTime.of(2026, 11, 21, 11, 59)))
        assertTrue(early.pinnedAt(LocalDateTime.of(2026, 11, 21, 12, 0)))
        // Postponed to tomorrow: unpinned until then.
        val moved = e.copy(postponedTo = LocalDateTime.of(2026, 11, 22, 13, 0))
        assertFalse(moved.pinnedAt(LocalDateTime.of(2026, 11, 21, 14, 0)))
    }
}

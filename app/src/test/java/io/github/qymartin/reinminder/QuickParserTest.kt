package io.github.qymartin.reinminder

import io.github.qymartin.reinminder.data.Repeat
import io.github.qymartin.reinminder.logic.QuickParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickParserTest {
    // Sunday 2026-11-01, 10:00 (the birthday use case is logged on 11/1).
    private val nov1 = LocalDateTime.of(2026, 11, 1, 10, 0)
    // Wednesday 2026-11-18, 19:56.
    private val nov18 = LocalDateTime.of(2026, 11, 18, 19, 56)

    @Test fun birthdayChinese() {
        val p = QuickParser.parse("11/5 下午5点 小王生日 #纪念日", nov1)
        assertEquals("小王生日", p.title)
        assertEquals(LocalDate.of(2026, 11, 5), p.date)
        assertEquals(LocalTime.of(17, 0), p.time)
        assertEquals("纪念日", p.topic)
        assertEquals(Repeat.YEARLY, p.repeat)
    }

    @Test fun weekdayAndAfternoon() {
        val p = QuickParser.parse("周五下午3点 体检 #健康", nov18)
        assertEquals("体检", p.title)
        assertEquals(LocalDate.of(2026, 11, 20), p.date)
        assertEquals(LocalTime.of(15, 0), p.time)
        assertEquals("健康", p.topic)
        assertNull(p.repeat)
    }

    @Test fun english() {
        val p = QuickParser.parse("Fri 3pm checkup #health", nov18)
        assertEquals("checkup", p.title)
        assertEquals(LocalDate.of(2026, 11, 20), p.date)
        assertEquals(LocalTime.of(15, 0), p.time)
        assertEquals("health", p.topic)
    }

    @Test fun tomorrowHalfPastAndImportant() {
        val p = QuickParser.parse("明天早上8点半 交水费 !", nov18)
        assertEquals("交水费", p.title)
        assertEquals(LocalDate.of(2026, 11, 19), p.date)
        assertEquals(LocalTime.of(8, 30), p.time)
        assertTrue(p.important)
    }

    @Test fun everyMonday() {
        val p = QuickParser.parse("每周一 9:30 周会 #工作", nov18)
        assertEquals("周会", p.title)
        assertEquals(Repeat.WEEKLY, p.repeat)
        assertEquals(LocalDate.of(2026, 11, 23), p.date)
        assertEquals(LocalTime.of(9, 30), p.time)
        assertEquals("工作", p.topic)
    }

    @Test fun monthlyDayNumber() {
        val p = QuickParser.parse("每月17号 交物业费 #账单", nov18)
        assertEquals("交物业费", p.title)
        assertEquals(Repeat.MONTHLY, p.repeat)
        assertEquals(LocalDate.of(2026, 12, 17), p.date)
        assertNull(p.time)
    }

    @Test fun passedDateRollsToNextYear() {
        val p = QuickParser.parse("3月8日 妈妈生日", nov18)
        assertEquals(LocalDate.of(2027, 3, 8), p.date)
        assertEquals(Repeat.YEARLY, p.repeat)
        assertNull(p.topic)
    }

    @Test fun tonight() {
        val p = QuickParser.parse("今晚8点 看电影", nov18)
        assertEquals("看电影", p.title)
        assertEquals(LocalDate.of(2026, 11, 18), p.date)
        assertEquals(LocalTime.of(20, 0), p.time)
    }

    @Test fun timeOnlyAlreadyPassedMeansTomorrow() {
        val p = QuickParser.parse("9:00 跑步", nov18)
        assertEquals(LocalDate.of(2026, 11, 19), p.date)
        assertEquals(LocalTime.of(9, 0), p.time)
    }

    @Test fun englishMonthNameAndNextWeekday() {
        val a = QuickParser.parse("Nov 21 dentist", nov18)
        assertEquals("dentist", a.title)
        assertEquals(LocalDate.of(2026, 11, 21), a.date)
        assertNull(a.time)
        val b = QuickParser.parse("next fri 10:30am car service", nov18)
        assertEquals("car service", b.title)
        assertEquals(LocalDate.of(2026, 11, 27), b.date)
        assertEquals(LocalTime.of(10, 30), b.time)
    }

    @Test fun inDaysAndPlainTitle() {
        val a = QuickParser.parse("3天后 还书", nov18)
        assertEquals(LocalDate.of(2026, 11, 21), a.date)
        assertEquals("还书", a.title)
        val b = QuickParser.parse("买牛奶", nov18)
        assertEquals("买牛奶", b.title)
        assertNull(b.date)
        assertNull(b.time)
    }

    @Test fun chineseNumbers() {
        assertEquals(12, QuickParser.number("十二"))
        assertEquals(10, QuickParser.number("十"))
        assertEquals(2, QuickParser.number("两"))
        assertEquals(20, QuickParser.number("二十"))
        val p = QuickParser.parse("下周三 上午十点 面试", nov18)
        assertEquals(LocalDate.of(2026, 11, 25), p.date)
        assertEquals(LocalTime.of(10, 0), p.time)
        assertEquals("面试", p.title)
    }

    @Test fun everyNUnits() {
        val a = QuickParser.parse("每2周 打扫 ", nov18)
        assertEquals(Repeat.WEEKLY, a.repeat)
        assertEquals(2, a.interval)
        assertEquals("打扫", a.title)
        val b = QuickParser.parse("every 3 months haircut", nov18)
        assertEquals(Repeat.MONTHLY, b.repeat)
        assertEquals(3, b.interval)
        assertEquals("haircut", b.title)
        val c = QuickParser.parse("每周五 周会", nov18)
        assertEquals(Repeat.WEEKLY, c.repeat)
        assertEquals(1, c.interval)
    }
}

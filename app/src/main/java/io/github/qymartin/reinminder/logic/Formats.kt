package io.github.qymartin.reinminder.logic

import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Dates, times and phrases that depend on values, in Chinese or English. Static UI text lives in strings.xml. */
object Fmt {
    private val zhWeek = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val enWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val enMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val enTime = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val zhTime = DateTimeFormatter.ofPattern("HH:mm")

    fun weekday(d: LocalDate, en: Boolean): String = (if (en) enWeek else zhWeek)[d.dayOfWeek.value - 1]

    /** Weekday headers for a month grid, Monday first. */
    fun weekHeaders(en: Boolean): List<String> =
        if (en) enWeek else listOf("一", "二", "三", "四", "五", "六", "日")

    fun date(d: LocalDate, en: Boolean, today: LocalDate? = null): String {
        val withYear = today != null && d.year != today.year
        return if (en) {
            "${weekday(d, true)}, ${enMonths[d.monthValue - 1]} ${d.dayOfMonth}" + if (withYear) ", ${d.year}" else ""
        } else {
            (if (withYear) "${d.year}年" else "") + "${d.monthValue}月${d.dayOfMonth}日 ${weekday(d, false)}"
        }
    }

    /** Month and day only: "11月5日" / "Nov 5". */
    fun monthDay(d: LocalDate, en: Boolean): String =
        if (en) "${enMonths[d.monthValue - 1]} ${d.dayOfMonth}" else "${d.monthValue}月${d.dayOfMonth}日"

    fun relativeDay(d: LocalDate, today: LocalDate, en: Boolean): String? = when (ChronoUnit.DAYS.between(today, d)) {
        0L -> if (en) "Today" else "今天"
        1L -> if (en) "Tomorrow" else "明天"
        2L -> if (en) null else "后天"
        -1L -> if (en) "Yesterday" else "昨天"
        else -> null
    }

    /** "今天" / "明天" / "11月21日 周五". */
    fun day(d: LocalDate, today: LocalDate, en: Boolean): String = relativeDay(d, today, en) ?: date(d, en, today)

    fun time(t: LocalTime, en: Boolean): String = if (en) t.format(enTime) else t.format(zhTime)

    fun allDay(en: Boolean) = if (en) "All day" else "全天"

    /** Time column of a row: "15:00" / "全天". */
    fun timeOrAllDay(t: LocalTime?, en: Boolean): String = if (t == null) allDay(en) else time(t, en)

    /** "明天 15:00" / "Tomorrow · 3:00 PM" / "11月21日 周五 · 全天". */
    fun moment(e: Event, today: LocalDate, en: Boolean): String {
        val d = day(e.shownDate(), today, en)
        val t = e.shownTime()
        return if (t == null) d else if (en) "$d · ${time(t, true)}" else "$d ${time(t, false)}"
    }

    fun repeat(r: Repeat, anchor: LocalDate, en: Boolean): String = when (r) {
        Repeat.NONE -> if (en) "Never" else "不重复"
        Repeat.DAILY -> if (en) "Daily" else "每天"
        Repeat.WEEKLY -> if (en) "Weekly on ${weekday(anchor, true)}" else "每${weekday(anchor, false)}"
        Repeat.MONTHLY -> if (en) "Monthly on the ${ordinal(anchor.dayOfMonth)}" else "每月 ${anchor.dayOfMonth} 日"
        Repeat.YEARLY -> if (en) "Yearly on ${monthDay(anchor, true)}" else "每年 ${monthDay(anchor, false)}"
    }

    /** Short repeat word for row subtitles. */
    fun repeatShort(r: Repeat, en: Boolean): String? = when (r) {
        Repeat.NONE -> null
        Repeat.DAILY -> if (en) "Daily" else "每天"
        Repeat.WEEKLY -> if (en) "Weekly" else "每周"
        Repeat.MONTHLY -> if (en) "Monthly" else "每月"
        Repeat.YEARLY -> if (en) "Yearly" else "每年"
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /**
     * A reminder offset in words. Timed events: "提前 1 小时" / "1 hour before".
     * All-day events count from 09:00 on the day: "前一天 20:00" / "Day before, 8:00 PM".
     */
    fun reminder(minutes: Int, allDay: Boolean, en: Boolean): String {
        if (allDay) {
            val ref = LocalDate.of(2000, 1, 10).atTime(ALL_DAY_REF)
            val at = ref.minusMinutes(minutes.toLong())
            val days = ChronoUnit.DAYS.between(at.toLocalDate(), ref.toLocalDate())
            val t = time(at.toLocalTime(), en)
            return when {
                days <= 0L -> if (en) "On the day, $t" else "当天 $t"
                days == 1L -> if (en) "Day before, $t" else "前一天 $t"
                else -> if (en) "$days days before, $t" else "提前 $days 天 $t"
            }
        }
        if (minutes == 0) return if (en) "At time" else "准时"
        // Up to a day is said in hours ("提前 24 小时"); longer in days.
        val d = if (minutes <= 1440 && minutes % 60 == 0) 0 else minutes / 1440
        val h = (minutes - d * 1440) / 60
        val m = minutes % 60
        return if (en) {
            val parts = buildList {
                if (d > 0) add(if (d == 1) "1 day" else "$d days")
                if (h > 0) add(if (h == 1) "1 hour" else "$h hours")
                if (m > 0) add("$m min")
            }
            parts.joinToString(" ") + " before"
        } else {
            val parts = buildList {
                if (d > 0) add("$d 天")
                if (h > 0) add("$h 小时")
                if (m > 0) add("$m 分钟")
            }
            "提前 " + parts.joinToString(" ")
        }
    }

    /** Several reminders: "提前 1 天、准时" / "1 day before + at time". */
    fun reminders(list: List<Int>, allDay: Boolean, en: Boolean): String? {
        if (list.isEmpty()) return null
        val sorted = list.sortedDescending()
        return if (en) {
            sorted.joinToString(" + ") { reminder(it, allDay, true) }.replace(" + At time", " + at time")
        } else {
            sorted.joinToString("、") { reminder(it, allDay, false) }
        }
    }

    fun month(ym: YearMonth, en: Boolean): String =
        if (en) "${listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[ym.monthValue - 1]} ${ym.year}"
        else "${ym.year}年${ym.monthValue}月"

    /** Subtitle of a row: "健康 · 每年 · 提前 1 小时". */
    fun meta(e: Event, en: Boolean, extra: String? = null, topic: String? = null): String {
        val parts = buildList {
            extra?.let { add(it) }
            topic?.let { add(it) }
            repeatShort(e.repeat, en)?.let { add(it) }
            if (e.postponedTo == null) reminders(e.reminders, e.time == null, en)?.let { add(it) }
            else add(if (en) "Postponed" else "已推迟")
        }
        return parts.joinToString(" · ")
    }
}

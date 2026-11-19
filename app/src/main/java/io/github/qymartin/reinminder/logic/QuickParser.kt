package io.github.qymartin.reinminder.logic

import io.github.qymartin.reinminder.data.Category
import io.github.qymartin.reinminder.data.Repeat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What one line of quick-add text was understood as. Anything not found is null. */
data class Parsed(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val repeat: Repeat? = null,
    val category: Category? = null,
    val important: Boolean = false,
)

/**
 * Reads one line in Chinese or English, e.g. "11/5 下午5点 小王生日 #纪念日" or "Fri 3pm checkup #health".
 * Pure function of the text and the current time, so it is unit-tested.
 */
object QuickParser {

    private val I = setOf(RegexOption.IGNORE_CASE)

    private val tagRe = Regex("""[#＃]([^\s#＃]+)""")
    private val bangRe = Regex("""(^|\s)[!！]+(?=\s|$)""")

    // Repeat
    private val zhEveryWeekdayRe = Regex("""每(?=(?:周|星期|礼拜)[一二三四五六日天1-7])""")
    private val zhRepeatRe = Regex("""每(天|日|周|星期|礼拜|月|年)""")
    private val enEveryWeekdayRe = Regex("""\bevery\s+(?=(?:mon|tue|wed|thu|fri|sat|sun))""", I)
    private val enRepeatRe = Regex("""\b(?:every\s+(day|week|month|year)|(daily|weekly|monthly|yearly|annually))\b""", I)

    // Relative days
    private val zhRelRe = Regex("""(大后天|后天|明天|明日|今天|今日|今晚|今早|明早|明晚)""")
    private val zhInDaysRe = Regex("""(\d{1,3})\s*天[以之]?后""")
    private val enRelRe = Regex("""\b(day after tomorrow|tomorrow|tmrw|tmr|today|tonight)\b""", I)
    private val enInDaysRe = Regex("""\bin\s+(\d{1,3})\s+days?\b""", I)

    // Dates
    private val ymdRe = Regex("""(?<!\d)(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})[日号]?(?!\d)""")
    private val zhMdRe = Regex("""(?<![\d/])(\d{1,2})月(\d{1,2})[日号]?""")
    private val slashMdRe = Regex("""(?<![\d/:.])(\d{1,2})/(\d{1,2})(?![\d/])""")
    private const val MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private val enMonthDayRe = Regex("""\b($MONTHS)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b""", I)
    private val enDayMonthRe = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($MONTHS)[a-z]*\b""", I)
    private val zhDayRe = Regex("""(?<![\d月])(\d{1,2})[号日](?!\d)""")
    private val enDayRe = Regex("""\bthe\s+(\d{1,2})(?:st|nd|rd|th)\b""", I)

    // Weekdays
    private val zhWeekdayRe = Regex("""(下下|下个?|这个?|本)?(?:周|星期|礼拜)([一二三四五六日天1-7])""")
    private val enWeekdayRe = Regex(
        """\b(next\s+|this\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|tues|thurs|thur|mon|tue|wed|thu|fri|sat|sun)\b""", I,
    )

    // Times
    private const val PERIOD = "上午|早上|早晨|中午|下午|傍晚|晚上|夜里|凌晨"
    private val colonTimeRe = Regex("""($PERIOD)?\s*(?<!\d)([01]?\d|2[0-3])[:：]([0-5]\d)(?!\d)\s*(am|pm|a\.m\.|p\.m\.)?""", I)
    private val zhTimeRe = Regex(
        """($PERIOD)?\s*([0-9]{1,2}|[零一二两三四五六七八九十]{1,3})\s*[点點时](?:\s*(半|一刻|三刻|[0-9]{1,2}|[零一二三四五六七八九十]{1,3})\s*分?)?""",
    )
    private val enAmPmRe = Regex("""\b(?:at\s+)?(\d{1,2})(?::([0-5]\d))?\s*(am|pm|a\.m\.|p\.m\.)(?=\W|$)""", I)
    private val enAtRe = Regex("""\bat\s+(\d{1,2})\b(?![:\d])""", I)
    private val enNoonRe = Regex("""\b(noon|midday)\b""", I)

    private val anniversaryWords = Regex("""生日|纪念日|birthday|anniversary""", I)

    fun parse(input: String, now: LocalDateTime): Parsed {
        val today = now.toLocalDate()
        var text = " $input "
        var category: Category? = null
        var important = false
        var repeat: Repeat? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        var evening = false
        var morning = false

        fun cut(m: MatchResult) {
            text = text.replaceRange(m.range, " ")
        }

        // Tags: #category, #重要 / #important
        while (true) {
            val m = tagRe.findAll(text).firstOrNull { tagMeaning(it.groupValues[1]) != null } ?: break
            when (val meaning = tagMeaning(m.groupValues[1])) {
                is Category -> if (category == null) category = meaning
                IMPORTANT -> important = true
            }
            cut(m)
        }
        bangRe.find(text)?.let { important = true; cut(it) }

        // Repeat
        zhEveryWeekdayRe.find(text)?.let { repeat = Repeat.WEEKLY; cut(it) }
        enEveryWeekdayRe.find(text)?.let { repeat = Repeat.WEEKLY; cut(it) }
        zhRepeatRe.find(text)?.let { m ->
            repeat = repeat ?: when (m.groupValues[1]) {
                "天", "日" -> Repeat.DAILY
                "周", "星期", "礼拜" -> Repeat.WEEKLY
                "月" -> Repeat.MONTHLY
                else -> Repeat.YEARLY
            }
            // Keep the "月" of "每月5号"? No: "每月" is the whole repeat word; the day follows as "5号".
            cut(m)
        }
        enRepeatRe.find(text)?.let { m ->
            val w = (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase()
            repeat = repeat ?: when (w) {
                "day", "daily" -> Repeat.DAILY
                "week", "weekly" -> Repeat.WEEKLY
                "month", "monthly" -> Repeat.MONTHLY
                else -> Repeat.YEARLY
            }
            cut(m)
        }

        // Relative days
        zhRelRe.find(text)?.let { m ->
            val w = m.groupValues[1]
            date = today.plusDays(
                when (w) {
                    "大后天" -> 3; "后天" -> 2; "明天", "明日", "明早", "明晚" -> 1; else -> 0
                }.toLong(),
            )
            if (w == "今晚" || w == "明晚") evening = true
            if (w == "今早" || w == "明早") morning = true
            cut(m)
        }
        if (date == null) enRelRe.find(text)?.let { m ->
            val w = m.groupValues[1].lowercase()
            date = today.plusDays(
                when (w) {
                    "day after tomorrow" -> 2; "tomorrow", "tmrw", "tmr" -> 1; else -> 0
                }.toLong(),
            )
            if (w == "tonight") evening = true
            cut(m)
        }
        if (date == null) zhInDaysRe.find(text)?.let { m ->
            date = today.plusDays(m.groupValues[1].toLong()); cut(m)
        }
        if (date == null) enInDaysRe.find(text)?.let { m ->
            date = today.plusDays(m.groupValues[1].toLong()); cut(m)
        }

        // Absolute dates
        if (date == null) ymdRe.find(text)?.let { m ->
            val d = dateOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            if (d != null) { date = d; cut(m) }
        }
        if (date == null) zhMdRe.find(text)?.let { m ->
            val d = upcoming(today, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            if (d != null) { date = d; cut(m) }
        }
        if (date == null) slashMdRe.find(text)?.let { m ->
            val d = upcoming(today, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            if (d != null) { date = d; cut(m) }
        }
        if (date == null) enMonthDayRe.find(text)?.let { m ->
            val d = upcoming(today, monthOf(m.groupValues[1]), m.groupValues[2].toInt())
            if (d != null) { date = d; cut(m) }
        }
        if (date == null) enDayMonthRe.find(text)?.let { m ->
            val d = upcoming(today, monthOf(m.groupValues[2]), m.groupValues[1].toInt())
            if (d != null) { date = d; cut(m) }
        }

        // Weekdays
        if (date == null) zhWeekdayRe.find(text)?.let { m ->
            val dow = zhWeekday(m.groupValues[2])
            val weeks = when (m.groupValues[1]) {
                "下下" -> 2; "下", "下个" -> 1; else -> 0
            }
            date = weekdayDate(today, dow, weeks)
            cut(m)
        }
        if (date == null) enWeekdayRe.find(text)?.let { m ->
            val dow = enWeekday(m.groupValues[2].lowercase())
            val weeks = if (m.groupValues[1].trim().lowercase() == "next") 1 else 0
            date = weekdayDate(today, dow, weeks)
            cut(m)
        }

        // Times
        colonTimeRe.find(text)?.let { m ->
            val h = adjustHour(m.groupValues[2].toInt(), m.groupValues[1], m.groupValues[4], evening, morning, guess = false)
            time = LocalTime.of(h, m.groupValues[3].toInt())
            cut(m)
        }
        if (time == null) enAmPmRe.find(text)?.let { m ->
            val raw = m.groupValues[1].toInt()
            if (raw in 1..12) {
                val h = adjustHour(raw, "", m.groupValues[3], evening, morning, guess = false)
                time = LocalTime.of(h, m.groupValues[2].ifEmpty { "0" }.toInt())
                cut(m)
            }
        }
        if (time == null) zhTimeRe.find(text)?.let { m ->
            val raw = number(m.groupValues[2])
            val minute = when (val mm = m.groupValues[3]) {
                "" -> 0
                "半" -> 30
                "一刻" -> 15
                "三刻" -> 45
                else -> number(mm) ?: 0
            }
            if (raw != null && raw in 0..24 && minute in 0..59) {
                val h = adjustHour(raw % 24, m.groupValues[1], "", evening, morning, guess = true)
                time = LocalTime.of(h, minute)
                cut(m)
            }
        }
        if (time == null) enAtRe.find(text)?.let { m ->
            val raw = m.groupValues[1].toInt()
            if (raw in 0..23) {
                time = LocalTime.of(adjustHour(raw, "", "", evening, morning, guess = true), 0)
                cut(m)
            }
        }
        if (time == null) enNoonRe.find(text)?.let { m ->
            time = LocalTime.NOON
            cut(m)
        }
        if (time == null && evening) time = LocalTime.of(20, 0)

        // A day number alone ("5号", "the 5th"): this month, or next month if it has passed.
        if (date == null) zhDayRe.find(text)?.let { m ->
            val d = dayOfMonth(today, m.groupValues[1].toInt())
            if (d != null) { date = d; cut(m) }
        }
        if (date == null) enDayRe.find(text)?.let { m ->
            val d = dayOfMonth(today, m.groupValues[1].toInt())
            if (d != null) { date = d; cut(m) }
        }

        // Only a time: today if it is still ahead, else tomorrow.
        val t = time
        if (date == null && t != null) {
            date = if (t.isAfter(now.toLocalTime())) today else today.plusDays(1)
        }

        val title = cleanTitle(text)
        if (anniversaryWords.containsMatchIn(title)) {
            if (category == null) category = Category.ANNIVERSARY
            if (repeat == null) repeat = Repeat.YEARLY
        }
        return Parsed(title, date, time, repeat, category, important)
    }

    private object IMPORTANT

    private fun tagMeaning(tag: String): Any? = when (tag.lowercase().trimEnd(',', '，', '.', '。')) {
        "工作", "work" -> Category.WORK
        "生活", "life" -> Category.LIFE
        "健康", "health" -> Category.HEALTH
        "账单", "bills", "bill" -> Category.BILLS
        "纪念日", "生日", "anniversary", "birthday" -> Category.ANNIVERSARY
        "个人", "personal" -> Category.PERSONAL
        "重要", "important" -> IMPORTANT
        else -> null
    }

    private fun adjustHour(h: Int, period: String, ampm: String, evening: Boolean, morning: Boolean, guess: Boolean): Int {
        val ap = ampm.lowercase().replace(".", "")
        return when {
            ap == "pm" -> if (h < 12) h + 12 else h
            ap == "am" -> if (h == 12) 0 else h
            period in setOf("下午", "傍晚", "晚上", "夜里") -> if (h < 12) h + 12 else h
            period == "中午" -> if (h in 1..10) h + 12 else h
            period.isNotEmpty() -> h
            evening -> if (h < 12) h + 12 else h
            morning -> h
            // "3点" with no hint: reminders at 3 am are rare, so 1-6 means afternoon.
            guess && h in 1..6 -> h + 12
            else -> h
        }
    }

    private fun dateOf(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    /** Month/day without a year: this year, or next year if the day has passed. */
    private fun upcoming(today: LocalDate, m: Int, d: Int): LocalDate? {
        val thisYear = dateOf(today.year, m, d) ?: (if (m == 2 && d == 29) dateOf(today.year, 2, 28) else null) ?: return null
        return if (thisYear.isBefore(today)) dateOf(today.year + 1, m, d) ?: thisYear.plusYears(1) else thisYear
    }

    private fun dayOfMonth(today: LocalDate, d: Int): LocalDate? {
        if (d !in 1..31) return null
        var month = today.withDayOfMonth(1)
        repeat(13) {
            val c = dateOf(month.year, month.monthValue, d)
            if (c != null && !c.isBefore(today)) return c
            month = month.plusMonths(1)
        }
        return null
    }

    private fun monthOf(s: String): Int = MONTHS.split('|').indexOf(s.lowercase().take(3)) + 1

    private fun zhWeekday(c: String): Int = when (c) {
        "一", "1" -> 1; "二", "2" -> 2; "三", "3" -> 3; "四", "4" -> 4
        "五", "5" -> 5; "六", "6" -> 6; else -> 7
    }

    private fun enWeekday(s: String): Int = when (s.take(3)) {
        "mon" -> 1; "tue" -> 2; "wed" -> 3; "thu" -> 4; "fri" -> 5; "sat" -> 6; else -> 7
    }

    /** weeks = 0: the next such day on or after today; 1: that day in next week (weeks start on Monday). */
    private fun weekdayDate(today: LocalDate, dow: Int, weeks: Int): LocalDate {
        if (weeks == 0) {
            val ahead = (dow - today.dayOfWeek.value + 7) % 7
            return today.plusDays(ahead.toLong())
        }
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday.plusWeeks(weeks.toLong()).plusDays((dow - 1).toLong())
    }

    /** "3", "十二", "两", "二十" ... */
    fun number(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        val digit = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (s.isEmpty()) return null
        val ten = s.indexOf('十')
        if (ten < 0) return if (s.length == 1) digit[s[0]] else null
        val tens = if (ten == 0) 1 else digit[s[0]] ?: return null
        val ones = if (ten == s.length - 1) 0 else digit[s[s.length - 1]] ?: return null
        return tens * 10 + ones
    }

    private val edgeChars = " \t,，.。、:：;；-–—~～"
    private val leadingWords = Regex("""^(?:(?:at|on|in|by)\s+|[的在于])+""", I)
    private val trailingWords = Regex("""(?:\s+(?:at|on|in|by))+$""", I)

    private fun cleanTitle(s: String): String {
        var t = s.replace(Regex("""\s+"""), " ").trim().trim { it in edgeChars }
        t = t.replace(leadingWords, "").replace(trailingWords, "").trim().trim { it in edgeChars }
        return t
    }
}

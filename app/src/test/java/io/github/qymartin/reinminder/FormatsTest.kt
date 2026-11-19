package io.github.qymartin.reinminder

import io.github.qymartin.reinminder.logic.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    @Test fun reminderLabels() {
        assertEquals("提前 24 小时", Fmt.reminder(1440, false, false))
        assertEquals("提前 12 小时", Fmt.reminder(720, false, false))
        assertEquals("提前 1 小时", Fmt.reminder(60, false, false))
        assertEquals("提前 2 天", Fmt.reminder(2880, false, false))
        assertEquals("提前 1 小时 30 分钟", Fmt.reminder(90, false, false))
        assertEquals("24 hours before", Fmt.reminder(1440, false, true))
        assertEquals("1 hour before", Fmt.reminder(60, false, true))
    }
}

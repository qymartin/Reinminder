package io.github.qymartin.reinminder

import io.github.qymartin.reinminder.backup.Backup
import io.github.qymartin.reinminder.backup.WrongPasswordException
import io.github.qymartin.reinminder.data.Topic
import io.github.qymartin.reinminder.data.Event
import io.github.qymartin.reinminder.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BackupTest {
    private val events = listOf(
        Event(
            id = 7, title = "小王生日", topic = "anniversary",
            anchor = LocalDate.of(2026, 11, 5), date = LocalDate.of(2026, 11, 5), time = LocalTime.of(17, 0),
            repeat = Repeat.YEARLY, reminders = listOf(1440, 0), important = true, notes = "蛋糕",
            postponedTo = LocalDateTime.of(2026, 11, 5, 18, 30), createdAt = 123,
        ),
        Event(id = 8, title = "Pick up package", anchor = LocalDate.of(2026, 11, 18), date = LocalDate.of(2026, 11, 18)),
    )

    private val topics = listOf(Topic("anniversary", "纪念日"), Topic("t1", "猫"))

    @Test fun plainRoundTrip() {
        val back = Backup.fromJson(Backup.toJson(events, topics, 1))
        assertEquals(events, back.events)
        assertEquals(topics, back.topics)
    }

    @Test fun readsVersion1Backup() {
        // 1.0 wrote "category" and had no topic list.
        val v1 = """{"app":"reinminder","format":1,"events":[{"id":3,"title":"体检","category":"health","date":"2026-11-21","time":"15:00","reminders":[60]}]}"""
        val back = Backup.fromJson(v1)
        assertEquals("health", back.events[0].topic)
        assertEquals(null, back.topics)
    }

    @Test fun encryptedRoundTripAndWrongPassword() {
        val plain = Backup.toJson(events, topics, 1).toByteArray()
        val enc = Backup.encrypt(plain, "correct horse".toCharArray())
        assertTrue(Backup.isEncrypted(enc))
        assertFalse(String(enc, Charsets.ISO_8859_1).contains("小王"))
        val back = Backup.decrypt(enc, "correct horse".toCharArray())
        assertEquals(events, Backup.fromJson(String(back)).events)
        try {
            Backup.decrypt(enc, "wrong".toCharArray())
            fail("expected wrong password")
        } catch (e: WrongPasswordException) {
            // expected
        }
    }
}

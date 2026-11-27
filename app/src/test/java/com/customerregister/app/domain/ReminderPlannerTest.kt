package com.customerregister.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    private val config = ReminderConfig(setOf(7, 3, 1), includeExpiryDay = true, time = LocalTime.of(9, 0))
    private val expiry = LocalDate.of(2026, 12, 5)
    private fun at(date: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(date, LocalTime.of(h, m))

    @Test fun offsetsSortedAndIncludeExpiryDay() {
        assertEquals(listOf(7, 3, 1, 0), ReminderPlanner.offsets(config))
        assertEquals(listOf(7, 3, 1), ReminderPlanner.offsets(config.copy(includeExpiryDay = false)))
        assertEquals(listOf(14, 7, 3, 1, 0), ReminderPlanner.offsets(config.copy(daysBefore = setOf(1, 3, 7, 14))))
    }

    @Test fun allFourDefaultRemindersFireOnTheirDayAtTheConfiguredTime() {
        for (d in listOf(7, 3, 1, 0)) {
            val day = expiry.minusDays(d.toLong())
            assertEquals("offset $d at 09:00", d, ReminderPlanner.dueOffset(expiry, at(day, 9), config))
            assertEquals("offset $d at 18:00", d, ReminderPlanner.dueOffset(expiry, at(day, 18), config))
        }
    }

    @Test fun reminderWaitsForConfiguredTimeOnItsDay() {
        assertNull(ReminderPlanner.dueOffset(expiry, at(expiry.minusDays(3), 8, 59), config))
    }

    @Test fun nothingDueFarFromExpiry() {
        assertNull(ReminderPlanner.dueOffset(expiry, at(expiry.minusDays(30), 12), config))
        assertNull(ReminderPlanner.dueOffset(expiry, at(expiry.minusDays(8), 12), config))
    }

    @Test fun missedReminderIsCaughtUpWithTheClosestOffset() {
        // Phone was off on day -7; switched on at day -5 at 07:00 -> the 7-day reminder fires now.
        assertEquals(7, ReminderPlanner.dueOffset(expiry, at(expiry.minusDays(5), 7), config))
        // Switched on at day -2 -> the 3-day reminder (not the 7-day one).
        assertEquals(3, ReminderPlanner.dueOffset(expiry, at(expiry.minusDays(2), 7), config))
    }

    @Test fun nothingAfterExpiration() {
        assertNull(ReminderPlanner.dueOffset(expiry, at(expiry.plusDays(1), 10), config))
    }

    @Test fun expiryDayCanBeDisabled() {
        val c = config.copy(includeExpiryDay = false)
        // On the expiry day the closest offset is 1 (already sent the day before) - planner returns 1,
        // and the database duplicate guard prevents a second notification.
        assertEquals(1, ReminderPlanner.dueOffset(expiry, at(expiry, 10), c))
        assertNull(ReminderPlanner.nextTrigger(expiry, at(expiry.minusDays(1), 10), c))
    }

    @Test fun nextTriggerWalksThroughTheSchedule() {
        var now = at(expiry.minusDays(20), 12)
        val fired = mutableListOf<LocalDateTime>()
        while (true) {
            val next = ReminderPlanner.nextTrigger(expiry, now, config) ?: break
            fired += next
            now = next
        }
        assertEquals(
            listOf(7, 3, 1, 0).map { at(expiry.minusDays(it.toLong()), 9) },
            fired
        )
    }

    @Test fun nextTriggerIsStrictlyInTheFuture() {
        val exactly = at(expiry.minusDays(3), 9)
        assertEquals(at(expiry.minusDays(1), 9), ReminderPlanner.nextTrigger(expiry, exactly, config))
    }

    @Test fun duplicateKeyIsStablePerExpiryAndOffset() {
        // Simulates the UNIQUE (customer, expiry, offset) guard: repeated checks on the same day
        // must keep returning the same offset so the DB insert is ignored.
        val seen = mutableSetOf<Pair<LocalDate, Int>>()
        var posted = 0
        listOf(9, 10, 13, 17, 23).forEach { h ->
            val off = ReminderPlanner.dueOffset(expiry, at(expiry.minusDays(3), h), config)!!
            if (seen.add(expiry to off)) posted++
        }
        assertEquals(1, posted)
    }

    @Test fun renewalChangesTheKeySoNewScheduleStarts() {
        val newExpiry = expiry.plusMonths(1)
        val now = at(expiry.minusDays(3), 10)
        assertNull(ReminderPlanner.dueOffset(newExpiry, now, config))
        assertEquals(at(newExpiry.minusDays(7), 9), ReminderPlanner.nextTrigger(newExpiry, now, config))
    }
}

package com.customerregister.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reminder rules.
 * @param daysBefore e.g. {7, 3, 1} — reminders N days before expiry.
 * @param includeExpiryDay also remind on the expiration date itself (offset 0).
 * @param time local time of day at which reminders fire.
 */
data class ReminderConfig(
    val daysBefore: Set<Int>,
    val includeExpiryDay: Boolean,
    val time: LocalTime,
)

/**
 * Decides which reminder (if any) is due for a customer, and when the next one fires.
 * Pure logic, unit-tested in ReminderPlannerTest.
 *
 * Each reminder is identified by (customerId, expiryDate, offset). The database stores that
 * triple with a UNIQUE index, so a reminder is posted at most once even if several workers
 * run at the same time, and changing the expiration date naturally starts a fresh schedule.
 */
object ReminderPlanner {

    fun offsets(config: ReminderConfig): List<Int> {
        val set = config.daysBefore.filter { it in 1..365 }.toMutableSet()
        if (config.includeExpiryDay) set.add(0)
        return set.sortedDescending()
    }

    /**
     * The reminder offset that should be shown at [now], or null.
     *
     * - The relevant offset is the smallest configured offset that is >= the remaining days.
     * - If that offset equals the remaining days, it is the reminder for *today* and is due
     *   only once the configured time of day has been reached.
     * - If it is larger (the phone was off, or the customer was just added close to expiry),
     *   it is a missed reminder and is due immediately ("catch-up"), showing the real
     *   remaining days. Older missed reminders are not repeated.
     * - Nothing is due after the expiration date has passed.
     */
    fun dueOffset(expiry: LocalDate, now: LocalDateTime, config: ReminderConfig): Int? {
        val today = now.toLocalDate()
        val remaining = SubscriptionMath.remainingDays(expiry, today)
        if (remaining < 0) return null
        val offset = offsets(config).filter { it >= remaining }.minOrNull() ?: return null
        if (offset.toLong() == remaining && now.toLocalTime().isBefore(config.time)) {
            // Today's reminder, but its time has not come yet. Wait for it rather than
            // posting an older missed reminder first (avoids two alerts on the same day).
            return null
        }
        return offset
    }

    /** The next moment strictly after [now] when a reminder should fire, or null if none remain. */
    fun nextTrigger(expiry: LocalDate, now: LocalDateTime, config: ReminderConfig): LocalDateTime? =
        offsets(config)
            .map { expiry.minusDays(it.toLong()).atTime(config.time) }
            .filter { it.isAfter(now) }
            .minOrNull()
}

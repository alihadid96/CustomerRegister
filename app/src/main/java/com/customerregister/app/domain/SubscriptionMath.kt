package com.customerregister.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DurationUnit { DAYS, WEEKS, MONTHS, YEARS }

enum class AccountStatus { ACTIVE, EXPIRING, EXPIRED }

enum class StatusFilter { ALL, ACTIVE, EXPIRING, EXPIRED }

enum class SortOrder { NAME, START_DATE, EXPIRATION, RECENT }

/** Pure date math used by the UI, notifications and exports. No Android dependencies. */
object SubscriptionMath {

    fun addDuration(start: LocalDate, value: Int, unit: DurationUnit): LocalDate {
        val v = value.toLong()
        return when (unit) {
            DurationUnit.DAYS -> start.plusDays(v)
            DurationUnit.WEEKS -> start.plusWeeks(v)
            DurationUnit.MONTHS -> start.plusMonths(v)
            DurationUnit.YEARS -> start.plusYears(v)
        }
    }

    /** Days from [today] until [expiry]. 0 = expires today, negative = already expired. */
    fun remainingDays(expiry: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, expiry)

    /**
     * Active: more than [expiringThreshold] days left.
     * Expiring soon: between 0 and [expiringThreshold] days left (inclusive; 0 = expires today).
     * Expired: the expiration date has passed.
     */
    fun status(expiry: LocalDate, today: LocalDate, expiringThreshold: Int): AccountStatus {
        val r = remainingDays(expiry, today)
        return when {
            r < 0 -> AccountStatus.EXPIRED
            r <= expiringThreshold -> AccountStatus.EXPIRING
            else -> AccountStatus.ACTIVE
        }
    }

    /**
     * Renewal base: if the subscription is still running, extend from the current expiry
     * (the customer keeps the days already paid for); if it has expired, start from today.
     */
    fun renewalBase(currentExpiry: LocalDate, today: LocalDate, fromToday: Boolean): LocalDate =
        if (fromToday || currentExpiry.isBefore(today)) today else currentExpiry

    fun renew(
        currentExpiry: LocalDate,
        today: LocalDate,
        value: Int,
        unit: DurationUnit,
        fromToday: Boolean = false,
    ): LocalDate = addDuration(renewalBase(currentExpiry, today, fromToday), value, unit)
}

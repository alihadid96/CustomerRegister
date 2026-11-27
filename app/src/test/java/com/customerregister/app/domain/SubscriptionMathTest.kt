package com.customerregister.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SubscriptionMathTest {
    private val today = LocalDate.of(2026, 11, 27)

    @Test fun addDurationHandlesAllUnits() {
        assertEquals(LocalDate.of(2026, 12, 7), SubscriptionMath.addDuration(today, 10, DurationUnit.DAYS))
        assertEquals(LocalDate.of(2026, 12, 11), SubscriptionMath.addDuration(today, 2, DurationUnit.WEEKS))
        assertEquals(LocalDate.of(2027, 2, 27), SubscriptionMath.addDuration(today, 3, DurationUnit.MONTHS))
        assertEquals(LocalDate.of(2027, 11, 27), SubscriptionMath.addDuration(today, 1, DurationUnit.YEARS))
    }

    @Test fun monthEndIsClamped() {
        assertEquals(LocalDate.of(2027, 2, 28), SubscriptionMath.addDuration(LocalDate.of(2027, 1, 31), 1, DurationUnit.MONTHS))
        assertEquals(LocalDate.of(2028, 2, 29), SubscriptionMath.addDuration(LocalDate.of(2028, 1, 31), 1, DurationUnit.MONTHS))
    }

    @Test fun remainingDays() {
        assertEquals(3L, SubscriptionMath.remainingDays(today.plusDays(3), today))
        assertEquals(0L, SubscriptionMath.remainingDays(today, today))
        assertEquals(-2L, SubscriptionMath.remainingDays(today.minusDays(2), today))
        // across a year boundary
        assertEquals(35L, SubscriptionMath.remainingDays(LocalDate.of(2027, 1, 1), today))
    }

    @Test fun statusThresholdsDefault7() {
        assertEquals(AccountStatus.ACTIVE, SubscriptionMath.status(today.plusDays(8), today, 7))
        assertEquals(AccountStatus.EXPIRING, SubscriptionMath.status(today.plusDays(7), today, 7))
        assertEquals(AccountStatus.EXPIRING, SubscriptionMath.status(today.plusDays(1), today, 7))
        assertEquals(AccountStatus.EXPIRING, SubscriptionMath.status(today, today, 7))
        assertEquals(AccountStatus.EXPIRED, SubscriptionMath.status(today.minusDays(1), today, 7))
    }

    @Test fun statusThresholdIsConfigurable() {
        assertEquals(AccountStatus.EXPIRING, SubscriptionMath.status(today.plusDays(14), today, 14))
        assertEquals(AccountStatus.ACTIVE, SubscriptionMath.status(today.plusDays(4), today, 3))
    }

    @Test fun renewalExtendsFromCurrentExpiryWhenActive() {
        val expiry = today.plusDays(5)
        assertEquals(expiry.plusMonths(1), SubscriptionMath.renew(expiry, today, 1, DurationUnit.MONTHS))
    }

    @Test fun renewalStartsTodayWhenExpiredOrRequested() {
        assertEquals(today.plusMonths(1), SubscriptionMath.renew(today.minusDays(10), today, 1, DurationUnit.MONTHS))
        assertEquals(today.plusMonths(3), SubscriptionMath.renew(today.plusDays(20), today, 3, DurationUnit.MONTHS, fromToday = true))
    }
}

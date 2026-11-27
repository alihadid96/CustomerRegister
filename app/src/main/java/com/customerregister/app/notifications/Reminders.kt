package com.customerregister.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.customerregister.app.CustomerRegisterApp
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.NotificationLogEntity
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.domain.ReminderPlanner
import com.customerregister.app.domain.SubscriptionMath
import com.customerregister.app.util.localized
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val TAG = "Reminders"

/**
 * Notification scheduling strategy (no exact alarms):
 *
 * 1. Each customer has an independent unique WorkManager job "reminder_<id>". It runs at the
 *    customer's next reminder time, posts the due reminder, then schedules itself for the next
 *    one. Editing, renewing or deleting a customer replaces or cancels that job.
 * 2. A periodic "safety net" job checks every customer twice a day, catching anything delayed
 *    by Doze / battery restrictions.
 * 3. WorkManager persists jobs across reboots; additionally SystemEventReceiver rebuilds every
 *    schedule after reboot, app update, clock change or timezone change.
 * 4. Duplicates are impossible: every posted reminder is first inserted into notification_log
 *    under a UNIQUE (customerId, expiryDate, offset) key; if the insert is ignored, nothing is
 *    posted.
 */
object ReminderScheduler {

    private const val TAG_REMINDER = "customer_reminder"
    private const val PERIODIC = "reminder_safety_net"
    private const val RESCHEDULE_ALL = "reminder_reschedule_all"
    const val KEY_CUSTOMER_ID = "customer_id"

    fun workName(customerId: Long) = "reminder_$customerId"

    /** Re-evaluate a customer now (after add / edit / renew). The worker chains the next run. */
    fun scheduleCustomer(context: Context, customerId: Long) {
        val req = OneTimeWorkRequestBuilder<CustomerReminderWorker>()
            .setInputData(workDataOf(KEY_CUSTOMER_ID to customerId))
            .addTag(TAG_REMINDER)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(customerId), ExistingWorkPolicy.REPLACE, req)
    }

    /** Called by the worker itself: schedule the next run after the current one finishes. */
    internal fun scheduleNext(context: Context, customerId: Long, at: LocalDateTime) {
        val zone = ZoneId.systemDefault()
        val delayMs = Duration.between(ZonedDateTime.now(zone), at.atZone(zone))
            .toMillis().coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<CustomerReminderWorker>()
            .setInputData(workDataOf(KEY_CUSTOMER_ID to customerId))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(TAG_REMINDER)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(customerId), ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun cancelCustomer(context: Context, customerId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(customerId))
        NotificationHelper.cancel(context, customerId)
    }

    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SafetyNetWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Rebuild all schedules (settings changed, reboot, timezone/clock change, restore). */
    fun rescheduleAll(context: Context) {
        val req = OneTimeWorkRequestBuilder<RescheduleAllWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RESCHEDULE_ALL, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_REMINDER)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}

/** Shared logic: post a due reminder for one customer (with duplicate guard). */
object ReminderEngine {

    /** @return the next trigger time, or null if no further reminders are pending. */
    suspend fun process(context: Context, customer: CustomerEntity): LocalDateTime? {
        val app = context.applicationContext as CustomerRegisterApp
        val settings = app.container.settings.current()
        if (!settings.notificationsEnabled) return null
        val config = settings.reminderConfig
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val expiry = customer.expiryDate

        val offset = ReminderPlanner.dueOffset(expiry, now, config)
        if (offset != null && NotificationHelper.canPost(context)) {
            val remaining = SubscriptionMath.remainingDays(expiry, now.toLocalDate())
            val logDao = app.container.db.notificationLogDao()
            val rowId = logDao.insertIfNew(
                NotificationLogEntity(
                    customerId = customer.id,
                    customerName = customer.name,
                    expiryEpochDay = customer.expiryEpochDay,
                    daysBefore = offset,
                    remainingDays = remaining.toInt(),
                )
            )
            if (rowId != -1L) {
                val lc = context.localized(settings.language)
                val posted = NotificationHelper.postReminder(lc, settings, customer, remaining)
                if (!posted) logDao.deleteById(rowId) // allow a retry later
                Log.d(TAG, "Customer ${customer.id}: offset $offset posted=$posted")
            }
        }
        return ReminderPlanner.nextTrigger(expiry, now, config)
    }
}

class CustomerReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(ReminderScheduler.KEY_CUSTOMER_ID, -1L)
        if (id <= 0) return Result.success()
        val app = applicationContext as CustomerRegisterApp
        val customer = app.container.db.customerDao().get(id) ?: return Result.success()
        return try {
            val next = ReminderEngine.process(applicationContext, customer)
            if (next != null) ReminderScheduler.scheduleNext(applicationContext, id, next)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Reminder worker failed for $id", e)
            Result.retry()
        }
    }
}

class SafetyNetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CustomerRegisterApp
        return try {
            val today = LocalDate.now()
            app.container.db.customerDao().getAll()
                .filter { it.expiryEpochDay >= today.toEpochDay() }
                .forEach { ReminderEngine.process(applicationContext, it) }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Safety net failed", e)
            Result.retry()
        }
    }
}

class RescheduleAllWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CustomerRegisterApp
        val settings = app.container.settings.current()
        val wm = WorkManager.getInstance(applicationContext)
        if (!settings.notificationsEnabled) {
            ReminderScheduler.cancelAll(applicationContext)
            return Result.success()
        }
        NotificationHelper.ensureChannel(applicationContext.localized(settings.language), settings)
        ReminderScheduler.ensurePeriodic(applicationContext)
        val today = LocalDate.now().toEpochDay()
        app.container.db.customerDao().getAll().forEach { c ->
            if (c.expiryEpochDay >= today) ReminderScheduler.scheduleCustomer(applicationContext, c.id)
            else wm.cancelUniqueWork(ReminderScheduler.workName(c.id))
        }
        return Result.success()
    }
}

/** Reboot, app update, manual clock change and timezone change all rebuild the schedule. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> ReminderScheduler.rescheduleAll(context)
        }
    }
}

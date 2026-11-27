package com.customerregister.app.data

import android.content.Context
import androidx.room.withTransaction
import com.customerregister.app.data.db.AppDatabase
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.NotificationLogEntity
import com.customerregister.app.data.db.RenewalEntity
import com.customerregister.app.domain.DurationUnit
import com.customerregister.app.notifications.NotificationHelper
import com.customerregister.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class CustomerRepository(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val customers = db.customerDao()
    private val renewals = db.renewalDao()
    private val logs = db.notificationLogDao()

    fun observeCustomers(): Flow<List<CustomerEntity>> = customers.observeAll()
    fun observeCustomer(id: Long): Flow<CustomerEntity?> = customers.observe(id)
    fun observeRenewals(customerId: Long): Flow<List<RenewalEntity>> = renewals.observeForCustomer(customerId)
    fun observeNotificationLog(): Flow<List<NotificationLogEntity>> = logs.observeVisible()
    fun observeNotificationLog(customerId: Long): Flow<List<NotificationLogEntity>> = logs.observeForCustomer(customerId)

    suspend fun get(id: Long): CustomerEntity? = customers.get(id)

    suspend fun add(
        name: String,
        phone: String,
        start: LocalDate,
        expiry: LocalDate,
        durationValue: Int,
        durationUnit: DurationUnit,
        notes: String,
    ): Long {
        val id = db.withTransaction {
            val newId = customers.insert(
                CustomerEntity(
                    name = name.trim(),
                    phone = phone,
                    startEpochDay = start.toEpochDay(),
                    expiryEpochDay = expiry.toEpochDay(),
                    durationValue = durationValue,
                    durationUnit = durationUnit,
                    notes = notes.trim(),
                )
            )
            // The first subscription period is the first entry in the history.
            renewals.insert(
                RenewalEntity(
                    customerId = newId,
                    previousExpiryEpochDay = start.toEpochDay(),
                    newExpiryEpochDay = expiry.toEpochDay(),
                    durationValue = durationValue,
                    durationUnit = durationUnit,
                    note = RenewalEntity.NOTE_INITIAL,
                )
            )
            newId
        }
        ReminderScheduler.scheduleCustomer(context, id)
        return id
    }

    suspend fun update(customer: CustomerEntity) {
        customers.update(customer.copy(name = customer.name.trim(), notes = customer.notes.trim(), updatedAt = System.currentTimeMillis()))
        // Expiration date or name may have changed: rebuild this customer's schedule.
        NotificationHelper.cancel(context, customer.id)
        ReminderScheduler.scheduleCustomer(context, customer.id)
    }

    suspend fun delete(customer: CustomerEntity) {
        ReminderScheduler.cancelCustomer(context, customer.id) // pending work + shown notification
        db.withTransaction {
            logs.deleteForCustomer(customer.id)
            customers.delete(customer) // renewals cascade
        }
    }

    /** Appends to the renewal history (never overwrites) and reschedules reminders. */
    suspend fun renew(
        customer: CustomerEntity,
        newExpiry: LocalDate,
        durationValue: Int,
        durationUnit: DurationUnit,
        note: String,
    ): CustomerEntity {
        val updated = customer.copy(
            expiryEpochDay = newExpiry.toEpochDay(),
            durationValue = if (durationValue > 0) durationValue else customer.durationValue,
            durationUnit = if (durationValue > 0) durationUnit else customer.durationUnit,
            updatedAt = System.currentTimeMillis(),
        )
        db.withTransaction {
            renewals.insert(
                RenewalEntity(
                    customerId = customer.id,
                    previousExpiryEpochDay = customer.expiryEpochDay,
                    newExpiryEpochDay = newExpiry.toEpochDay(),
                    durationValue = durationValue,
                    durationUnit = durationUnit,
                    note = note.trim(),
                )
            )
            customers.update(updated)
        }
        NotificationHelper.cancel(context, customer.id)
        ReminderScheduler.scheduleCustomer(context, customer.id) // REPLACE cancels old reminders
        return updated
    }

    suspend fun clearNotificationHistory() = logs.hideAll()
}

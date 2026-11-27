package com.customerregister.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.customerregister.app.domain.AccountStatus
import com.customerregister.app.domain.DurationUnit
import com.customerregister.app.domain.SubscriptionMath
import java.time.LocalDate

/**
 * Customer. The primary key doubles as the visible Customer ID; it is AUTOINCREMENT and seeded
 * at 1000 so the first customer gets ID 1001 and IDs are never reused after deletion.
 */
@Entity(
    tableName = "customers",
    indices = [Index("name"), Index("phone"), Index("expiryEpochDay")]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** International digits without '+', e.g. 963944123456 */
    val phone: String,
    val startEpochDay: Long,
    val expiryEpochDay: Long,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// Derived values live in extensions (not in the entity) so Room never treats them as columns.
val CustomerEntity.startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
val CustomerEntity.expiryDate: LocalDate get() = LocalDate.ofEpochDay(expiryEpochDay)
val CustomerEntity.createdDate: LocalDate
    get() = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

fun CustomerEntity.remainingDays(today: LocalDate = LocalDate.now()): Long =
    SubscriptionMath.remainingDays(expiryDate, today)

fun CustomerEntity.status(threshold: Int, today: LocalDate = LocalDate.now()): AccountStatus =
    SubscriptionMath.status(expiryDate, today, threshold)

/** Append-only renewal history. Rows are never updated. */
@Entity(
    tableName = "renewals",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId")]
)
data class RenewalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val renewedAt: Long = System.currentTimeMillis(),
    val previousExpiryEpochDay: Long,
    val newExpiryEpochDay: Long,
    /** 0 when a custom expiration date was chosen. */
    val durationValue: Int,
    val durationUnit: DurationUnit,
    val note: String = "",
) {
    companion object {
        /** Marks the first subscription period recorded when the customer was created. */
        const val NOTE_INITIAL = "__initial__"
    }
}

/**
 * Notification history + duplicate guard. The UNIQUE index on (customerId, expiryEpochDay,
 * daysBefore) guarantees that each reminder is posted at most once per expiration date.
 * "Clearing" history only hides rows so cleared reminders are not posted again.
 */
@Entity(
    tableName = "notification_log",
    indices = [
        Index(value = ["customerId", "expiryEpochDay", "daysBefore"], unique = true),
        Index("postedAt")
    ]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val expiryEpochDay: Long,
    val daysBefore: Int,
    val remainingDays: Int,
    val postedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

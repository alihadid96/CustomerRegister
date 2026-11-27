package com.customerregister.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY id")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface RenewalDao {
    @Query("SELECT * FROM renewals WHERE customerId = :customerId ORDER BY renewedAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<RenewalEntity>>

    @Query("SELECT * FROM renewals ORDER BY id")
    suspend fun getAll(): List<RenewalEntity>

    @Insert
    suspend fun insert(renewal: RenewalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(renewals: List<RenewalEntity>)

    @Query("DELETE FROM renewals")
    suspend fun deleteAll()
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_log WHERE hidden = 0 ORDER BY postedAt DESC LIMIT 500")
    fun observeVisible(): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_log WHERE customerId = :customerId AND hidden = 0 ORDER BY postedAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_log ORDER BY id")
    suspend fun getAll(): List<NotificationLogEntity>

    /** Returns -1 if this reminder was already recorded (duplicate). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(entry: NotificationLogEntity): Long

    @Query("DELETE FROM notification_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NotificationLogEntity>)

    @Query("UPDATE notification_log SET hidden = 1")
    suspend fun hideAll()

    @Query("DELETE FROM notification_log WHERE customerId = :customerId")
    suspend fun deleteForCustomer(customerId: Long)

    @Query("DELETE FROM notification_log")
    suspend fun deleteAll()
}

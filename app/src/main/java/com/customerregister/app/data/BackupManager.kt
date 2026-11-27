package com.customerregister.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.customerregister.app.BuildConfig
import com.customerregister.app.data.db.AppDatabase
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.NotificationLogEntity
import com.customerregister.app.data.db.RenewalEntity
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.data.db.remainingDays
import com.customerregister.app.data.db.startDate
import com.customerregister.app.data.db.status
import com.customerregister.app.domain.CsvExporter
import com.customerregister.app.domain.DurationUnit
import com.customerregister.app.domain.ExportTable
import com.customerregister.app.domain.XlsxExporter
import com.customerregister.app.notifications.ReminderScheduler
import com.customerregister.app.util.Formats
import com.customerregister.app.util.durationLabel
import com.customerregister.app.util.statusLabel
import com.customerregister.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Summary of a backup file, shown before the user confirms a restore. */
data class BackupInfo(val customers: Int, val renewals: Int, val createdAt: Long, val json: JSONObject)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    companion object {
        const val FORMAT = "customerregister-backup"
        const val FORMAT_VERSION = 1
    }

    // ---------- Backup ----------

    suspend fun writeBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val customers = db.customerDao().getAll()
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("dbVersion", AppDatabase.VERSION)
            put("createdAt", System.currentTimeMillis())
            put("customers", JSONArray().apply { customers.forEach { put(it.toJson()) } })
            put("renewals", JSONArray().apply { db.renewalDao().getAll().forEach { put(it.toJson()) } })
            put("notificationLog", JSONArray().apply { db.notificationLogDao().getAll().forEach { put(it.toJson()) } })
            put("settings", JSONObject(settings.exportMap()))
        }
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open file")
        customers.size
    }

    // ---------- Restore ----------

    suspend fun readBackup(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Cannot open file")
        val json = JSONObject(text)
        require(json.optString("format") == FORMAT) { "Not a CustomerRegister backup" }
        BackupInfo(
            customers = json.optJSONArray("customers")?.length() ?: 0,
            renewals = json.optJSONArray("renewals")?.length() ?: 0,
            createdAt = json.optLong("createdAt"),
            json = json,
        )
    }

    /** Replaces ALL current data with the backup. The caller must have confirmed with the user. */
    suspend fun restore(info: BackupInfo) = withContext(Dispatchers.IO) {
        val json = info.json
        val customers = json.optJSONArray("customers").mapObjects { it.toCustomer() }
        val renewals = json.optJSONArray("renewals").mapObjects { it.toRenewal() }
        val logs = json.optJSONArray("notificationLog").mapObjects { it.toLog() }
        val customerIds = customers.map { it.id }.toSet()

        ReminderScheduler.cancelAll(context)
        db.withTransaction {
            db.notificationLogDao().deleteAll()
            db.renewalDao().deleteAll()
            db.customerDao().deleteAll()
            db.customerDao().insertAll(customers)
            db.renewalDao().insertAll(renewals.filter { it.customerId in customerIds })
            db.notificationLogDao().insertAll(logs)
        }
        json.optJSONObject("settings")?.let { s ->
            val map = mutableMapOf<String, Any?>()
            s.keys().forEach { k -> map[k] = s.opt(k) }
            settings.importMap(map)
        }
        ReminderScheduler.rescheduleAll(context)
    }

    // ---------- Exports ----------

    suspend fun customerTable(): ExportTable {
        val s = settings.current()
        val rows = db.customerDao().getAll().map { c ->
            listOf(
                Formats.customerId(c.id),
                c.name,
                "+" + c.phone,
                Formats.date(c.startDate),
                Formats.date(c.expiryDate),
                context.durationLabel(c.durationValue, c.durationUnit),
                context.statusLabel(c.status(s.expiringThreshold)),
                c.remainingDays().toString(),
                c.notes,
            )
        }
        val header = listOf(
            R.string.col_id, R.string.col_name, R.string.col_phone, R.string.col_start,
            R.string.col_expiry, R.string.col_duration, R.string.col_status,
            R.string.col_remaining, R.string.col_notes
        ).map { context.getString(it) }
        return ExportTable(context.getString(R.string.sheet_customers), header, rows)
    }

    private suspend fun renewalTable(): ExportTable {
        val names = db.customerDao().getAll().associate { it.id to it.name }
        val rows = db.renewalDao().getAll().map { r ->
            listOf(
                r.customerId.toString(),
                names[r.customerId].orEmpty(),
                Formats.dateTime(r.renewedAt),
                Formats.date(java.time.LocalDate.ofEpochDay(r.previousExpiryEpochDay)),
                Formats.date(java.time.LocalDate.ofEpochDay(r.newExpiryEpochDay)),
                context.durationLabel(r.durationValue, r.durationUnit),
                if (r.note == RenewalEntity.NOTE_INITIAL) context.getString(R.string.history_initial) else r.note,
            )
        }
        val header = listOf(
            R.string.col_id, R.string.col_name, R.string.col_renewed_at, R.string.col_previous_expiry,
            R.string.col_new_expiry, R.string.col_duration, R.string.col_notes
        ).map { context.getString(it) }
        return ExportTable(context.getString(R.string.sheet_renewals), header, rows)
    }

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val table = customerTable()
        context.contentResolver.openOutputStream(uri, "w")?.use { CsvExporter.write(table, it) }
            ?: error("Cannot open file")
        table.rows.size
    }

    suspend fun exportXlsx(uri: Uri): Int = withContext(Dispatchers.IO) {
        val customers = customerTable()
        val renewals = renewalTable()
        context.contentResolver.openOutputStream(uri, "w")?.use { XlsxExporter.write(listOf(customers, renewals), it) }
            ?: error("Cannot open file")
        customers.rows.size
    }
}

// ---------- JSON mapping ----------

private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { f(getJSONObject(it)) }
}

private fun unitOf(s: String): DurationUnit =
    runCatching { DurationUnit.valueOf(s) }.getOrDefault(DurationUnit.MONTHS)

private fun CustomerEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("phone", phone)
    put("startEpochDay", startEpochDay); put("expiryEpochDay", expiryEpochDay)
    put("durationValue", durationValue); put("durationUnit", durationUnit.name)
    put("notes", notes); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun JSONObject.toCustomer() = CustomerEntity(
    id = getLong("id"),
    name = getString("name"),
    phone = getString("phone"),
    startEpochDay = getLong("startEpochDay"),
    expiryEpochDay = getLong("expiryEpochDay"),
    durationValue = optInt("durationValue", 1),
    durationUnit = unitOf(optString("durationUnit", "MONTHS")),
    notes = optString("notes", ""),
    createdAt = optLong("createdAt", System.currentTimeMillis()),
    updatedAt = optLong("updatedAt", System.currentTimeMillis()),
)

private fun RenewalEntity.toJson() = JSONObject().apply {
    put("id", id); put("customerId", customerId); put("renewedAt", renewedAt)
    put("previousExpiryEpochDay", previousExpiryEpochDay); put("newExpiryEpochDay", newExpiryEpochDay)
    put("durationValue", durationValue); put("durationUnit", durationUnit.name); put("note", note)
}

private fun JSONObject.toRenewal() = RenewalEntity(
    id = getLong("id"),
    customerId = getLong("customerId"),
    renewedAt = getLong("renewedAt"),
    previousExpiryEpochDay = getLong("previousExpiryEpochDay"),
    newExpiryEpochDay = getLong("newExpiryEpochDay"),
    durationValue = optInt("durationValue", 0),
    durationUnit = unitOf(optString("durationUnit", "MONTHS")),
    note = optString("note", ""),
)

private fun NotificationLogEntity.toJson() = JSONObject().apply {
    put("id", id); put("customerId", customerId); put("customerName", customerName)
    put("expiryEpochDay", expiryEpochDay); put("daysBefore", daysBefore)
    put("remainingDays", remainingDays); put("postedAt", postedAt); put("hidden", hidden)
}

private fun JSONObject.toLog() = NotificationLogEntity(
    id = getLong("id"),
    customerId = getLong("customerId"),
    customerName = optString("customerName", ""),
    expiryEpochDay = getLong("expiryEpochDay"),
    daysBefore = optInt("daysBefore", 0),
    remainingDays = optInt("remainingDays", 0),
    postedAt = optLong("postedAt", 0L),
    hidden = optBoolean("hidden", false),
)

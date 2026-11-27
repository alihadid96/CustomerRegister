package com.customerregister.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.customerregister.app.domain.DurationUnit

class Converters {
    @TypeConverter
    fun unitToString(unit: DurationUnit): String = unit.name

    @TypeConverter
    fun stringToUnit(value: String): DurationUnit =
        runCatching { DurationUnit.valueOf(value) }.getOrDefault(DurationUnit.MONTHS)
}

@Database(
    entities = [CustomerEntity::class, RenewalEntity::class, NotificationLogEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun renewalDao(): RenewalDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        const val VERSION = 1
        const val NAME = "customer_register.db"
        const val FIRST_CUSTOMER_ID_SEED = 1000L

        /**
         * Schema migrations. When the schema changes: bump VERSION, add a Migration(old, new)
         * here, and commit the generated JSON in app/schemas. Destructive fallback is never
         * enabled, so customer data is never silently dropped on upgrade.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        /** Makes the first customer ID 1001 (and keeps IDs increasing after deletes). */
        fun seedIds(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'customers'")
            db.execSQL(
                "INSERT INTO sqlite_sequence (name, seq) VALUES ('customers', $FIRST_CUSTOMER_ID_SEED)"
            )
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedIds(db)
                    }
                })
                .build()
    }
}

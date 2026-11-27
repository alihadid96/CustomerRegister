package com.customerregister.app

import android.app.Application
import android.content.Context
import com.customerregister.app.data.BackupManager
import com.customerregister.app.data.CustomerRepository
import com.customerregister.app.data.SettingsRepository
import com.customerregister.app.data.db.AppDatabase
import com.customerregister.app.notifications.NotificationHelper
import com.customerregister.app.notifications.ReminderScheduler
import com.customerregister.app.util.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Simple manual dependency container (no DI framework needed for an app this size). */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val customers = CustomerRepository(context, db)
    val backup = BackupManager(context, db, settings)
}

class CustomerRegisterApp : Application() {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            val s = container.settings.current()
            NotificationHelper.ensureChannel(localized(s.language), s)
            if (s.notificationsEnabled) ReminderScheduler.ensurePeriodic(this@CustomerRegisterApp)
        }
    }
}

val Context.app: CustomerRegisterApp get() = applicationContext as CustomerRegisterApp

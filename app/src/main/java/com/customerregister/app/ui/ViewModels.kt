package com.customerregister.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.customerregister.app.AppContainer
import com.customerregister.app.R
import com.customerregister.app.app
import com.customerregister.app.data.AppSettings
import com.customerregister.app.data.BackupInfo
import com.customerregister.app.data.ThemeMode
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.NotificationLogEntity
import com.customerregister.app.data.db.RenewalEntity
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.data.db.remainingDays
import com.customerregister.app.data.db.startDate
import com.customerregister.app.data.db.status
import com.customerregister.app.domain.AccountStatus
import com.customerregister.app.domain.DurationUnit
import com.customerregister.app.domain.PhoneNumbers
import com.customerregister.app.domain.SortOrder
import com.customerregister.app.domain.StatusFilter
import com.customerregister.app.domain.SubscriptionMath
import com.customerregister.app.notifications.NotificationHelper
import com.customerregister.app.notifications.ReminderScheduler
import com.customerregister.app.ui.components.CustomerItem
import com.customerregister.app.util.localized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, Context) -> VM,
): VM {
    val ctx = LocalContext.current.applicationContext
    val factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { create(ctx.app.container, ctx) }
    }
    return viewModel(key = key, factory = factory)
}

// ---------------------------------------------------------------------------------------------
// Main (dashboard, customer list, notification history) — shared across the bottom tabs.
// ---------------------------------------------------------------------------------------------

data class DashboardState(
    val total: Int = 0,
    val active: Int = 0,
    val expiring: Int = 0,
    val expired: Int = 0,
    val upcoming: List<CustomerItem> = emptyList(),
    val recent: List<CustomerItem> = emptyList(),
    /** Number of expirations in each of the next 8 weeks. */
    val weekly: List<Int> = List(8) { 0 },
)

class MainViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.customers

    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val today = MutableStateFlow(LocalDate.now())
    fun refreshToday() = today.update { LocalDate.now() }

    /** null until the database has loaded (avoids flashing the empty state). */
    val items: StateFlow<List<CustomerItem>?> = combine(
        repo.observeCustomers(), container.settings.settings, today
    ) { list, s, t ->
        list.map { CustomerItem(it, it.status(s.expiringThreshold, t), it.remainingDays(t)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dashboard: StateFlow<DashboardState> = items.map { list ->
        val l = list.orEmpty()
        DashboardState(
            total = l.size,
            active = l.count { it.status == AccountStatus.ACTIVE },
            expiring = l.count { it.status == AccountStatus.EXPIRING },
            expired = l.count { it.status == AccountStatus.EXPIRED },
            upcoming = l.filter { it.remaining in 0..30 }.sortedBy { it.remaining }.take(6),
            recent = l.sortedByDescending { it.customer.createdAt }.take(5),
            weekly = List(8) { w -> l.count { it.remaining in (w * 7L)..(w * 7L + 6) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    // Customer list controls
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(StatusFilter.ALL)
    val sort = MutableStateFlow(SortOrder.EXPIRATION)

    val filtered: StateFlow<List<CustomerItem>?> = combine(items, query, filter, sort) { list, q, f, s ->
        if (list == null) return@combine null
        val needle = q.trim()
        val digits = needle.filter { it.isDigit() }
        list.asSequence()
            .filter {
                when (f) {
                    StatusFilter.ALL -> true
                    StatusFilter.ACTIVE -> it.status == AccountStatus.ACTIVE
                    StatusFilter.EXPIRING -> it.status == AccountStatus.EXPIRING
                    StatusFilter.EXPIRED -> it.status == AccountStatus.EXPIRED
                }
            }
            .filter {
                needle.isEmpty() ||
                    it.customer.name.contains(needle, ignoreCase = true) ||
                    it.customer.id.toString().contains(needle) ||
                    (digits.length >= 3 && it.customer.phone.contains(digits.trimStart('0')))
            }
            .let { seq ->
                when (s) {
                    SortOrder.NAME -> seq.sortedBy { it.customer.name.lowercase() }
                    SortOrder.START_DATE -> seq.sortedByDescending { it.customer.startEpochDay }
                    SortOrder.EXPIRATION -> seq.sortedBy { it.customer.expiryEpochDay }
                    SortOrder.RECENT -> seq.sortedByDescending { it.customer.createdAt }
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notificationLog: StateFlow<List<NotificationLogEntity>> = repo.observeNotificationLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearNotificationHistory() = viewModelScope.launch { repo.clearNotificationHistory() }
}

// ---------------------------------------------------------------------------------------------
// Add / edit customer
// ---------------------------------------------------------------------------------------------

data class EditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val phone: String = "",
    val start: LocalDate = LocalDate.now(),
    val durationValue: String = "1",
    val durationUnit: DurationUnit = DurationUnit.MONTHS,
    val customExpiry: Boolean = false,
    val expiry: LocalDate = LocalDate.now().plusMonths(1),
    val notes: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    val durationInt: Int get() = durationValue.toIntOrNull() ?: 0
    val computedExpiry: LocalDate
        get() = if (customExpiry) expiry
        else SubscriptionMath.addDuration(start, durationInt.coerceAtLeast(0), durationUnit)
    val nameError: Boolean get() = name.isBlank()
    val durationError: Boolean get() = !customExpiry && durationInt !in 1..3650
    val expiryError: Boolean get() = computedExpiry.isBefore(start)
}

class CustomerEditViewModel(private val container: AppContainer, private val id: Long?) : ViewModel() {
    private val _state = MutableStateFlow(EditState(loaded = id == null))
    val state: StateFlow<EditState> = _state.asStateFlow()
    private var original: CustomerEntity? = null
    var countryCode: String = ""
        private set
    var threshold: Int = 7
        private set

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            countryCode = settings.defaultCountryCode
            threshold = settings.expiringThreshold
            if (id != null) {
                val c = container.customers.get(id)
                original = c
                if (c != null) {
                    val computed = SubscriptionMath.addDuration(c.startDate, c.durationValue, c.durationUnit)
                    _state.value = EditState(
                        loaded = true,
                        isNew = false,
                        name = c.name,
                        phone = PhoneNumbers.display(c.phone),
                        start = c.startDate,
                        durationValue = c.durationValue.coerceAtLeast(1).toString(),
                        durationUnit = c.durationUnit,
                        customExpiry = computed != c.expiryDate,
                        expiry = c.expiryDate,
                        notes = c.notes,
                    )
                } else {
                    _state.update { it.copy(loaded = true) }
                }
            }
        }
    }

    fun update(f: (EditState) -> EditState) = _state.update(f)

    fun normalizedPhone(): String = PhoneNumbers.normalize(_state.value.phone, countryCode)
    fun phoneError(): Boolean = !PhoneNumbers.isValid(normalizedPhone())

    /** @param onSaved receives the customer id. */
    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        if (s.nameError || s.durationError || s.expiryError || phoneError()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val phone = normalizedPhone()
            val expiry = s.computedExpiry
            val value = if (s.customExpiry) 0 else s.durationInt
            val orig = original
            val resultId = if (orig == null) {
                container.customers.add(s.name, phone, s.start, expiry, value, s.durationUnit, s.notes)
            } else {
                container.customers.update(
                    orig.copy(
                        name = s.name,
                        phone = phone,
                        startEpochDay = s.start.toEpochDay(),
                        expiryEpochDay = expiry.toEpochDay(),
                        durationValue = if (value > 0) value else orig.durationValue,
                        durationUnit = s.durationUnit,
                        notes = s.notes,
                    )
                )
                orig.id
            }
            _state.update { it.copy(saving = false) }
            onSaved(resultId)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Customer details
// ---------------------------------------------------------------------------------------------

class CustomerDetailViewModel(private val container: AppContainer, val id: Long) : ViewModel() {
    val customer: StateFlow<CustomerEntity?> = container.customers.observeCustomer(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val renewals: StateFlow<List<RenewalEntity>> = container.customers.observeRenewals(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminders: StateFlow<List<NotificationLogEntity>> = container.customers.observeNotificationLog(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        customer.value?.let { container.customers.delete(it) }
            ?: container.customers.get(id)?.let { container.customers.delete(it) }
        onDone()
    }
}

// ---------------------------------------------------------------------------------------------
// Renewal
// ---------------------------------------------------------------------------------------------

enum class RenewMode { PRESET, CUSTOM_DURATION, CUSTOM_DATE }

data class RenewState(
    val customer: CustomerEntity? = null,
    val mode: RenewMode = RenewMode.PRESET,
    val presetMonths: Int = 1,
    val durationValue: String = "1",
    val durationUnit: DurationUnit = DurationUnit.MONTHS,
    val customDate: LocalDate = LocalDate.now().plusMonths(1),
    val fromToday: Boolean = false,
    val note: String = "",
    val saving: Boolean = false,
) {
    fun newExpiry(today: LocalDate = LocalDate.now()): LocalDate? {
        val c = customer ?: return null
        return when (mode) {
            RenewMode.PRESET -> SubscriptionMath.renew(c.expiryDate, today, presetMonths, DurationUnit.MONTHS, fromToday)
            RenewMode.CUSTOM_DURATION -> {
                val v = durationValue.toIntOrNull() ?: return null
                if (v !in 1..3650) return null
                SubscriptionMath.renew(c.expiryDate, today, v, durationUnit, fromToday)
            }
            RenewMode.CUSTOM_DATE -> customDate
        }
    }
}

class RenewViewModel(private val container: AppContainer, id: Long) : ViewModel() {
    private val _state = MutableStateFlow(RenewState())
    val state: StateFlow<RenewState> = _state.asStateFlow()
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val c = container.customers.get(id)
            _state.update {
                it.copy(
                    customer = c,
                    presetMonths = if (c != null && c.durationUnit == DurationUnit.MONTHS && c.durationValue in listOf(1, 3, 6, 12)) c.durationValue else 1,
                    customDate = (c?.expiryDate ?: LocalDate.now()).plusMonths(1),
                )
            }
        }
    }

    fun update(f: (RenewState) -> RenewState) = _state.update(f)

    fun confirm(onDone: (CustomerEntity) -> Unit) {
        val s = _state.value
        val c = s.customer ?: return
        val newExpiry = s.newExpiry() ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val (value, unit) = when (s.mode) {
                RenewMode.PRESET -> s.presetMonths to DurationUnit.MONTHS
                RenewMode.CUSTOM_DURATION -> (s.durationValue.toIntOrNull() ?: 0) to s.durationUnit
                RenewMode.CUSTOM_DATE -> 0 to c.durationUnit
            }
            val updated = container.customers.renew(c, newExpiry, value, unit, s.note)
            _state.update { it.copy(saving = false, customer = updated) }
            onDone(updated)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

class SettingsViewModel(private val container: AppContainer, private val appContext: Context) : ViewModel() {
    private val repo = container.settings
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One-shot messages for a snackbar. */
    val message = MutableStateFlow<String?>(null)
    val pendingRestore = MutableStateFlow<BackupInfo?>(null)

    private fun rescheduleAfter(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        ReminderScheduler.rescheduleAll(appContext)
    }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setTheme(m) }
    fun setLanguage(tag: String) = viewModelScope.launch { repo.setLanguage(tag) }

    fun setNotificationsEnabled(v: Boolean) = rescheduleAfter { repo.setNotificationsEnabled(v) }
    fun setNotifyOnExpiryDay(v: Boolean) = rescheduleAfter { repo.setNotifyOnExpiryDay(v) }
    fun addReminderDay(d: Int) = rescheduleAfter {
        val cur = repo.current().reminderDays
        repo.setReminderDays(cur + d)
    }
    fun removeReminderDay(d: Int) = rescheduleAfter {
        repo.setReminderDays(repo.current().reminderDays - d)
    }
    fun resetReminderDays() = rescheduleAfter { repo.setReminderDays(AppSettings.DEFAULT_REMINDER_DAYS) }
    fun setReminderTime(h: Int, m: Int) = rescheduleAfter { repo.setReminderTime(h, m) }
    fun setThreshold(v: Int) = viewModelScope.launch { repo.setExpiringThreshold(v) }
    fun setSound(uri: String, name: String) = viewModelScope.launch {
        repo.setSound(uri, name)
        val s = repo.current()
        NotificationHelper.ensureChannel(appContext.localized(s.language), s)
    }
    fun setReminderTemplate(t: String) = viewModelScope.launch { repo.setReminderTemplate(t) }
    fun setRenewalTemplate(t: String) = viewModelScope.launch { repo.setRenewalTemplate(t) }
    fun setCountryCode(cc: String) = viewModelScope.launch { repo.setDefaultCountryCode(cc) }

    fun sendTest(context: Context) = viewModelScope.launch {
        val s = repo.current()
        val ok = NotificationHelper.postTest(context.localized(s.language), s)
        if (!ok) message.value = context.getString(R.string.notif_permission_needed)
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        message.value = runCatching { container.backup.exportCsv(uri) }
            .fold({ appContext.getString(R.string.export_done, it) }, { appContext.getString(R.string.error_generic, it.message ?: "") })
    }

    fun exportXlsx(uri: Uri) = viewModelScope.launch {
        message.value = runCatching { container.backup.exportXlsx(uri) }
            .fold({ appContext.getString(R.string.export_done, it) }, { appContext.getString(R.string.error_generic, it.message ?: "") })
    }

    fun backup(uri: Uri) = viewModelScope.launch {
        message.value = runCatching { container.backup.writeBackup(uri) }
            .fold({ appContext.getString(R.string.backup_done, it) }, { appContext.getString(R.string.error_generic, it.message ?: "") })
    }

    fun prepareRestore(uri: Uri) = viewModelScope.launch {
        runCatching { container.backup.readBackup(uri) }
            .onSuccess { pendingRestore.value = it }
            .onFailure { message.value = appContext.getString(R.string.restore_invalid) }
    }

    /** @param onLanguage called with the restored language so the activity can apply it. */
    fun confirmRestore(onLanguage: (String) -> Unit) = viewModelScope.launch {
        val info = pendingRestore.value ?: return@launch
        pendingRestore.value = null
        runCatching { container.backup.restore(info) }
            .onSuccess {
                message.value = appContext.getString(R.string.restore_done, info.customers)
                onLanguage(repo.settings.first().language)
            }
            .onFailure { message.value = appContext.getString(R.string.error_generic, it.message ?: "") }
    }
}

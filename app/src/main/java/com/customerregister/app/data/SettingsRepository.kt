package com.customerregister.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.customerregister.app.domain.ReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val reminderDays: List<Int> = DEFAULT_REMINDER_DAYS,
    val notifyOnExpiryDay: Boolean = true,
    val expiringThreshold: Int = 7,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    /** "" = system default sound, "silent" = no sound, otherwise a content:// ringtone URI. */
    val soundUri: String = "",
    val soundName: String = "",
    /** Bumped whenever the sound changes (Android fixes a channel's sound once created). */
    val channelVersion: Int = 1,
    /** Blank = use the built-in (localised) default template. */
    val reminderTemplate: String = "",
    val renewalTemplate: String = "",
    val defaultCountryCode: String = "963",
    val onboardingDone: Boolean = false,
    /** "" = follow the phone, "en" or "ar". */
    val language: String = "",
) {
    val reminderConfig: ReminderConfig
        get() = ReminderConfig(
            daysBefore = reminderDays.toSet(),
            includeExpiryDay = notifyOnExpiryDay,
            time = LocalTime.of(reminderHour.coerceIn(0, 23), reminderMinute.coerceIn(0, 59)),
        )

    companion object {
        val DEFAULT_REMINDER_DAYS = listOf(7, 3, 1)
        const val SOUND_SILENT = "silent"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val NOTIF_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_DAYS = stringPreferencesKey("reminder_days")
        val EXPIRY_DAY = booleanPreferencesKey("notify_on_expiry_day")
        val THRESHOLD = intPreferencesKey("expiring_threshold")
        val HOUR = intPreferencesKey("reminder_hour")
        val MINUTE = intPreferencesKey("reminder_minute")
        val SOUND_URI = stringPreferencesKey("sound_uri")
        val SOUND_NAME = stringPreferencesKey("sound_name")
        val CHANNEL_VERSION = intPreferencesKey("channel_version")
        val REMINDER_TEMPLATE = stringPreferencesKey("reminder_template")
        val RENEWAL_TEMPLATE = stringPreferencesKey("renewal_template")
        val COUNTRY_CODE = stringPreferencesKey("default_country_code")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> fromPrefs(p) }

    suspend fun current(): AppSettings = settings.first()

    private fun fromPrefs(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            notificationsEnabled = p[Keys.NOTIF_ENABLED] ?: d.notificationsEnabled,
            reminderDays = p[Keys.REMINDER_DAYS]?.let(::parseDays) ?: d.reminderDays,
            notifyOnExpiryDay = p[Keys.EXPIRY_DAY] ?: d.notifyOnExpiryDay,
            expiringThreshold = p[Keys.THRESHOLD] ?: d.expiringThreshold,
            reminderHour = p[Keys.HOUR] ?: d.reminderHour,
            reminderMinute = p[Keys.MINUTE] ?: d.reminderMinute,
            soundUri = p[Keys.SOUND_URI] ?: d.soundUri,
            soundName = p[Keys.SOUND_NAME] ?: d.soundName,
            channelVersion = p[Keys.CHANNEL_VERSION] ?: d.channelVersion,
            reminderTemplate = p[Keys.REMINDER_TEMPLATE] ?: d.reminderTemplate,
            renewalTemplate = p[Keys.RENEWAL_TEMPLATE] ?: d.renewalTemplate,
            defaultCountryCode = p[Keys.COUNTRY_CODE] ?: d.defaultCountryCode,
            onboardingDone = p[Keys.ONBOARDING] ?: d.onboardingDone,
            language = p[Keys.LANGUAGE] ?: d.language,
        )
    }

    private fun parseDays(s: String): List<Int> =
        s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..365 }.distinct().sortedDescending()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setNotificationsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.NOTIF_ENABLED] = v }
    suspend fun setReminderDays(days: List<Int>) = context.dataStore.edit {
        it[Keys.REMINDER_DAYS] = days.filter { d -> d in 1..365 }.distinct().sortedDescending().joinToString(",")
    }
    suspend fun setNotifyOnExpiryDay(v: Boolean) = context.dataStore.edit { it[Keys.EXPIRY_DAY] = v }
    suspend fun setExpiringThreshold(v: Int) = context.dataStore.edit { it[Keys.THRESHOLD] = v.coerceIn(1, 90) }
    suspend fun setReminderTime(hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.HOUR] = hour.coerceIn(0, 23); it[Keys.MINUTE] = minute.coerceIn(0, 59)
    }
    suspend fun setSound(uri: String, name: String) = context.dataStore.edit {
        it[Keys.SOUND_URI] = uri
        it[Keys.SOUND_NAME] = name
        it[Keys.CHANNEL_VERSION] = (it[Keys.CHANNEL_VERSION] ?: 1) + 1
    }
    suspend fun setReminderTemplate(t: String) = context.dataStore.edit { it[Keys.REMINDER_TEMPLATE] = t }
    suspend fun setRenewalTemplate(t: String) = context.dataStore.edit { it[Keys.RENEWAL_TEMPLATE] = t }
    suspend fun setDefaultCountryCode(cc: String) = context.dataStore.edit {
        it[Keys.COUNTRY_CODE] = cc.filter { c -> c.isDigit() }.take(4)
    }
    suspend fun setLanguage(tag: String) = context.dataStore.edit { it[Keys.LANGUAGE] = tag }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING] = true }

    /** Used by backup/restore. */
    suspend fun exportMap(): Map<String, Any> {
        val s = current()
        return mapOf(
            "theme" to s.theme.name,
            "notificationsEnabled" to s.notificationsEnabled,
            "reminderDays" to s.reminderDays.joinToString(","),
            "notifyOnExpiryDay" to s.notifyOnExpiryDay,
            "expiringThreshold" to s.expiringThreshold,
            "reminderHour" to s.reminderHour,
            "reminderMinute" to s.reminderMinute,
            "reminderTemplate" to s.reminderTemplate,
            "renewalTemplate" to s.renewalTemplate,
            "defaultCountryCode" to s.defaultCountryCode,
            "language" to s.language,
        )
    }

    suspend fun importMap(m: Map<String, Any?>) = context.dataStore.edit { p ->
        (m["theme"] as? String)?.let { v -> if (ThemeMode.entries.any { it.name == v }) p[Keys.THEME] = v }
        (m["notificationsEnabled"] as? Boolean)?.let { p[Keys.NOTIF_ENABLED] = it }
        (m["reminderDays"] as? String)?.let { p[Keys.REMINDER_DAYS] = parseDays(it).joinToString(",") }
        (m["notifyOnExpiryDay"] as? Boolean)?.let { p[Keys.EXPIRY_DAY] = it }
        (m["expiringThreshold"] as? Number)?.let { p[Keys.THRESHOLD] = it.toInt().coerceIn(1, 90) }
        (m["reminderHour"] as? Number)?.let { p[Keys.HOUR] = it.toInt().coerceIn(0, 23) }
        (m["reminderMinute"] as? Number)?.let { p[Keys.MINUTE] = it.toInt().coerceIn(0, 59) }
        (m["reminderTemplate"] as? String)?.let { p[Keys.REMINDER_TEMPLATE] = it }
        (m["renewalTemplate"] as? String)?.let { p[Keys.RENEWAL_TEMPLATE] = it }
        (m["defaultCountryCode"] as? String)?.let { p[Keys.COUNTRY_CODE] = it.filter { c -> c.isDigit() }.take(4) }
        (m["language"] as? String)?.let { if (it in listOf("", "en", "ar")) p[Keys.LANGUAGE] = it }
    }
}

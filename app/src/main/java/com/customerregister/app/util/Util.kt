package com.customerregister.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.customerregister.app.R
import com.customerregister.app.data.AppSettings
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.data.db.remainingDays
import com.customerregister.app.domain.AccountStatus
import com.customerregister.app.domain.DurationUnit
import com.customerregister.app.domain.MessageTemplate
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    /** dd/MM/yyyy with Western digits, matching the format used in WhatsApp messages and exports. */
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)

    fun date(d: LocalDate): String = DATE.format(d)
    fun dateTime(epochMillis: Long): String =
        DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    fun customerId(id: Long): String = id.toString()
}

/** Returns a context whose resources use the in-app language (needed for background work). */
fun Context.localized(language: String): Context {
    if (language.isBlank()) return this
    val config = Configuration(resources.configuration)
    val locale = Locale.forLanguageTag(language)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.durationLabel(value: Int, unit: DurationUnit): String {
    if (value <= 0) return getString(R.string.duration_custom)
    val res = when (unit) {
        DurationUnit.DAYS -> R.plurals.n_days
        DurationUnit.WEEKS -> R.plurals.n_weeks
        DurationUnit.MONTHS -> R.plurals.n_months
        DurationUnit.YEARS -> R.plurals.n_years
    }
    return resources.getQuantityString(res, value, value)
}

fun Context.statusLabel(status: AccountStatus): String = getString(
    when (status) {
        AccountStatus.ACTIVE -> R.string.status_active
        AccountStatus.EXPIRING -> R.string.status_expiring
        AccountStatus.EXPIRED -> R.string.status_expired
    }
)

fun Context.remainingLabel(remaining: Long): String = when {
    remaining > 0 -> resources.getQuantityString(R.plurals.days_remaining, remaining.toInt(), remaining.toInt())
    remaining == 0L -> getString(R.string.expires_today)
    else -> resources.getQuantityString(R.plurals.days_ago, (-remaining).toInt(), (-remaining).toInt())
}

/**
 * WhatsApp Click-to-Chat (https://wa.me/<number>?text=<message>).
 * The message is only pre-filled: the user always presses Send inside WhatsApp.
 */
object WhatsApp {
    const val PKG_BUSINESS = "com.whatsapp.w4b"
    const val PKG_REGULAR = "com.whatsapp"

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** WhatsApp Business if installed, else WhatsApp, else null (browser / chooser). */
    fun preferredPackage(context: Context): String? = when {
        isInstalled(context, PKG_BUSINESS) -> PKG_BUSINESS
        isInstalled(context, PKG_REGULAR) -> PKG_REGULAR
        else -> null
    }

    fun url(phoneDigits: String, message: String): String =
        "https://wa.me/${phoneDigits.filter { it.isDigit() }}?text=" +
            URLEncoder.encode(message, "UTF-8").replace("+", "%20")

    fun intent(context: Context, phoneDigits: String, message: String): Intent {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url(phoneDigits, message)))
        preferredPackage(context)?.let { i.setPackage(it) }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return i
    }

    fun open(context: Context, phoneDigits: String, message: String) {
        try {
            context.startActivity(intent(context, phoneDigits, message))
        } catch (e: ActivityNotFoundException) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url(phoneDigits, message)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (e2: ActivityNotFoundException) {
                context.toast(context.getString(R.string.whatsapp_not_found))
            }
        }
    }

    fun reminderMessage(context: Context, settings: AppSettings, c: CustomerEntity): String =
        render(context, settings.reminderTemplate.ifBlank { context.getString(R.string.default_reminder_template) }, c)

    fun renewalMessage(context: Context, settings: AppSettings, c: CustomerEntity): String =
        render(context, settings.renewalTemplate.ifBlank { context.getString(R.string.default_renewal_template) }, c)

    private fun render(context: Context, template: String, c: CustomerEntity): String =
        MessageTemplate.render(
            template = template,
            name = c.name,
            id = Formats.customerId(c.id),
            expiryDate = Formats.date(c.expiryDate),
            remainingDays = c.remainingDays(),
            phone = "+" + c.phone,
        )
}

/** Applies the in-app language ("" = follow the phone). Recreates activities; call on the main thread. */
fun applyAppLanguage(tag: String) {
    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
        if (tag.isBlank()) androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        else androidx.core.os.LocaleListCompat.forLanguageTags(tag)
    )
}

/** The current in-app language as "", "en" or "ar". */
fun currentAppLanguage(): String {
    val list = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    if (list.isEmpty) return ""
    return when (list[0]?.language) {
        "ar" -> "ar"
        "en" -> "en"
        else -> ""
    }
}

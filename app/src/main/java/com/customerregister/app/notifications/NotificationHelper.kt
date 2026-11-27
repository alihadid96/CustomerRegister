package com.customerregister.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.customerregister.app.MainActivity
import com.customerregister.app.R
import com.customerregister.app.data.AppSettings
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.util.Formats
import com.customerregister.app.util.WhatsApp

object NotificationHelper {

    private const val CHANNEL_PREFIX = "expiry_reminders_v"
    const val EXTRA_CUSTOMER_ID = "customer_id"
    const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"

    fun channelId(settings: AppSettings) = CHANNEL_PREFIX + settings.channelVersion

    /**
     * Android fixes a channel's sound once it is created, so a sound change creates a new
     * channel version and deletes the old ones.
     */
    fun ensureChannel(context: Context, settings: AppSettings) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val id = channelId(settings)
        nm.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { nm.deleteNotificationChannel(it.id) }
        if (nm.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(
            id,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
            enableLights(true)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            when (settings.soundUri) {
                AppSettings.SOUND_SILENT -> setSound(null, null)
                "" -> setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
                else -> setSound(Uri.parse(settings.soundUri), attrs)
            }
        }
        nm.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openCustomerIntent(context: Context, customerId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CUSTOMER_ID, customerId)
            // Make each customer's intent distinct so extras are not merged.
            action = "com.customerregister.app.OPEN_CUSTOMER_$customerId"
        }
        return PendingIntent.getActivity(
            context, customerId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Posts a reminder. One notification per customer (id = customer id) so a newer reminder
     * replaces an older one for the same customer instead of stacking.
     * @param lc a context localised to the in-app language.
     */
    @SuppressLint("MissingPermission") // checked by canPost()
    fun postReminder(
        lc: Context,
        settings: AppSettings,
        customer: CustomerEntity,
        remainingDays: Long,
    ): Boolean {
        if (!canPost(lc)) return false
        ensureChannel(lc, settings)
        val title = when {
            remainingDays <= 0L -> lc.getString(R.string.notif_title_today)
            else -> lc.getString(R.string.notif_title_soon)
        }
        val remainingText = if (remainingDays <= 0L) lc.getString(R.string.expires_today)
        else lc.resources.getQuantityString(R.plurals.n_days, remainingDays.toInt(), remainingDays.toInt())
        val body = lc.getString(
            R.string.notif_body,
            customer.name,
            Formats.customerId(customer.id),
            Formats.date(customer.expiryDate),
            remainingText
        )
        val waIntent = WhatsApp.intent(lc, customer.phone, WhatsApp.reminderMessage(lc, settings, customer))
        val waPending = PendingIntent.getActivity(
            lc, 100_000 + customer.id.toInt(), waIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = openCustomerIntent(lc, customer.id)
        val n = NotificationCompat.Builder(lc, channelId(settings))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2F6BFF.toInt())
            .setContentTitle(title)
            .setContentText(lc.getString(R.string.notif_short, customer.name, remainingText))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, lc.getString(R.string.action_open_profile), open)
            .addAction(0, lc.getString(R.string.action_whatsapp), waPending)
            .build()
        NotificationManagerCompat.from(lc).notify(customer.id.toInt(), n)
        return true
    }

    fun cancel(context: Context, customerId: Long) {
        NotificationManagerCompat.from(context).cancel(customerId.toInt())
    }

    @SuppressLint("MissingPermission")
    fun postTest(lc: Context, settings: AppSettings): Boolean {
        if (!canPost(lc)) return false
        ensureChannel(lc, settings)
        val n = NotificationCompat.Builder(lc, channelId(settings))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2F6BFF.toInt())
            .setContentTitle(lc.getString(R.string.notif_title_soon))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    lc.getString(R.string.notif_body, "Ahmad", "1024", "05/12/2026",
                        lc.resources.getQuantityString(R.plurals.n_days, 3, 3))
                )
            )
            .setContentText(lc.getString(R.string.notif_test))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(lc).notify(Int.MAX_VALUE - 1, n)
        return true
    }
}

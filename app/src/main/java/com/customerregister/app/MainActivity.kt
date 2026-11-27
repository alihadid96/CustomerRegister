package com.customerregister.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.customerregister.app.notifications.NotificationHelper
import com.customerregister.app.notifications.ReminderScheduler
import com.customerregister.app.ui.AppRoot
import com.customerregister.app.ui.theme.CustomerRegisterTheme
import com.customerregister.app.ui.theme.isDark
import com.customerregister.app.util.currentAppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val openCustomerId = MutableStateFlow<Long?>(null)
    private val canPost = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost.value = NotificationHelper.canPost(this)
        if (granted) ReminderScheduler.rescheduleAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        canPost.value = NotificationHelper.canPost(this)

        val settingsRepo = app.container.settings
        lifecycleScope.launch {
            val s = settingsRepo.current()
            // Keep our stored language in sync with the system per-app language (Android 13+).
            if (Build.VERSION.SDK_INT >= 33) {
                val actual = currentAppLanguage()
                if (actual != s.language) settingsRepo.setLanguage(actual)
            }
            // First launch: ask for notification permission (Android 13+).
            if (!s.onboardingDone) {
                settingsRepo.setOnboardingDone()
                requestNotificationPermission()
                ReminderScheduler.rescheduleAll(this@MainActivity)
            }
        }

        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = null)
            val s = settings
            val dark = if (s == null) false else isDark(s.theme)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            CustomerRegisterTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (s != null) {
                        val target by openCustomerId.collectAsState()
                        AppRoot(
                            canPost = canPost.value,
                            onRequestPermission = ::requestNotificationPermission,
                            openCustomerId = target,
                            onCustomerOpened = { openCustomerId.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canPost.value = NotificationHelper.canPost(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(NotificationHelper.EXTRA_CUSTOMER_ID, -1L) ?: -1L
        if (id > 0) {
            openCustomerId.value = id
            intent?.removeExtra(NotificationHelper.EXTRA_CUSTOMER_ID)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) || !hasAskedBefore()) {
                markAsked()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Permission permanently denied or notifications switched off: open the system page.
        if (!NotificationHelper.canPost(this)) openNotificationSettings()
    }

    private fun prefs() = getSharedPreferences("permission_state", MODE_PRIVATE)
    private fun hasAskedBefore() = prefs().getBoolean("asked_notifications", false)
    private fun markAsked() = prefs().edit().putBoolean("asked_notifications", true).apply()

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}

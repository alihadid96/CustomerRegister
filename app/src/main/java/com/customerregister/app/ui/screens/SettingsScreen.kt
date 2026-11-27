@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.customerregister.app.ui.screens

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.BuildConfig
import com.customerregister.app.R
import com.customerregister.app.data.AppSettings
import com.customerregister.app.data.ThemeMode
import com.customerregister.app.domain.MessageTemplate
import com.customerregister.app.ui.SettingsViewModel
import com.customerregister.app.ui.appViewModel
import com.customerregister.app.ui.components.AppTopBar
import com.customerregister.app.ui.components.ConfirmDialog
import com.customerregister.app.util.Formats
import com.customerregister.app.util.applyAppLanguage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EditDialog { NONE, ADD_DAY, THRESHOLD, COUNTRY, REMINDER_TEMPLATE, RENEWAL_TEMPLATE }

@Composable
fun SettingsScreen(canPost: Boolean, onRequestPermission: () -> Unit) {
    val vm = appViewModel("settings") { c, ctx -> SettingsViewModel(c, ctx) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf(EditDialog.NONE) }
    var batteryOk by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { batteryOk = isIgnoringBatteryOptimizations(context) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }

    val stamp = remember { DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT).format(java.time.LocalDateTime.now()) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> uri?.let(vm::exportXlsx) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::backup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::prepareRestore)
    }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            when {
                picked == null -> vm.setSound(AppSettings.SOUND_SILENT, context.getString(R.string.sound_silent))
                RingtoneManager.isDefault(picked) -> vm.setSound("", "")
                else -> vm.setSound(
                    picked.toString(),
                    RingtoneManager.getRingtone(context, picked)?.getTitle(context) ?: picked.lastPathSegment.orEmpty()
                )
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_settings)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------------- Appearance ----------------
            SettingsCard(stringResource(R.string.section_appearance)) {
                LabeledRow(Icons.Rounded.DarkMode, stringResource(R.string.theme))
                val themes = listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LabeledRow(Icons.Rounded.Language, stringResource(R.string.language))
                val langs = listOf("" to R.string.language_system, "en" to R.string.language_english, "ar" to R.string.language_arabic)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    langs.forEachIndexed { i, (tag, label) ->
                        SegmentedButton(
                            selected = s.language == tag,
                            onClick = {
                                if (s.language != tag) {
                                    vm.setLanguage(tag)
                                    applyAppLanguage(tag)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, langs.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }

            // ---------------- Notifications ----------------
            SettingsCard(stringResource(R.string.section_notifications)) {
                SwitchRow(
                    Icons.Rounded.Notifications,
                    stringResource(R.string.enable_notifications),
                    stringResource(R.string.enable_notifications_sub),
                    s.notificationsEnabled,
                ) { vm.setNotificationsEnabled(it) }

                if (!canPost) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.notif_permission_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRequestPermission) { Text(stringResource(R.string.allow)) }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LabeledRow(Icons.Rounded.NotificationsActive, stringResource(R.string.reminder_intervals), stringResource(R.string.reminder_intervals_sub))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.reminderDays.sortedDescending().forEach { d ->
                        InputChip(
                            selected = true,
                            onClick = { vm.removeReminderDay(d) },
                            label = { Text(context.resources.getQuantityString(R.plurals.days_before, d, d)) },
                            trailingIcon = { Icon(Icons.Rounded.Close, stringResource(R.string.remove), Modifier.size(18.dp)) },
                            enabled = s.notificationsEnabled,
                        )
                    }
                    AssistChip(
                        onClick = { dialog = EditDialog.ADD_DAY },
                        label = { Text(stringResource(R.string.add_interval)) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                        enabled = s.notificationsEnabled,
                    )
                }
                if (s.reminderDays != AppSettings.DEFAULT_REMINDER_DAYS) {
                    TextButton(onClick = { vm.resetReminderDays() }) { Text(stringResource(R.string.reset_default_intervals)) }
                }
                SwitchRow(
                    Icons.Rounded.Event,
                    stringResource(R.string.notify_expiry_day),
                    stringResource(R.string.notify_expiry_day_sub),
                    s.notifyOnExpiryDay,
                    enabled = s.notificationsEnabled,
                ) { vm.setNotifyOnExpiryDay(it) }
                ClickRow(
                    Icons.Rounded.Schedule,
                    stringResource(R.string.reminder_time),
                    String.format(Locale.ROOT, "%02d:%02d", s.reminderHour, s.reminderMinute),
                    enabled = s.notificationsEnabled,
                ) {
                    TimePickerDialog(
                        context,
                        { _, h, m -> vm.setReminderTime(h, m) },
                        s.reminderHour, s.reminderMinute,
                        DateFormat.is24HourFormat(context)
                    ).show()
                }
                ClickRow(
                    Icons.Rounded.MusicNote,
                    stringResource(R.string.notification_sound),
                    when (s.soundUri) {
                        "" -> stringResource(R.string.sound_default)
                        AppSettings.SOUND_SILENT -> stringResource(R.string.sound_silent)
                        else -> s.soundName.ifBlank { stringResource(R.string.sound_custom) }
                    },
                    enabled = s.notificationsEnabled,
                ) {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.notification_sound))
                        val existing: Uri? = when (s.soundUri) {
                            "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            AppSettings.SOUND_SILENT -> null
                            else -> Uri.parse(s.soundUri)
                        }
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                    }
                    runCatching { ringtoneLauncher.launch(intent) }
                }
                OutlinedButton(
                    onClick = { vm.sendTest(context) },
                    enabled = s.notificationsEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(R.string.send_test_notification)) }
            }

            // ---------------- Status thresholds ----------------
            SettingsCard(stringResource(R.string.section_status)) {
                ClickRow(
                    Icons.Rounded.HourglassBottom,
                    stringResource(R.string.expiring_threshold),
                    context.resources.getQuantityString(R.plurals.n_days, s.expiringThreshold, s.expiringThreshold),
                ) { dialog = EditDialog.THRESHOLD }
                Text(
                    stringResource(R.string.expiring_threshold_sub, s.expiringThreshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- Battery ----------------
            SettingsCard(stringResource(R.string.section_reliability)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.BatteryAlert, null,
                        tint = if (batteryOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (batteryOk) R.string.battery_ok else R.string.battery_restricted),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.battery_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!batteryOk) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(stringResource(R.string.open_battery_settings)) }
                }
            }

            // ---------------- WhatsApp ----------------
            SettingsCard(stringResource(R.string.section_whatsapp)) {
                ClickRow(
                    Icons.AutoMirrored.Rounded.Chat,
                    stringResource(R.string.reminder_template),
                    s.reminderTemplate.ifBlank { stringResource(R.string.default_reminder_template) }.lines().first(),
                ) { dialog = EditDialog.REMINDER_TEMPLATE }
                ClickRow(
                    Icons.Rounded.Autorenew,
                    stringResource(R.string.renewal_template),
                    s.renewalTemplate.ifBlank { stringResource(R.string.default_renewal_template) }.lines().first(),
                ) { dialog = EditDialog.RENEWAL_TEMPLATE }
                ClickRow(
                    Icons.Rounded.Phone,
                    stringResource(R.string.default_country_code),
                    if (s.defaultCountryCode.isBlank()) stringResource(R.string.none) else "+" + s.defaultCountryCode,
                ) { dialog = EditDialog.COUNTRY }
            }

            // ---------------- Data ----------------
            SettingsCard(stringResource(R.string.section_data)) {
                ClickRow(Icons.Rounded.Description, stringResource(R.string.export_csv), stringResource(R.string.export_csv_sub)) {
                    csvLauncher.launch("customers_$stamp.csv")
                }
                ClickRow(Icons.Rounded.TableChart, stringResource(R.string.export_xlsx), stringResource(R.string.export_xlsx_sub)) {
                    xlsxLauncher.launch("customers_$stamp.xlsx")
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ClickRow(Icons.Rounded.Backup, stringResource(R.string.backup), stringResource(R.string.backup_sub)) {
                    backupLauncher.launch("CustomerRegister_backup_$stamp.json")
                }
                ClickRow(Icons.Rounded.Restore, stringResource(R.string.restore), stringResource(R.string.restore_sub)) {
                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                }
            }

            // ---------------- About ----------------
            SettingsCard(stringResource(R.string.section_about)) {
                LabeledRow(Icons.Rounded.Info, stringResource(R.string.app_name), stringResource(R.string.version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                LabeledRow(Icons.Rounded.Security, stringResource(R.string.privacy), stringResource(R.string.privacy_text))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------------- Dialogs ----------------
    val s = settings ?: return
    when (dialog) {
        EditDialog.ADD_DAY -> NumberDialog(
            title = stringResource(R.string.add_interval),
            label = stringResource(R.string.days_before_expiry),
            initial = "",
            range = 1..365,
            onDismiss = { dialog = EditDialog.NONE },
        ) { vm.addReminderDay(it) }
        EditDialog.THRESHOLD -> NumberDialog(
            title = stringResource(R.string.expiring_threshold),
            label = stringResource(R.string.days),
            initial = s.expiringThreshold.toString(),
            range = 1..90,
            onDismiss = { dialog = EditDialog.NONE },
        ) { vm.setThreshold(it) }
        EditDialog.COUNTRY -> TextDialog(
            title = stringResource(R.string.default_country_code),
            label = stringResource(R.string.country_code_label),
            initial = s.defaultCountryCode,
            numeric = true,
            onDismiss = { dialog = EditDialog.NONE },
        ) { vm.setCountryCode(it) }
        EditDialog.REMINDER_TEMPLATE -> TemplateDialog(
            title = stringResource(R.string.reminder_template),
            initial = s.reminderTemplate.ifBlank { stringResource(R.string.default_reminder_template) },
            defaultText = stringResource(R.string.default_reminder_template),
            onDismiss = { dialog = EditDialog.NONE },
        ) { t, isDefault -> vm.setReminderTemplate(if (isDefault) "" else t) }
        EditDialog.RENEWAL_TEMPLATE -> TemplateDialog(
            title = stringResource(R.string.renewal_template),
            initial = s.renewalTemplate.ifBlank { stringResource(R.string.default_renewal_template) },
            defaultText = stringResource(R.string.default_renewal_template),
            onDismiss = { dialog = EditDialog.NONE },
        ) { t, isDefault -> vm.setRenewalTemplate(if (isDefault) "" else t) }
        EditDialog.NONE -> Unit
    }

    pendingRestore?.let { info ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(
                R.string.restore_confirm_text,
                info.customers,
                info.renewals,
                if (info.createdAt > 0) Formats.dateTime(info.createdAt) else "—",
            ),
            confirmLabel = stringResource(R.string.restore),
            destructive = true,
            onConfirm = { vm.confirmRestore { lang -> applyAppLanguage(lang) } },
            onDismiss = { vm.pendingRestore.value = null },
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun IconBox(icon: ImageVector, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.05f))
    ) {
        Icon(
            icon, null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LabeledRow(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon, enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon, enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initial: String,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.toIntOrNull()
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                label = { Text(label) },
                supportingText = { Text("${range.first} – ${range.last}") },
                isError = text.isNotEmpty() && !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            Button(onClick = { value?.let(onConfirm); onDismiss() }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String,
    numeric: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = if (numeric) v.filter { it.isDigit() }.take(4) else v },
                label = { Text(label) },
                prefix = { if (numeric) Text("+") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TemplateDialog(
    title: String,
    initial: String,
    defaultText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val preview = MessageTemplate.render(text, "Ahmad", "1024", Formats.date(LocalDate.now().plusDays(3)), 3)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.template_variables), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageTemplate.VARIABLES.forEach { v ->
                        AssistChip(onClick = { text += v }, label = { Text(v) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelMedium)
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { text = defaultText }) { Text(stringResource(R.string.reset_to_default)) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, text.trim() == defaultText.trim()); onDismiss() }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

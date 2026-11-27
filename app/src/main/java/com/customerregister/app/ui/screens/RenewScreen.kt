@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.customerregister.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.R
import com.customerregister.app.data.db.CustomerEntity
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.domain.SubscriptionMath
import com.customerregister.app.ui.RenewMode
import com.customerregister.app.ui.RenewViewModel
import com.customerregister.app.ui.appViewModel
import com.customerregister.app.ui.components.AppTopBar
import com.customerregister.app.ui.components.DateField
import com.customerregister.app.ui.components.StatusBadge
import com.customerregister.app.ui.components.WhatsAppComposerDialog
import com.customerregister.app.ui.components.WhatsAppGreen
import com.customerregister.app.util.Formats
import com.customerregister.app.util.WhatsApp
import com.customerregister.app.util.durationLabel
import com.customerregister.app.util.remainingLabel
import java.time.LocalDate

@Composable
fun RenewScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "renew_$id") { c, _ -> RenewViewModel(c, id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renewed by remember { mutableStateOf<CustomerEntity?>(null) }
    var whatsApp by remember { mutableStateOf<CustomerEntity?>(null) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.renew_subscription), onBack = onBack) }) { padding ->
        val c = s.customer
        val st = settings
        if (c == null || st == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val today = LocalDate.now()
        val expired = c.expiryDate.isBefore(today)
        val newExpiry = s.newExpiry(today)

        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.customer_id_value, Formats.customerId(c.id)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.current_expiry, Formats.date(c.expiryDate)),
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(SubscriptionMath.status(c.expiryDate, today, st.expiringThreshold))
                    }
                }
            }

            Text(stringResource(R.string.choose_duration), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 3, 6, 12).forEach { m ->
                    FilterChip(
                        selected = s.mode == RenewMode.PRESET && s.presetMonths == m,
                        onClick = { vm.update { it.copy(mode = RenewMode.PRESET, presetMonths = m) } },
                        label = { Text(context.resources.getQuantityString(R.plurals.n_months, m, m)) },
                    )
                }
                FilterChip(
                    selected = s.mode == RenewMode.CUSTOM_DURATION,
                    onClick = { vm.update { it.copy(mode = RenewMode.CUSTOM_DURATION) } },
                    label = { Text(stringResource(R.string.custom_duration)) },
                )
                FilterChip(
                    selected = s.mode == RenewMode.CUSTOM_DATE,
                    onClick = { vm.update { it.copy(mode = RenewMode.CUSTOM_DATE) } },
                    label = { Text(stringResource(R.string.custom_date)) },
                )
            }

            AnimatedVisibility(s.mode == RenewMode.CUSTOM_DURATION) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = s.durationValue,
                        onValueChange = { v -> vm.update { it.copy(durationValue = v.filter { ch -> ch.isDigit() }.take(4)) } },
                        label = { Text(stringResource(R.string.field_duration_value)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DurationUnitSelector(s.durationUnit) { u -> vm.update { it.copy(durationUnit = u) } }
                }
            }
            AnimatedVisibility(s.mode == RenewMode.CUSTOM_DATE) {
                DateField(
                    label = stringResource(R.string.new_expiry),
                    date = s.customDate,
                    icon = Icons.Rounded.EventAvailable,
                    onDateChange = { d -> vm.update { it.copy(customDate = d) } },
                )
            }

            if (s.mode != RenewMode.CUSTOM_DATE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.start_from_today), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (expired) R.string.start_from_today_expired else R.string.start_from_today_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.fromToday || expired,
                        enabled = !expired,
                        onCheckedChange = { v -> vm.update { it.copy(fromToday = v) } },
                    )
                }
            }

            // Preview
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_expiry), style = MaterialTheme.typography.labelMedium)
                        Text(Formats.date(c.expiryDate), style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.new_expiry), style = MaterialTheme.typography.labelMedium)
                        Text(
                            newExpiry?.let { Formats.date(it) } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (newExpiry != null) {
                            Text(
                                context.remainingLabel(SubscriptionMath.remainingDays(newExpiry, today)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = s.note,
                onValueChange = { v -> vm.update { it.copy(note = v) } },
                label = { Text(stringResource(R.string.renewal_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { vm.confirm { renewed = it } },
                enabled = newExpiry != null && !s.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Rounded.Autorenew, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.confirm_renewal))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val r = renewed
    if (r != null) {
        AlertDialog(
            onDismissRequest = { renewed = null; onBack() },
            icon = { Icon(Icons.Rounded.TaskAlt, null, tint = WhatsAppGreen) },
            title = { Text(stringResource(R.string.renewed_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.renewed_text, r.name, Formats.date(r.expiryDate),
                        context.durationLabel(r.durationValue, r.durationUnit)
                    )
                )
            },
            confirmButton = {
                Button(onClick = { whatsApp = r; renewed = null }) { Text(stringResource(R.string.send_confirmation)) }
            },
            dismissButton = {
                TextButton(onClick = { renewed = null; onBack() }) { Text(stringResource(R.string.done)) }
            },
        )
    }
    val w = whatsApp
    val st = settings
    if (w != null && st != null) {
        WhatsAppComposerDialog(
            customer = w,
            initialMessage = WhatsApp.renewalMessage(context, st, w),
            onDismiss = { whatsApp = null; onBack() },
        )
    }
}

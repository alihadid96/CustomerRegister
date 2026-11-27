@file:OptIn(ExperimentalMaterial3Api::class)

package com.customerregister.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.R
import com.customerregister.app.data.db.RenewalEntity
import com.customerregister.app.data.db.createdDate
import com.customerregister.app.data.db.expiryDate
import com.customerregister.app.data.db.remainingDays
import com.customerregister.app.data.db.startDate
import com.customerregister.app.data.db.status
import com.customerregister.app.domain.PhoneNumbers
import com.customerregister.app.domain.ReminderPlanner
import com.customerregister.app.ui.CustomerDetailViewModel
import com.customerregister.app.ui.appViewModel
import com.customerregister.app.ui.components.AppTopBar
import com.customerregister.app.ui.components.ConfirmDialog
import com.customerregister.app.ui.components.InfoRow
import com.customerregister.app.ui.components.SectionHeader
import com.customerregister.app.ui.components.StatusBadge
import com.customerregister.app.ui.components.WhatsAppComposerDialog
import com.customerregister.app.ui.theme.Brand
import com.customerregister.app.ui.theme.statusColor
import com.customerregister.app.util.Formats
import com.customerregister.app.util.WhatsApp
import com.customerregister.app.util.durationLabel
import com.customerregister.app.util.remainingLabel
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun CustomerDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRenew: (Long) -> Unit,
) {
    val vm = appViewModel(key = "detail_$id") { c, _ -> CustomerDetailViewModel(c, id) }
    val customer by vm.customer.collectAsStateWithLifecycle()
    val renewals by vm.renewals.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showWhatsApp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.customer_details), onBack = onBack) {
                if (customer != null) {
                    IconButton(onClick = { onEdit(id) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                }
            }
        },
    ) { padding ->
        val c = customer
        val s = settings
        if (c == null || s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val status = c.status(s.expiringThreshold)
        val remaining = c.remainingDays()
        val color = statusColor(status)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero card
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Brand.Navy, color.copy(alpha = 0.85f))))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                                ) {
                                    Text(
                                        c.name.trim().take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                    Text(
                                        stringResource(R.string.customer_id_value, Formats.customerId(c.id)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (remaining >= 0) remaining.toString() else "0",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = Color.White,
                                    )
                                    Text(
                                        context.remainingLabel(remaining),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                                androidx.compose.material3.Surface(shape = CircleShape, color = Color.White) {
                                    StatusBadge(status, Modifier.padding(2.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Actions
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showWhatsApp = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C4A), contentColor = Color.White),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_whatsapp), maxLines = 1)
                    }
                    Button(
                        onClick = { onRenew(id) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Rounded.Autorenew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.renew), maxLines = 1)
                    }
                }
            }

            // Info
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        InfoRow(Icons.Rounded.Badge, stringResource(R.string.col_id), Formats.customerId(c.id))
                        InfoRow(Icons.Rounded.Phone, stringResource(R.string.field_phone), PhoneNumbers.display(c.phone), ltrValue = true)
                        InfoRow(Icons.Rounded.Event, stringResource(R.string.field_start), Formats.date(c.startDate))
                        InfoRow(Icons.Rounded.EventBusy, stringResource(R.string.field_expiry), Formats.date(c.expiryDate))
                        InfoRow(Icons.Rounded.Timer, stringResource(R.string.field_duration), context.durationLabel(c.durationValue, c.durationUnit))
                        InfoRow(Icons.Rounded.Schedule, stringResource(R.string.customer_since), Formats.date(c.createdDate))
                        if (c.notes.isNotBlank()) {
                            InfoRow(Icons.Rounded.Description, stringResource(R.string.field_notes), c.notes)
                        }
                    }
                }
            }

            // Reminder schedule for this customer
            item {
                val config = s.reminderConfig
                val now = LocalDateTime.now()
                val offsets = ReminderPlanner.offsets(config)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reminder_schedule), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!s.notificationsEnabled) {
                            Text(stringResource(R.string.notifications_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (offsets.isEmpty()) {
                            Text(stringResource(R.string.no_reminders_configured), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            offsets.forEach { d ->
                                val at = c.expiryDate.minusDays(d.toLong()).atTime(config.time)
                                val sent = reminders.any { it.expiryEpochDay == c.expiryEpochDay && it.daysBefore == d }
                                val passed = !at.isAfter(now)
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(8.dp).clip(CircleShape).background(
                                            when {
                                                sent -> Brand.Green
                                                passed -> MaterialTheme.colorScheme.outline
                                                else -> Brand.Electric
                                            }
                                        )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (d == 0) stringResource(R.string.on_expiry_day)
                                        else context.resources.getQuantityString(R.plurals.days_before, d, d),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        Formats.date(at.toLocalDate()) + "  " +
                                            stringResource(
                                                when {
                                                    sent -> R.string.reminder_sent
                                                    passed -> R.string.reminder_skipped
                                                    else -> R.string.reminder_scheduled
                                                }
                                            ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Renewal history
            item {
                SectionHeader(stringResource(R.string.renewal_history))
            }
            if (renewals.isEmpty()) {
                item { Text(stringResource(R.string.no_renewals), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(renewals, key = { it.id }) { r -> RenewalRow(r) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val c = customer
    val s = settings
    if (confirmDelete && c != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_customer_title),
            text = stringResource(R.string.delete_customer_text, c.name),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = { vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (showWhatsApp && c != null && s != null) {
        WhatsAppComposerDialog(
            customer = c,
            initialMessage = WhatsApp.reminderMessage(context, s, c),
            onDismiss = { showWhatsApp = false },
        )
    }
}

@Composable
private fun RenewalRow(r: RenewalEntity) {
    val context = LocalContext.current
    val initial = r.note == RenewalEntity.NOTE_INITIAL
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background((if (initial) Brand.Electric else Brand.Green).copy(alpha = 0.15f))
            ) {
                Icon(
                    if (initial) Icons.Rounded.History else Icons.Rounded.Autorenew,
                    null,
                    tint = if (initial) Brand.Electric else Brand.Green,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (initial) stringResource(R.string.history_initial) else stringResource(R.string.history_renewed),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${Formats.date(LocalDate.ofEpochDay(r.previousExpiryEpochDay))} → ${Formats.date(LocalDate.ofEpochDay(r.newExpiryEpochDay))}",
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                )
                Text(
                    "${context.durationLabel(r.durationValue, r.durationUnit)} · ${Formats.dateTime(r.renewedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!initial && r.note.isNotBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(r.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

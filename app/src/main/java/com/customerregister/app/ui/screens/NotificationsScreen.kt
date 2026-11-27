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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.R
import com.customerregister.app.data.db.NotificationLogEntity
import com.customerregister.app.ui.MainViewModel
import com.customerregister.app.ui.components.AppTopBar
import com.customerregister.app.ui.components.ConfirmDialog
import com.customerregister.app.ui.components.EmptyState
import com.customerregister.app.ui.theme.Brand
import com.customerregister.app.util.Formats
import java.time.LocalDate

@Composable
fun NotificationsScreen(
    vm: MainViewModel,
    canPost: Boolean,
    onRequestPermission: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
) {
    val log by vm.notificationLog.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = items.orEmpty().map { it.customer.id }.toSet()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.nav_notifications)) {
                if (log.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_history))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!canPost || settings?.notificationsEnabled == false) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Brand.Orange.copy(alpha = 0.14f)),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsOff, null, tint = Brand.Orange)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (!canPost) R.string.notif_permission_title else R.string.notifications_disabled),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (!canPost) {
                                    Text(stringResource(R.string.notif_permission_needed), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (!canPost) {
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow)) }
                            }
                        }
                    }
                }
            }
            if (log.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.NotificationsNone,
                        stringResource(R.string.no_notifications_title),
                        stringResource(R.string.no_notifications_subtitle),
                    )
                }
            }
            items(log, key = { it.id }) { entry ->
                LogRow(entry, exists = entry.customerId in existing) { onOpenCustomer(entry.customerId) }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history),
            text = stringResource(R.string.clear_history_text),
            confirmLabel = stringResource(R.string.clear),
            destructive = true,
            onConfirm = { vm.clearNotificationHistory() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun LogRow(entry: NotificationLogEntity, exists: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val color = if (entry.remainingDays <= 1) Brand.Red else if (entry.remainingDays <= 3) Brand.Orange else Brand.Electric
    Card(
        onClick = onClick,
        enabled = exists,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f))
            ) { Icon(Icons.Rounded.NotificationsActive, null, tint = color, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${entry.customerName}  ·  #${entry.customerId}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        R.string.log_line,
                        Formats.date(LocalDate.ofEpochDay(entry.expiryEpochDay)),
                        if (entry.remainingDays <= 0) context.getString(R.string.expires_today)
                        else context.resources.getQuantityString(R.plurals.n_days, entry.remainingDays, entry.remainingDays),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    Formats.dateTime(entry.postedAt) + if (!exists) "  ·  " + context.getString(R.string.customer_deleted) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


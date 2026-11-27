@file:OptIn(ExperimentalMaterial3Api::class)

package com.customerregister.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.R
import com.customerregister.app.domain.StatusFilter
import com.customerregister.app.ui.MainViewModel
import com.customerregister.app.ui.components.Avatar
import com.customerregister.app.ui.components.CustomerItem
import com.customerregister.app.ui.components.CustomerRow
import com.customerregister.app.ui.components.EmptyState
import com.customerregister.app.ui.components.SectionHeader
import com.customerregister.app.ui.components.StatCard
import com.customerregister.app.ui.components.WhatsAppComposerDialog
import com.customerregister.app.ui.components.WhatsAppGreen
import com.customerregister.app.ui.theme.Brand
import com.customerregister.app.ui.theme.statusColor
import com.customerregister.app.util.WhatsApp
import com.customerregister.app.util.remainingLabel

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onAddCustomer: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenList: (StatusFilter) -> Unit,
) {
    val state by vm.dashboard.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var whatsAppFor by remember { mutableStateOf<CustomerItem?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCustomer,
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                text = { Text(stringResource(R.string.add_customer)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Header(
                    total = state.total,
                    onQuickWhatsApp = { showPicker = true },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            stringResource(R.string.stat_total), state.total, Icons.Rounded.Groups, Brand.Electric,
                            Modifier.weight(1f), onClick = { onOpenList(StatusFilter.ALL) }
                        )
                        StatCard(
                            stringResource(R.string.stat_active), state.active, Icons.Rounded.CheckCircle, Brand.Green,
                            Modifier.weight(1f), onClick = { onOpenList(StatusFilter.ACTIVE) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            stringResource(R.string.stat_expiring), state.expiring, Icons.Rounded.HourglassBottom, Brand.Orange,
                            Modifier.weight(1f), onClick = { onOpenList(StatusFilter.EXPIRING) }
                        )
                        StatCard(
                            stringResource(R.string.stat_expired), state.expired, Icons.Rounded.Error, Brand.Red,
                            Modifier.weight(1f), onClick = { onOpenList(StatusFilter.EXPIRED) }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OverviewCard(state.active, state.expiring, state.expired, state.weekly)
                }
            }

            item {
                SectionHeader(
                    stringResource(R.string.upcoming_expirations),
                    Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                ) {
                    TextButton(onClick = { onOpenList(StatusFilter.EXPIRING) }) { Text(stringResource(R.string.see_all)) }
                }
            }
            if (items != null && state.upcoming.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_upcoming),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(state.upcoming, key = { "up_${it.customer.id}" }) { item ->
                CustomerRow(
                    item,
                    onClick = { onOpenCustomer(item.customer.id) },
                    onWhatsApp = { whatsAppFor = item },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem(),
                )
            }

            item {
                SectionHeader(
                    stringResource(R.string.recently_added),
                    Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                ) {
                    TextButton(onClick = { onOpenList(StatusFilter.ALL) }) { Text(stringResource(R.string.see_all)) }
                }
            }
            if (items != null && state.recent.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Groups,
                        stringResource(R.string.empty_customers_title),
                        stringResource(R.string.empty_customers_subtitle),
                    )
                }
            }
            items(state.recent, key = { "re_${it.customer.id}" }) { item ->
                CustomerRow(
                    item,
                    onClick = { onOpenCustomer(item.customer.id) },
                    onWhatsApp = { whatsAppFor = item },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem(),
                )
            }
        }
    }

    if (showPicker) {
        CustomerPickerSheet(
            items = items.orEmpty().sortedWith(compareBy<CustomerItem>({ it.remaining < 0 }, { kotlin.math.abs(it.remaining) })),
            onDismiss = { showPicker = false },
            onPick = { showPicker = false; whatsAppFor = it },
        )
    }
    val target = whatsAppFor
    val s = settings
    if (target != null && s != null) {
        WhatsAppComposerDialog(
            customer = target.customer,
            initialMessage = WhatsApp.reminderMessage(context, s, target.customer),
            onDismiss = { whatsAppFor = null },
        )
    }
}

@Composable
private fun Header(total: Int, onQuickWhatsApp: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brand.headerGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                stringResource(R.string.dashboard_subtitle, total),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onQuickWhatsApp,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quick_whatsapp))
            }
        }
    }
}

@Composable
private fun OverviewCard(active: Int, expiring: Int, expired: Int, weekly: List<Int>) {
    val total = (active + expiring + expired).coerceAtLeast(1)
    val a by animateFloatAsState(active / total.toFloat(), tween(800), label = "a")
    val e by animateFloatAsState(expiring / total.toFloat(), tween(800), label = "e")
    val x by animateFloatAsState(expired / total.toFloat(), tween(800), label = "x")
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.expiration_overview), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (a > 0f) Box(Modifier.weight(a).fillMaxHeight().background(Brand.Green))
                if (e > 0f) Box(Modifier.weight(e).fillMaxHeight().background(Brand.Orange))
                if (x > 0f) Box(Modifier.weight(x).fillMaxHeight().background(Brand.Red))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(Brand.Green, stringResource(R.string.stat_active), active)
                Legend(Brand.Orange, stringResource(R.string.stat_expiring), expiring)
                Legend(Brand.Red, stringResource(R.string.stat_expired), expired)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.next_8_weeks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val max = (weekly.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weekly.forEachIndexed { i, count ->
                    val frac by animateFloatAsState(count / max.toFloat(), tween(800), label = "w$i")
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            if (count > 0) count.toString() else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((4 + 60 * frac).dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (i == 0) Brand.Orange
                                    else if (count > 0) Brand.Electric else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.week_short, i + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label $value", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bottom sheet to pick a customer for a quick WhatsApp message. */
@Composable
fun CustomerPickerSheet(items: List<CustomerItem>, onDismiss: () -> Unit, onPick: (CustomerItem) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.quick_whatsapp), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        val shown = items.filter {
            q.isBlank() || it.customer.name.contains(q.trim(), true) ||
                it.customer.id.toString().contains(q.trim()) || it.customer.phone.contains(q.filter { c -> c.isDigit() }.ifEmpty { "\u0000" })
        }
        LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
            items(shown, key = { it.customer.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.customer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("#${item.customer.id} · ${context.remainingLabel(item.remaining)}") },
                    leadingContent = { Avatar(item.customer.name, statusColor(item.status), 40) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = WhatsAppGreen) },
                    modifier = Modifier.clickable { onPick(item) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

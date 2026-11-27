@file:OptIn(ExperimentalMaterial3Api::class)

package com.customerregister.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.R
import com.customerregister.app.domain.SortOrder
import com.customerregister.app.domain.StatusFilter
import com.customerregister.app.ui.MainViewModel
import com.customerregister.app.ui.components.AppTopBar
import com.customerregister.app.ui.components.CustomerItem
import com.customerregister.app.ui.components.CustomerRow
import com.customerregister.app.ui.components.EmptyState
import com.customerregister.app.ui.components.WhatsAppComposerDialog
import com.customerregister.app.util.WhatsApp

@Composable
fun CustomersScreen(
    vm: MainViewModel,
    onAddCustomer: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
) {
    val list by vm.filtered.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sortMenu by remember { mutableStateOf(false) }
    var whatsAppFor by remember { mutableStateOf<CustomerItem?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.nav_customers)) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(
                            SortOrder.EXPIRATION to R.string.sort_expiration,
                            SortOrder.NAME to R.string.sort_name,
                            SortOrder.START_DATE to R.string.sort_start,
                            SortOrder.RECENT to R.string.sort_recent,
                        ).forEach { (order, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                onClick = { vm.sort.value = order; sortMenu = false },
                                trailingIcon = { if (sort == order) Icon(Icons.Rounded.Check, null) },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCustomer,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.add_customer)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    StatusFilter.ALL to R.string.filter_all,
                    StatusFilter.ACTIVE to R.string.status_active,
                    StatusFilter.EXPIRING to R.string.status_expiring,
                    StatusFilter.EXPIRED to R.string.status_expired,
                ).forEach { (f, label) ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.filter.value = f },
                        label = { Text(stringResource(label)) },
                        leadingIcon = if (filter == f) ({ Icon(Icons.Rounded.Check, null, Modifier.padding(0.dp)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
            val current = list
            when {
                current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                current.isEmpty() -> EmptyState(
                    if (query.isBlank() && filter == StatusFilter.ALL) Icons.Rounded.PersonAdd else Icons.Rounded.SearchOff,
                    stringResource(if (query.isBlank() && filter == StatusFilter.ALL) R.string.empty_customers_title else R.string.no_results),
                    stringResource(if (query.isBlank() && filter == StatusFilter.ALL) R.string.empty_customers_subtitle else R.string.no_results_subtitle),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.results_count, current.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(current, key = { it.customer.id }) { item ->
                        CustomerRow(
                            item,
                            onClick = { onOpenCustomer(item.customer.id) },
                            onWhatsApp = { whatsAppFor = item },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.customerregister.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customerregister.app.R
import com.customerregister.app.domain.DurationUnit
import com.customerregister.app.domain.PhoneNumbers
import com.customerregister.app.domain.SubscriptionMath
import com.customerregister.app.ui.CustomerEditViewModel
import com.customerregister.app.ui.appViewModel
import com.customerregister.app.ui.components.AppTopBar
import com.customerregister.app.ui.components.DateField
import com.customerregister.app.ui.components.StatusBadge
import com.customerregister.app.util.Formats
import com.customerregister.app.util.remainingLabel
import java.time.LocalDate

@Composable
fun CustomerEditScreen(id: Long?, onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val vm = appViewModel(key = "edit_${id ?: "new"}") { c, _ -> CustomerEditViewModel(c, id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(if (id == null) R.string.add_customer else R.string.edit_customer),
                onBack = onBack,
            )
        },
    ) { padding ->
        if (!s.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = s.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.field_name)) },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                isError = s.showErrors && s.nameError,
                supportingText = if (s.showErrors && s.nameError) ({ Text(stringResource(R.string.error_name)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            val normalized = vm.normalizedPhone()
            val phoneError = s.showErrors && vm.phoneError()
            OutlinedTextField(
                value = s.phone,
                onValueChange = { v -> vm.update { it.copy(phone = v.filter { c -> c.isDigit() || c in "+ -()" }) } },
                label = { Text(stringResource(R.string.field_phone)) },
                leadingIcon = { Icon(Icons.Rounded.Phone, null) },
                isError = phoneError,
                supportingText = {
                    Text(
                        if (phoneError) stringResource(R.string.error_phone)
                        else if (normalized.isNotEmpty()) stringResource(R.string.phone_preview, PhoneNumbers.display(normalized))
                        else stringResource(R.string.phone_hint, vm.countryCode),
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = stringResource(R.string.field_start),
                date = s.start,
                icon = Icons.Rounded.Event,
                onDateChange = { d -> vm.update { it.copy(start = d) } },
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.field_duration), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.custom_expiry), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = s.customExpiry, onCheckedChange = { v ->
                            vm.update { it.copy(customExpiry = v, expiry = it.computedExpiry) }
                        })
                    }
                    AnimatedVisibility(!s.customExpiry) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = s.durationValue,
                                onValueChange = { v -> vm.update { it.copy(durationValue = v.filter { c -> c.isDigit() }.take(4)) } },
                                label = { Text(stringResource(R.string.field_duration_value)) },
                                isError = s.showErrors && s.durationError,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DurationUnitSelector(s.durationUnit) { u -> vm.update { it.copy(durationUnit = u) } }
                        }
                    }
                    AnimatedVisibility(s.customExpiry) {
                        DateField(
                            label = stringResource(R.string.field_expiry),
                            date = s.expiry,
                            icon = Icons.Rounded.EventAvailable,
                            onDateChange = { d -> vm.update { it.copy(expiry = d) } },
                        )
                    }
                }
            }

            // Live preview of the calculated expiration
            val expiry = s.computedExpiry
            val remaining = SubscriptionMath.remainingDays(expiry, LocalDate.now())
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.calculated_expiry),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            Formats.date(expiry),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            context.remainingLabel(remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    StatusBadge(SubscriptionMath.status(expiry, LocalDate.now(), vm.threshold))
                }
            }
            if (s.showErrors && s.expiryError) {
                Text(stringResource(R.string.error_expiry), color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = s.notes,
                onValueChange = { v -> vm.update { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.field_notes)) },
                leadingIcon = { Icon(Icons.Rounded.Description, null) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.save { newId -> onSaved(newId, id == null) } },
                enabled = !s.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DurationUnitSelector(selected: DurationUnit, onSelect: (DurationUnit) -> Unit) {
    val units = listOf(
        DurationUnit.DAYS to R.string.unit_days,
        DurationUnit.WEEKS to R.string.unit_weeks,
        DurationUnit.MONTHS to R.string.unit_months,
        DurationUnit.YEARS to R.string.unit_years,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        units.forEachIndexed { i, (unit, label) ->
            SegmentedButton(
                selected = selected == unit,
                onClick = { onSelect(unit) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = units.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

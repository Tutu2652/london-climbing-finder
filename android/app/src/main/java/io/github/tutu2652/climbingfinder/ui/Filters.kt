package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.CheapestMode
import io.github.tutu2652.climbingfinder.core.London
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK).withZone(London)
val longDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK).withZone(London)
val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(London)

/** Toolbar button that opens the climber, type, zone and air-con filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterButton() {
    val model = LocalModel.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        BadgedBox(badge = { if (model.filtersActive) Badge() }) {
            Icon(if (model.filtersActive) Icons.Filled.FilterList else Icons.Outlined.FilterList, "Filters")
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favourites only", modifier = Modifier.weight(1f))
                    Switch(model.favouritesOnly, { model.chooseFavouritesOnly(it) })
                }
                Text("Climber", style = MaterialTheme.typography.labelLarge)
                ClimberPicker()
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Segmented(listOf("", "bouldering", "ropes"), model.typeFilter,
                    { mapOf("" to "Any type", "bouldering" to "Bouldering", "ropes" to "Ropes").getValue(it) }) { model.typeFilter = it }
                Text("Zone", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(model.zoneFilter == null, { model.zoneFilter = null }, { Text("Any") })
                    for (z in model.zones) FilterChip(model.zoneFilter == z, { model.zoneFilter = z }, { Text("$z") })
                }
                Text("Air-conditioning", style = MaterialTheme.typography.labelLarge)
                Segmented(listOf("", "ac", "full"), model.acFilter,
                    { mapOf("" to "All gyms", "ac" to "Any AC", "full" to "Full AC").getValue(it) }) { model.acFilter = it }
                if (model.filtersActive) {
                    TextButton(onClick = { model.clearFilters() }) { Text("Clear filters", color = Warn) }
                }
            }
        }
    }
}

/** Adult vs student/concession prices. Shown wherever prices are, so it's never a surprise. */
@Composable
fun ClimberPicker() {
    val model = LocalModel.current
    Segmented(listOf(false, true), model.concession, { if (it) "Student / conc." else "Adult" }) { model.chooseConcession(it) }
}

/** Date, time and adult/concession controls shown at the top of Cheapest and gym pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenControls(showMode: Boolean = true) {
    val model = LocalModel.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val local = model.whenAt.atZone(London)
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }) { Text(dayFormat.format(model.whenAt)) }
            OutlinedButton(onClick = { pickTime = true }) { Text(timeFormat.format(model.whenAt)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { model.resetToNow() }, enabled = model.whenPinned) { Text("Now") }
        }
        ClimberPicker()
        if (showMode) {
            Segmented(CheapestMode.entries, model.mode, { it.title }) { model.mode = it }
        }
        if (model.filtersActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(filterSummary(), style = MaterialTheme.typography.bodySmall, color = secondaryText, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.clearFilters() }) { Text("Clear") }
            }
        }
    }
    // Picking a date or time stops the controls following the clock until you tap Now.
    if (pickDate) {
        val state = rememberDatePickerState(local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        model.pickWhen(local.with(d).toInstant())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(local.hour, local.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    model.pickWhen(local.withHour(state.hour).withMinute(state.minute).toInstant())
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun filterSummary(): String {
    val model = LocalModel.current
    val parts = mutableListOf<String>()
    if (model.favouritesOnly) parts.add("favourites")
    if (model.typeFilter.isNotEmpty()) parts.add(model.typeFilter)
    model.zoneFilter?.let { parts.add("zone $it") }
    if (model.acFilter.isNotEmpty()) parts.add(if (model.acFilter == "full") "full AC" else "AC")
    return "Showing " + parts.joinToString(" · ")
}

fun LocalDate.isToday(): Boolean = this == LocalDate.now(London)

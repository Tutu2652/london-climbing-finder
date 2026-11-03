package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.CheapestMode
import io.github.tutu2652.climbingfinder.core.acLevel
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.openState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheapestScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    Screen("Cheapest", back = false, actions = { FilterButton() }, body = { padding ->
        val rows = model.cheapest()
        PullToRefreshBox(refreshing, onRefresh = {
            scope.launch { refreshing = true; model.refreshData(force = true); model.refreshTravel(); refreshing = false }
        }, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                section(key = "when") { WhenControls() }
                if (model.isBankHoliday) {
                    section(key = "bh") {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, null, tint = Amber)
                            Text("${longDayFormat.format(model.whenAt)} is a UK bank holiday, so some off-peak, Magic Time and Bargain deals don't apply.",
                                style = MaterialTheme.typography.bodySmall, color = Amber)
                        }
                    }
                }
                section(key = "rows", footer = footer(model)) {
                    if (rows.isEmpty()) RowItem(divider = false) { Text("No priced gyms match these filters.", color = secondaryText) }
                    rows.forEachIndexed { i, r ->
                        Column(if (i == 0) Modifier.background(Brand.copy(alpha = 0.10f)) else Modifier) {
                            RowItem(onClick = { nav.navigate("gym/${r.gym.id}") }, divider = i > 0) { CheapestRow(i + 1, r) }
                        }
                    }
                }
            }
        }
    })
}

private fun footer(model: AppModel): String {
    val who = model.climberLabel
    val day = dayFormat.format(model.whenAt)
    val lead = if (model.mode == CheapestMode.DAY)
        "$who prices: the cheapest you can get on $day. The time shown is when that price applies; \"also\" lines are other cheap options that day."
    else "$who prices at ${timeFormat.format(model.whenAt)} on $day. The time shown is how long that price lasts."
    return "$lead \"pre-book\" = book ahead. \"verify\" = price not confirmed when researched."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheapestRow(rank: Int, row: AppModel.Ranked) {
    val model = LocalModel.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, color = if (rank == 1) Brand else secondaryText,
            modifier = Modifier.widthIn(min = 22.dp))
        GymLogo(row.gym, 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.gym.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                FavouriteMark(row.gym.id)
                VerifyFlag(row.gym)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatPrice(row.price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Pill(row.label, row.kind.color)
            }
            if (row.`when`.isNotEmpty()) IconText(Icons.Filled.Schedule, row.`when`, MaterialTheme.typography.bodyMedium)
            for (o in row.alts) {
                Text("also ${formatPrice(o.price)} ${o.label} · ${o.`when`}", style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                OpenBadge(row.gym.openState(model.moment))
                Text("Zone ${row.gym.zone}", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                model.travelText(row.gym)?.let { IconText(Icons.Filled.Train, it) }
                if (row.gym.acLevel != "none") {
                    Icon(Icons.Filled.AcUnit, if (row.gym.acLevel == "full") "Full air-conditioning" else "Some air-conditioning",
                        tint = secondaryText, modifier = Modifier.size(16.dp))
                }
            }
            NoteSummary(row.gym.id)
        }
    }
}

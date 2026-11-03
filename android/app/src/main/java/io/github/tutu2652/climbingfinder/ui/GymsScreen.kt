package io.github.tutu2652.climbingfinder.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.acLevel
import io.github.tutu2652.climbingfinder.core.bestOffpeak
import io.github.tutu2652.climbingfinder.core.brandRank
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.peakPrice
import io.github.tutu2652.climbingfinder.core.typeLabel
import java.util.Locale

enum class GymSort(val title: String) {
    CHAIN("By chain"), PEAK("Cheapest — peak"), OFFPEAK("Cheapest — off-peak"), DISTANCE("Nearest first")
}

/** Gyms grouped into chain sections: LCC first, independents last. */
fun brandGroups(gyms: List<Gym>): List<Pair<String, List<Gym>>> =
    gyms.sortedWith(compareBy({ it.brandRank }, { it.brand }, { it.name }))
        .groupBy { it.brand }.toList()

@Composable
fun GymsScreen() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("climbing", Context.MODE_PRIVATE) }
    var sort by remember { mutableStateOf(runCatching { GymSort.valueOf(prefs.getString("gymSort", "CHAIN")!!) }.getOrDefault(GymSort.CHAIN)) }
    var search by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val filtered = filterGyms(model, search)
    Screen("All gyms", back = false, actions = {
        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
        DropdownMenu(sortMenu, { sortMenu = false }) {
            for (s in GymSort.entries) DropdownMenuItem(
                text = { Text(s.title) },
                leadingIcon = { RadioButton(s == sort, null) },
                onClick = { sort = s; prefs.edit().putString("gymSort", s.name).apply(); sortMenu = false },
            )
        }
        FilterButton()
    }) {
        item {
            OutlinedTextField(search, { search = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Gym, area, postcode or facility") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
        }
        section { Column(Modifier.padding(14.dp)) { ClimberPicker() } }
        if (sort == GymSort.CHAIN) {
            val favs = filtered.filter { model.isFavourite(it.id) }.sortedBy { it.name }
            if (search.isEmpty() && !model.favouritesOnly && favs.isNotEmpty()) {
                section("Favourites") { favs.forEachIndexed { i, g -> GymRow(g, i > 0) } }
            }
            val groups = filtered.sortedWith(compareBy({ it.brandRank }, { it.brand }, { it.zone }, { it.name })).groupBy { it.brand }
            for ((brand, gyms) in groups) section(brand) { gyms.forEachIndexed { i, g -> GymRow(g, i > 0) } }
        } else {
            section {
                if (sort == GymSort.DISTANCE && model.origin == null) {
                    val nav = LocalNav.current
                    RowItem(onClick = { nav.navigate("origin") }, divider = false) {
                        Text("Set your location to sort by distance", color = MaterialTheme.colorScheme.primary)
                    }
                }
                sorted(model, filtered, sort).forEachIndexed { i, g -> GymRow(g, i > 0 || (sort == GymSort.DISTANCE && model.origin == null)) }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Explainer(if (search.isEmpty()) "No gyms match the current filters."
                else "No gyms match \"$search\"" + if (model.filtersActive) " with the current filters." else ".")
            }
        }
    }
}

/**
 * Every word must appear somewhere. Spaces and punctuation are ignored, so
 * "autobelay", "auto belay" and "auto-belay" all match, as does "se11" for "SE11 5JA".
 */
private fun filterGyms(model: AppModel, search: String): List<Gym> {
    fun squash(s: String) = s.lowercase(Locale.UK).filter { it.isLetterOrDigit() }
    val words = search.split(" ").map { squash(it) }.filter { it.isNotEmpty() }
    return model.data.gyms.filter { g ->
        if (!model.matchesFilters(g)) return@filter false
        val hay = squash(listOfNotNull(g.name, g.brand, g.area, g.borough, g.postcode, g.address, g.typeLabel, g.facilities).joinToString(" "))
        words.all { it in hay }
    }
}

private fun sorted(model: AppModel, gyms: List<Gym>, sort: GymSort): List<Gym> {
    val conc = model.concession
    val key: (Gym) -> Double = when (sort) {
        GymSort.PEAK -> { g -> g.peakPrice(conc) ?: Double.MAX_VALUE }
        GymSort.OFFPEAK -> { g -> g.bestOffpeak(conc) ?: Double.MAX_VALUE }
        else -> { g -> model.distanceKm(g) ?: Double.MAX_VALUE }
    }
    return gyms.sortedWith(compareBy(key, { it.name }))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GymRow(gym: Gym, divider: Boolean) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val conc = model.concession
    RowItem(onClick = { nav.navigate("gym/${gym.id}") }, divider = divider) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GymLogo(gym, 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gym.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    FavouriteMark(gym.id)
                    VerifyFlag(gym)
                }
                Text(listOfNotNull(gym.area, "Zone ${gym.zone}", gym.typeLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = secondaryText)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("peak " + formatPrice(gym.peakPrice(conc)), PeakPurple)
                    gym.bestOffpeak(conc)?.let { Pill("from " + formatPrice(it), Amber) }
                    model.distanceKm(gym)?.let {
                        Text(String.format(Locale.UK, "%.1f km", it), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                    if (gym.acLevel != "none") Icon(Icons.Filled.AcUnit, "Air-conditioning", tint = secondaryText, modifier = Modifier.size(16.dp))
                }
                NoteSummary(gym.id)
            }
        }
    }
}

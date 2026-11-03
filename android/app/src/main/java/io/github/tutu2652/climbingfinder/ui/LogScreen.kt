package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.LocationService
import io.github.tutu2652.climbingfinder.core.ClimbSession
import io.github.tutu2652.climbingfinder.core.Discipline
import io.github.tutu2652.climbingfinder.core.Moment
import io.github.tutu2652.climbingfinder.core.brandRank
import io.github.tutu2652.climbingfinder.core.formatDuration
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.haversineKm
import io.github.tutu2652.climbingfinder.core.nowSecond
import io.github.tutu2652.climbingfinder.core.shortName
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val fullDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(io.github.tutu2652.climbingfinder.core.London)
val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK).withZone(io.github.tutu2652.climbingfinder.core.London)
val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.UK).withZone(io.github.tutu2652.climbingfinder.core.London)

/** The current time, ticking every `periodMs`, for live timers. */
@Composable
fun rememberNow(periodMs: Long = 30_000): State<Instant> = produceState(Instant.now(), periodMs) {
    while (true) { delay(periodMs); value = Instant.now() }
}

/** Sessions grouped under "October 2026"-style headings, in the order given. */
fun monthGroups(sessions: List<ClimbSession>): List<Pair<String, List<ClimbSession>>> =
    sessions.groupBy { monthFormat.format(it.start) }.toList()

/** The Log tab: start or resume a session, see recent sessions and a summary. */
@Composable
fun LogScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val sessions = model.logbook.sessions
    val active = model.logbook.active
    Screen("Logbook", back = false) {
        section {
            if (active != null) {
                RowItem(onClick = { nav.navigate("session/${active.id}") }, divider = false) { ActiveSessionCard(active) }
            } else {
                ActionRow(Icons.Filled.PlayCircle, "Start a session", divider = false) { nav.navigate("start?past=false") }
                ActionRow(Icons.Filled.History, "Log a past session") { nav.navigate("start?past=true") }
            }
        }
        if (sessions.isNotEmpty()) {
            section {
                RowItem(onClick = { nav.navigate("stats") }, divider = false) { MonthSummary(sessions) }
                ActionRow(Icons.Filled.Apartment, "Visits by gym", value = sessions.map { it.gymID }.toSet().size.let { "$it gym${if (it == 1) "" else "s"}" }) { nav.navigate("visits") }
            }
        }
        for ((title, group) in monthGroups(sessions.filter { !it.isActive })) {
            section(title) {
                group.forEachIndexed { i, s -> RowItem(onClick = { nav.navigate("session/${s.id}") }, divider = i > 0) { SessionRow(s) } }
            }
        }
        if (sessions.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
                    Text("Start a session when you get to the wall, then tap a grade each time you send.",
                        color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(session: ClimbSession) {
    val model = LocalModel.current
    val now by rememberNow()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        model.gym(session.gymID)?.let { GymLogo(it, 48.dp) }
        Column {
            Text("Climbing now", style = MaterialTheme.typography.labelMedium, color = Brand, fontWeight = FontWeight.SemiBold)
            Text(model.gym(session.gymID)?.name ?: "Session", style = MaterialTheme.typography.titleMedium)
            Text("${formatDuration(session.durationSeconds(now))} · ${session.sends.size} sends", color = secondaryText)
        }
    }
}

/** Hardest top grade in a session: boulders first, else ropes. */
fun topLabel(s: ClimbSession): String? =
    s.hardest(Discipline.BOULDER)?.let { Discipline.BOULDER.grades[it] } ?: s.hardest(Discipline.ROPE)?.let { Discipline.ROPE.grades[it] }

@Composable
fun SessionRow(session: ClimbSession) {
    val model = LocalModel.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        model.gym(session.gymID)?.let { GymLogo(it, 40.dp) }
        Column(Modifier.weight(1f)) {
            Text(model.gym(session.gymID)?.shortName ?: "Unknown gym", style = MaterialTheme.typography.titleSmall)
            Text(dayFormat.format(session.start) + " · " + formatDuration(session.durationSeconds()),
                style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${session.sends.size} sends", fontWeight = FontWeight.SemiBold)
            topLabel(session)?.let { Pill("top $it", Amber) }
        }
    }
}

@Composable
private fun MonthSummary(sessions: List<ClimbSession>) {
    val monthAgo = Instant.now().minus(30, ChronoUnit.DAYS)
    val recent = sessions.filter { it.start >= monthAgo }
    val sends = recent.flatMap { it.sends }
    val top = sends.filter { it.discipline == Discipline.BOULDER && it.isGraded }.maxOfOrNull { it.grade }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Last 30 days", style = MaterialTheme.typography.labelMedium, color = secondaryText)
        Row {
            Tile("${recent.size}", "sessions", Modifier.weight(1f))
            Tile("${sends.size}", "sends", Modifier.weight(1f))
            Tile(top?.let { "V$it" } ?: "–", "hardest", Modifier.weight(1f))
            Tile(formatPrice(recent.mapNotNull { it.price }.sum()).replace(".00", ""), "spent", Modifier.weight(1f))
        }
    }
}

@Composable
fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

// --- Start a session

@Composable
fun StartSessionScreen(past: Boolean, gymID: String?) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val sessions = model.logbook.sessions
    var chosen by rememberSaveable {
        mutableStateOf(
            gymID ?: sessions.firstOrNull { model.gym(it.gymID) != null }?.gymID
            ?: model.data.gyms.filter { model.isFavourite(it.id) }.minByOrNull { it.name }?.id
            ?: model.data.gyms.sortedWith(compareBy({ it.brandRank }, { it.brand }, { it.name })).firstOrNull()?.id ?: ""
        )
    }
    // Set once you choose a gym yourself, so a late location fix never overrides it.
    var pickedByHand by rememberSaveable { mutableStateOf(false) }
    var nearby by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var startAt by remember { mutableStateOf(nowSecond().minus(2, ChronoUnit.HOURS)) }
    var hours by remember { mutableDoubleStateOf(2.0) }
    var onPass by rememberSaveable { mutableStateOf(false) }
    val at = if (past) startAt else Instant.now()
    var priceText by remember(chosen, at.epochSecond / 60, model.concession) {
        mutableStateOf(model.gym(chosen)?.let { model.pricer.applicable(it, Moment.of(at), model.concession).price }?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "")
    }

    // For a live session, start at the gym you're standing in, if any.
    LaunchedEffect(Unit) {
        if (past || gymID != null || !LocationService.isAuthorized(ctx)) return@LaunchedEffect
        val here = LocationService.currentLocation(ctx) ?: return@LaunchedEffect
        if (!here.hasAccuracy() || here.accuracy > 500 || pickedByHand) return@LaunchedEffect
        val (gym, km) = model.data.gyms.map { it to haversineKm(here.latitude, here.longitude, it.lat, it.lng) }.minByOrNull { it.second } ?: return@LaunchedEffect
        if (km <= 0.5) { chosen = gym.id; nearby = true }
    }

    fun start() {
            val price = if (onPass) null else priceText.replace("£", "").trim().toDoubleOrNull()
            val s = ClimbSession(
                gymID = chosen,
                start = if (past) startAt else nowSecond(),
                end = if (past) minOf(startAt.plusSeconds((hours * 3600).toLong()), nowSecond()) else null,
                price = price,
            )
            if (!past) model.logbook.active?.let { a -> model.logbook.update(a.id) { it.copy(end = nowSecond()) } }
            model.logbook.insert(s)
            nav.navigate("session/${s.id}") { popUpTo("start?past={past}&gym={gym}") { inclusive = true } }
    }

    Screen(if (past) "Log a past session" else "Start a session", actions = {
        TextButton(enabled = chosen.isNotEmpty(), onClick = { start() }) { Text(if (past) "Add" else "Start", fontWeight = FontWeight.SemiBold) }
    }) {
        section(footer = if (nearby) "Nearest gym to you" else null) {
            RowItem(onClick = { picking = true }, divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    model.gym(chosen)?.let { GymLogo(it, 32.dp) }
                    Text("Gym", modifier = Modifier.weight(1f))
                    if (nearby) Icon(Icons.Filled.LocationOn, "Nearest gym to you", tint = Brand, modifier = Modifier.size(16.dp))
                    Text(model.gym(chosen)?.name ?: "Choose a gym", color = secondaryText)
                }
            }
            if (past) {
                DateTimeRow("Started", startAt, max = Instant.now()) { startAt = it }
                RowItem {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Length: " + formatDuration((hours * 3600).toLong()), modifier = Modifier.weight(1f))
                        TextButton(onClick = { hours = maxOf(0.5, hours - 0.25) }) { Text("−") }
                        TextButton(onClick = { hours = minOf(8.0, hours + 0.25) }) { Text("+") }
                    }
                }
            }
        }
        section(footer = if (onPass) "Logged as £0, so it isn't counted in what you spend on day passes."
        else "Filled in from the gym's ${if (model.concession) "concession" else "adult"} price at that time. Change it if you paid something else.") {
            RowItem(onClick = { onPass = !onPass }, divider = false, chevron = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Membership or pass", modifier = Modifier.weight(1f))
                    Switch(onPass, { onPass = it })
                }
            }
            if (!onPass) {
                RowItem {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Entry price", modifier = Modifier.weight(1f))
                        OutlinedTextField(priceText, { priceText = it }, singleLine = true, prefix = { Text("£") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(0.45f))
                    }
                }
            }
        }
        item {
            Button(enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(14.dp), onClick = { start() }) {
                Text(if (past) "Add session" else "Start session")
            }
        }
    }
    if (picking) GymPicker(chosen, { picking = false }) { chosen = it; pickedByHand = true; nearby = false; picking = false }
}

/** Shared gym selection for both live and past sessions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPicker(selection: String, dismiss: () -> Unit, pick: (String) -> Unit) {
    val model = LocalModel.current
    var search by remember { mutableStateOf("") }
    fun normalize(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.UK).filter { it.isLetterOrDigit() }
    val words = search.split(" ").map { normalize(it) }.filter { it.isNotEmpty() }
    val gyms = model.data.gyms.filter { g ->
        val text = normalize(listOfNotNull(g.name, g.brand, g.area, g.borough, g.postcode, g.address).joinToString(" "))
        words.all { it in text }
    }.sortedWith(compareBy({ it.brandRank }, { it.brand }, { it.name }))
    ModalBottomSheet(onDismissRequest = dismiss) {
        Text("Choose a gym", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(search, { search = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Gym, chain, area or postcode") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            var lastBrand = ""
            for (g in gyms) {
                if (g.brand != lastBrand) {
                    lastBrand = g.brand
                    item(key = "h" + g.brand) {
                        Text(g.brand.uppercase(), style = MaterialTheme.typography.labelMedium, color = secondaryText,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
                    }
                }
                item(key = g.id) {
                    Row(Modifier.fillMaxWidth().clickable { pick(g.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GymLogo(g, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(g.name)
                            Text(listOfNotNull(g.area, g.postcode).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        }
                        if (selection == g.id) Icon(Icons.Filled.Check, "Selected", tint = Brand)
                    }
                }
            }
            if (gyms.isEmpty()) item { Text("No gyms match \"$search\".", color = secondaryText, modifier = Modifier.padding(20.dp)) }
            item { Spacer(Modifier.padding(16.dp)) }
        }
    }
}

package io.github.tutu2652.climbingfinder.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.core.Climb
import io.github.tutu2652.climbingfinder.core.ClimbResult
import io.github.tutu2652.climbingfinder.core.ClimbSession
import io.github.tutu2652.climbingfinder.core.Discipline
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.London
import io.github.tutu2652.climbingfinder.core.formatDuration
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.shortName
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class Period(val title: String, val days: Long?) {
    MONTH("30 days", 30), MONTH3("3 months", 91), YEAR("Year", 365), ALL("All", null)
}

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK).withZone(London)

@Composable
fun StatsScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("climbing", Context.MODE_PRIVATE) }
    var period by remember { mutableStateOf(runCatching { Period.valueOf(prefs.getString("statsPeriod", "MONTH3")!!) }.getOrDefault(Period.MONTH3)) }
    var discipline by remember { mutableStateOf(Discipline.BOULDER) }
    val allSessions = model.logbook.sessions.sortedBy { it.start }
    val s = period.days?.let { d -> val from = Instant.now().minus(d, ChronoUnit.DAYS); allSessions.filter { it.start >= from } } ?: allSessions
    val climbs = s.flatMap { it.climbs }.filter { it.discipline == discipline }
    val sends = climbs.filter { it.result.isSend }
    // Comp problems logged without a grade count as sends but not in grade stats.
    val graded = sends.filter { it.isGraded }
    Screen("Stats") {
        section {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(Period.entries, period, { it.title }) { period = it; prefs.edit().putString("statsPeriod", it.name).apply() }
                if (allSessions.any { x -> x.climbs.any { it.discipline == Discipline.ROPE } }) {
                    Segmented(Discipline.entries, discipline, { it.title }) { discipline = it }
                }
            }
        }
        section {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row {
                    Tile("${s.size}", "sessions", Modifier.weight(1f))
                    Tile(formatDuration(s.sumOf { it.durationSeconds() }), "climbing", Modifier.weight(1f))
                    Tile("${sends.size}", "sends", Modifier.weight(1f))
                }
                Row {
                    Tile(graded.maxOfOrNull { it.grade }?.let { discipline.grades[it] } ?: "–", "hardest", Modifier.weight(1f))
                    Tile(if (sends.isEmpty()) "–" else "${(sends.count { it.result == ClimbResult.FLASH } * 100.0 / sends.size).roundToInt()}%", "flashed", Modifier.weight(1f))
                    Tile(if (s.isEmpty()) "–" else String.format(Locale.UK, "%.1f", sends.size.toDouble() / s.size), "sends / session", Modifier.weight(1f))
                }
            }
        }
        if (graded.isNotEmpty()) {
            section("Grade pyramid", footer = "Sends at each grade. A broad base under your hardest grade usually means you're ready to push it.") {
                Pyramid(graded, discipline)
            }
        }
        val progress = s.mapNotNull { x -> x.hardest(discipline)?.let { x.start to it } }
        if (progress.size >= 2) section("Hardest send per session") { ProgressChart(progress, discipline) }
        if (s.size >= 2) section("Sessions per week") { WeeklyChart(s) }
        if (s.isNotEmpty()) {
            section("Where you climbed") {
                val rows = s.groupBy { it.gymID }.mapNotNull { (id, ss) -> model.gym(id)?.let { Triple(it, ss.size, ss.mapNotNull { x -> x.price }.sum()) } }
                    .sortedWith(compareByDescending<Triple<Gym, Int, Double>> { it.second }.thenByDescending { it.third })
                rows.forEachIndexed { i, (gym, visits, spent) ->
                    RowItem(onClick = { nav.navigate("gymVisits/${gym.id}") }, divider = i > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GymLogo(gym, 32.dp)
                            Text(gym.shortName, Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$visits visit${if (visits == 1) "" else "s"}")
                                if (spent > 0) Text(formatPrice(spent), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                            }
                        }
                    }
                }
            }
            membershipCheck(allSessions)
        }
        if (allSessions.isEmpty()) {
            item { Explainer("Nothing to show yet. Log a few sessions and your stats appear here.") }
        }
    }
}

@Composable
private fun Pyramid(sends: List<Climb>, d: Discipline) {
    val top = sends.maxOf { it.grade }
    val low = maxOf(0, sends.minOf { it.grade })
    val most = (low..top).maxOf { g -> sends.count { it.grade == g } }.coerceAtLeast(1)
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (g in (low..top).reversed()) {
            val flash = sends.count { it.grade == g && it.result == ClimbResult.FLASH }
            val send = sends.count { it.grade == g && it.result == ClimbResult.SEND }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.grades[g], Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.weight(1f).height(18.dp)) {
                    if (flash > 0) Box(Modifier.weight(flash.toFloat()).fillMaxHeight().background(Amber, RoundedCornerShape(3.dp)))
                    if (send > 0) Box(Modifier.weight(send.toFloat()).fillMaxHeight().background(Brand, RoundedCornerShape(3.dp)))
                    val rest = most - flash - send
                    if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
                }
                Text("${flash + send}", Modifier.width(28.dp).padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend("Flash", Amber); Legend("Send", Brand)
        }
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

@Composable
private fun ProgressChart(points: List<Pair<Instant, Int>>, d: Discipline) {
    val lo = maxOf(0, points.minOf { it.second } - 1)
    val hi = maxOf(lo + 1, points.maxOf { it.second })
    val t0 = points.first().first.epochSecond.toFloat()
    val t1 = maxOf(t0 + 1, points.last().first.epochSecond.toFloat())
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.padding(14.dp)) {
        Row(Modifier.height(170.dp)) {
            Column(Modifier.fillMaxHeight().width(36.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(d.grades[hi], style = MaterialTheme.typography.bodySmall, color = secondaryText)
                Text(d.grades[lo], style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                for (g in lo..hi) {
                    val y = size.height * (1 - (g - lo).toFloat() / (hi - lo))
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                val pts = points.map { (t, g) ->
                    Offset(size.width * (t.epochSecond - t0) / (t1 - t0), size.height * (1 - (g - lo).toFloat() / (hi - lo)))
                }
                val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(path, Brand, style = Stroke(width = 5f))
                for (p in pts) drawCircle(Brand, 7f, p)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            Text(shortDate.format(points.first().first), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            Spacer(Modifier.weight(1f))
            Text(shortDate.format(points.last().first), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
    }
}

@Composable
private fun WeeklyChart(sessions: List<ClimbSession>) {
    val weekOf = { t: Instant -> t.atZone(London).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val counts = sessions.groupBy { weekOf(it.start) }.mapValues { it.value.size }
    val first = counts.keys.min()
    val last = counts.keys.max()
    val weeks = generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= last }.toList()
    val most = counts.values.max()
    Column(Modifier.padding(14.dp)) {
        Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in weeks) {
                val n = counts[w] ?: 0
                Box(Modifier.weight(1f).fillMaxHeight(if (n == 0) 0.01f else n.toFloat() / most).background(Brand, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(DateTimeFormatter.ofPattern("d MMM", Locale.UK).format(first), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            Spacer(Modifier.weight(1f))
            Text("most in a week: $most", style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
    }
}

/**
 * Compares the last 30 days of day-pass spending at each chain with its
 * monthly membership.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.membershipCheck(sessions: List<ClimbSession>) {
    item {
        val model = LocalModel.current
        val monthAgo = Instant.now().minus(30, ChronoUnit.DAYS)
        val recent = sessions.filter { it.start >= monthAgo }
        data class Check(val brand: String, val visits: Int, val spent: Double, val membership: Double, val gym: Gym)
        val rows = recent.groupBy { s -> model.gym(s.gymID)?.let { if (it.brand == "Independent") it.id else it.brand } ?: s.gymID }
            .mapNotNull { (brand, ss) ->
                // Independents' memberships cover one gym, so compare per gym there.
                val g = ss.firstNotNullOfOrNull { model.gym(it.gymID) } ?: return@mapNotNull null
                val m = (if (model.concession) (g.membership?.concessionMonthly ?: g.membership?.fullMonthly) else g.membership?.fullMonthly)
                    ?: return@mapNotNull null
                Check(if (g.brand == "Independent") g.shortName else brand, ss.size, ss.mapNotNull { it.price }.sum(), m, g)
            }
            .filter { it.visits >= 2 }
            .sortedByDescending { it.spent }
        if (rows.isNotEmpty()) {
            Column {
                GroupCard("Membership check · last 30 days") {
                    rows.forEachIndexed { i, r ->
                        val paidVisits = recent.count { s ->
                            val gym = model.gym(s.gymID) ?: return@count false
                            val same = if (r.gym.brand == "Independent") gym.id == r.gym.id else gym.brand == r.gym.brand
                            same && (s.price ?: 0.0) > 0
                        }
                        val perVisit = if (paidVisits > 0) r.spent / paidVisits else null
                        RowItem(divider = i > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(r.brand, style = MaterialTheme.typography.titleSmall)
                                Text("${r.visits} visits, ${formatPrice(r.spent)} on entry · membership ${formatPrice(r.membership)}/mo")
                                if (r.spent >= r.membership) {
                                    Text("Compared with the listed monthly price, potential savings are ${formatPrice(r.spent - r.membership)}.",
                                        color = Brand, fontWeight = FontWeight.SemiBold)
                                } else if (perVisit != null) {
                                    Text("At about ${formatPrice(perVisit)} a visit, membership pays off from ${ceil(r.membership / perVisit).toInt()} visits a month.",
                                        color = secondaryText)
                                }
                            }
                        }
                    }
                }
                Explainer("Uses what you logged as your entry price. Chains' memberships usually cover all their sites.")
            }
        }
    }
}

data class VisitedGym(val gym: Gym, val visits: Int, val last: Instant)

fun visitedGyms(sessions: List<ClimbSession>, gym: (String) -> Gym?): List<VisitedGym> =
    sessions.groupBy { it.gymID }.mapNotNull { (id, ss) -> gym(id)?.let { VisitedGym(it, ss.size, ss.maxOf { s -> s.start }) } }
        .sortedWith(compareByDescending<VisitedGym> { it.visits }.thenByDescending { it.last })

/** Every gym you've visited, most visits first. */
@Composable
fun VisitsByGymScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val sessions = model.logbook.sessions
    val rows = visitedGyms(sessions) { model.gym(it) }
    Screen("Visits by gym") {
        section(footer = "${sessions.size} visit${if (sessions.size == 1) "" else "s"} to ${rows.size} gym${if (rows.size == 1) "" else "s"} in total.") {
            rows.forEachIndexed { i, r ->
                RowItem(onClick = { nav.navigate("gymVisits/${r.gym.id}") }, divider = i > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GymLogo(r.gym, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(r.gym.shortName)
                            Text("Last visit " + fullDateFormat.format(r.last), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        }
                        Text("${r.visits}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (r.visits == 1) "visit" else "visits", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                }
            }
        }
    }
}

/** All your visits to one gym, with totals for that gym. */
@Composable
fun GymVisitsScreen(id: String) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val gym = model.gym(id) ?: return Screen("Your visits") {}
    val visits = model.logbook.sessions.filter { it.gymID == id }
    val sends = visits.flatMap { it.sends }
    val paid = visits.mapNotNull { it.price }.filter { it > 0 }
    Screen("Your visits") {
        section {
            RowItem(divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GymLogo(gym, 48.dp)
                    Column {
                        Text(gym.name, style = MaterialTheme.typography.titleMedium)
                        Text(gym.area ?: "", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                }
            }
            RowItem {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row {
                        Tile("${visits.size}", if (visits.size == 1) "visit" else "visits", Modifier.weight(1f))
                        Tile(formatDuration(visits.sumOf { it.durationSeconds() }), "climbing", Modifier.weight(1f))
                        Tile("${sends.size}", "sends", Modifier.weight(1f))
                    }
                    Row {
                        Tile(hardestLabel(visits), "hardest", Modifier.weight(1f))
                        Tile(formatPrice(paid.sum()), "spent", Modifier.weight(1f))
                        Tile(if (paid.isEmpty()) "–" else formatPrice(paid.sum() / paid.size), "per paid visit", Modifier.weight(1f))
                    }
                }
            }
            ActionRow(Icons.Filled.Info, "Gym details & prices") { nav.navigate("gym/${gym.id}") }
        }
        colourSendsSection(gym.id, sends, model.colourScale(gym.id)) { nav.navigate("colours/${gym.id}") }
        for ((title, group) in monthGroups(visits)) {
            section(title) {
                group.forEachIndexed { i, s -> RowItem(onClick = { nav.navigate("session/${s.id}") }, divider = i > 0) { VisitRow(s) } }
            }
        }
        if (visits.isEmpty()) item { Explainer("No visits yet. Sessions you log here will show up in this list.") }
    }
}

/** One visit in a gym's list: date, length, sends, top grade. */
@Composable
fun VisitRow(session: ClimbSession) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(longDayFormat.format(session.start), style = MaterialTheme.typography.titleSmall)
            Text(timeFormat.format(session.start) + " · " + formatDuration(session.durationSeconds()) + if (session.isActive) " · now" else "",
                style = MaterialTheme.typography.bodySmall, color = secondaryText)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${session.sends.size} sends", fontWeight = FontWeight.SemiBold)
            topLabel(session)?.let { Pill("top $it", Amber) }
        }
    }
}

/** Prefer boulders when a visit includes both disciplines. */
private fun hardestLabel(visits: List<ClimbSession>): String {
    visits.mapNotNull { it.hardest(Discipline.BOULDER) }.maxOrNull()?.let { return "V$it" }
    visits.mapNotNull { it.hardest(Discipline.ROPE) }.maxOrNull()?.let { return Discipline.ROPE.grades[it] }
    return "–"
}

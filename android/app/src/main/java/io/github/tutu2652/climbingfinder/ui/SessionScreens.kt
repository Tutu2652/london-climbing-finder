package io.github.tutu2652.climbingfinder.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.core.Climb
import io.github.tutu2652.climbingfinder.core.ClimbResult
import io.github.tutu2652.climbingfinder.core.ClimbSession
import io.github.tutu2652.climbingfinder.core.Discipline
import io.github.tutu2652.climbingfinder.core.London
import io.github.tutu2652.climbingfinder.core.formatDuration
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.nowSecond
import io.github.tutu2652.climbingfinder.core.shortName
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

val ClimbResult.icon: ImageVector
    get() = when (this) {
        ClimbResult.FLASH -> Icons.Filled.Bolt
        ClimbResult.SEND -> Icons.Filled.Check
        ClimbResult.ATTEMPT -> Icons.Filled.Close
    }

/**
 * Opens a session the right way: the logging screen while it's live, the
 * read-only summary once it's finished.
 */
@Composable
fun SessionScreen(id: String) {
    val session = LocalModel.current.logbook.session(id)
    when {
        session == null -> Screen("Session") { section { RowItem(divider = false) { Text("This session was deleted.") } } }
        session.isActive -> SessionLogScreen(id)
        else -> VisitDetailScreen(session)
    }
}

/**
 * One session: tap a grade to log a climb (as a flash, send or attempt),
 * see the tally, and add notes. Works for live and past sessions.
 */
@Composable
fun SessionLogScreen(id: String) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { ctx.getSharedPreferences("climbing", Context.MODE_PRIVATE) }
    val session = model.logbook.session(id) ?: return SessionScreen(id)
    val gym = model.gym(session.gymID)
    val ropesHere = gym?.type?.contains("ropes") == true || session.climbs.any { it.discipline == Discipline.ROPE }
    var discipline by remember { mutableStateOf(Discipline.of(prefs.getString("logDiscipline", "boulder")!!) ?: Discipline.BOULDER) }
    if (!ropesHere) discipline = Discipline.BOULDER
    var mode by remember { mutableStateOf(ClimbResult.of(prefs.getString("logMode", "send")!!) ?: ClimbResult.SEND) }
    var allGrades by remember { mutableStateOf(false) }
    // Carry on in comp mode if that's what you were last logging.
    var comp by remember { mutableStateOf(session.climbs.maxByOrNull { it.loggedAt }?.comp ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val now by rememberNow(1000)

    fun log(grade: Int) {
        val at = if (session.isActive) nowSecond() else minOf(session.end ?: session.start, nowSecond())
        model.logbook.update(id) { it.copy(climbs = it.climbs + Climb.of(discipline, grade, mode, at, comp)) }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Screen(if (session.isActive) "Session" else dayFormat.format(session.start), actions = {
        if (session.isActive) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Delete session") }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        } else {
            TextButton(onClick = { nav.popBackStack() }) { Text("Done") }
        }
    }) {
        section {
            RowItem(divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    gym?.let { GymLogo(it, 48.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(gym?.name ?: "Unknown gym", style = MaterialTheme.typography.titleMedium)
                        Text(dateTimeFormat.format(session.start), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                    Text(formatDuration(session.durationSeconds(now)), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = if (session.isActive) Brand else secondaryText)
                }
            }
        }

        section(if (comp) "Tap the grade it felt, or No grade, to log a comp ${mode.title.lowercase()}"
        else "Tap a grade to log ${if (mode == ClimbResult.ATTEMPT) "an" else "a"} ${mode.title.lowercase()}") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ropesHere) {
                    Segmented(Discipline.entries, discipline, { it.title }) { discipline = it; prefs.edit().putString("logDiscipline", it.raw).apply() }
                }
                Segmented(ClimbResult.entries, mode, { it.title }) { mode = it; prefs.edit().putString("logMode", it.raw).apply() }
                Row(Modifier.clickable { comp = !comp }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = secondaryText)
                    Text("  Comp routes", modifier = Modifier.weight(1f))
                    Switch(comp, { comp = it })
                }
                val grades = discipline.grades
                val shown = if (allGrades) grades.size else minOf(discipline.commonCount, grades.size)
                val tiles = (if (comp) listOf(Climb.UNGRADED to "No grade") else emptyList()) + (0 until shown).map { it to grades[it] }
                for (row in tiles.chunked(5)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((g, title) in row) {
                            val count = session.climbs.count { it.discipline == discipline && it.grade == g && it.comp == comp && it.result.isSend }
                            GradeTile(title, count, Modifier.weight(1f),
                                if (g == Climb.UNGRADED) "Log comp ${mode.title.lowercase()} with no grade"
                                else "Log ${if (comp) "comp " else ""}${mode.title.lowercase()} at $title") { log(g) }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { allGrades = !allGrades }) { Text(if (allGrades) "Fewer grades" else "More grades") }
                    Spacer(Modifier.weight(1f))
                    session.climbs.maxByOrNull { it.loggedAt }?.let { last ->
                        TextButton(onClick = { model.logbook.update(id) { s -> s.copy(climbs = s.climbs.filterNot { it.id == last.id }) } }) {
                            Text("Undo ${last.result.title.lowercase()} ${last.label}", color = Warn)
                        }
                    }
                }
            }
        }

        if (session.climbs.isNotEmpty()) {
            section("This session") { RowItem(divider = false) { SessionTally(session) } }
            section("Climbs", footer = "Tap a climb to change its grade or comp tag.") {
                session.climbs.sortedByDescending { it.loggedAt }.forEachIndexed { i, c ->
                    RowItem(onClick = { nav.navigate("climb/${session.id}/${c.id}") }, divider = i > 0) { ClimbRow(c) }
                }
            }
        }

        section("Notes") {
            RowItem(divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Felt", color = secondaryText)
                    StarPicker(session.feeling, 28) { k -> model.logbook.update(id) { it.copy(feeling = k) } }
                }
            }
            RowItem {
                OutlinedTextField(session.notes, { t -> model.logbook.update(id) { it.copy(notes = t) } },
                    placeholder = { Text("Projects, beta, how it went…") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        }

        section("Details") {
            DateTimeRow("Started", session.start, max = session.end ?: Instant.now(), divider = false) { t -> model.logbook.update(id) { it.copy(start = t) } }
            session.end?.let { end ->
                DateTimeRow("Finished", end, min = session.start) { t -> model.logbook.update(id) { it.copy(end = t) } }
            }
            PriceRow(session.price) { p -> model.logbook.update(id) { it.copy(price = p) } }
        }

        if (session.isActive) {
            item {
                Button(onClick = {
                    model.logbook.update(id) { it.copy(end = nowSecond()) }
                    nav.popBackStack()
                }, modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Icon(Icons.Filled.StopCircle, null); Text("  End session")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session and its ${session.climbs.size} climbs?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; nav.popBackStack(); model.logbook.delete(id) }) { Text("Delete", color = Warn) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GradeTile(title: String, count: Int, modifier: Modifier, description: String, onTap: () -> Unit) {
    Column(
        modifier.heightIn(min = 52.dp)
            .background(Brand.copy(alpha = if (count > 0) 0.16f else 0.07f), RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .semantics { contentDescription = description }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(if (count > 0) "$count" else " ", style = MaterialTheme.typography.labelSmall, color = Brand, fontWeight = FontWeight.SemiBold)
    }
}

/** Entry price; empty means a membership or pass. */
@Composable
private fun PriceRow(price: Double?, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(price?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "") }
    RowItem {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Entry", modifier = Modifier.weight(1f))
            OutlinedTextField(text, { t ->
                text = t
                val v = t.trim().toDoubleOrNull()
                if (t.isBlank() || (v != null && v >= 0)) onChange(v)
            }, singleLine = true, prefix = { Text("£") }, placeholder = { Text("Membership / pass") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(0.55f))
        }
    }
}

/** A labelled date and time; tapping picks the date, then the time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRow(label: String, value: Instant, min: Instant? = null, max: Instant? = null, divider: Boolean = true, onChange: (Instant) -> Unit) {
    var step by remember { mutableStateOf(0) }   // 0 closed, 1 date, 2 time
    var picked by remember { mutableStateOf(value) }
    LabeledRow(label, dayFormat.format(value) + ", " + timeFormat.format(value), divider = divider) { picked = value; step = 1 }
    fun clamp(t: Instant): Instant {
        var r = t
        if (min != null && r < min) r = min
        if (max != null && r > max) r = max
        return r
    }
    if (step == 1) {
        val local = picked.atZone(London)
        val state = rememberDatePickerState(local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked = local.with(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()).toInstant() }
                    step = 2
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (step == 2) {
        val local = picked.atZone(London)
        val state = rememberTimePickerState(local.hour, local.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    onChange(clamp(local.withHour(state.hour).withMinute(state.minute).withSecond(0).toInstant()))
                    step = 0
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun ClimbRow(climb: Climb) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(climb.result.icon, null, tint = if (climb.result == ClimbResult.FLASH) Amber else if (climb.result == ClimbResult.SEND) Brand else secondaryText,
            modifier = Modifier.size(20.dp))
        Text(climb.gradeLabel, fontWeight = FontWeight.SemiBold)
        Text(climb.result.title, color = secondaryText)
        if (climb.comp) Pill("Comp", Amber)
        Spacer(Modifier.weight(1f))
        Text(timeFormat.format(climb.loggedAt), style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

/** Per-grade counts, hardest first, plus totals. */
@Composable
fun SessionTally(session: ClimbSession) {
    val climbs = session.climbs
    val sends = climbs.filter { it.result.isSend }
    val flashes = climbs.count { it.result == ClimbResult.FLASH }
    val attempts = climbs.size - sends.size
    val comps = climbs.count { it.comp }
    data class TallyRow(val label: String, val comp: Boolean, val flash: Int, val send: Int, val attempt: Int)
    // Regular climbs first, then comp problems; hardest first within each, ungraded last.
    val rows = climbs.groupBy { Triple(it.disciplineRaw, it.grade, it.comp) }.map { (_, cs) ->
        val c = cs.first()
        Triple(c, c.grade, TallyRow(c.gradeLabel, c.comp, cs.count { it.result == ClimbResult.FLASH },
            cs.count { it.result == ClimbResult.SEND }, cs.count { it.result == ClimbResult.ATTEMPT }))
    }.sortedWith(compareBy({ if (it.first.discipline == Discipline.ROPE) 1 else 0 }, { if (it.third.comp) 1 else 0 }, { -it.second }))
        .map { it.third }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${sends.size} sends" + (if (flashes > 0) " ($flashes flashed)" else "") +
            (if (attempts > 0) " · $attempts attempt${if (attempts == 1) "" else "s"}" else "") +
            (if (comps > 0) " · $comps comp" else ""), fontWeight = FontWeight.SemiBold)
        for (r in rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(r.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
                if (r.comp) Icon(Icons.Filled.EmojiEvents, "Comp", tint = secondaryText, modifier = Modifier.size(16.dp))
                if (r.flash > 0) IconText(Icons.Filled.Bolt, "${r.flash}", MaterialTheme.typography.bodyMedium, Amber)
                if (r.send > 0) IconText(Icons.Filled.Check, "${r.send}", MaterialTheme.typography.bodyMedium, Brand)
                if (r.attempt > 0) IconText(Icons.Filled.Close, "${r.attempt}", MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Everything about one finished visit. Edit opens the logging screen. */
@Composable
private fun VisitDetailScreen(session: ClimbSession) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val gym = model.gym(session.gymID)
    val climbs = session.climbs.sortedBy { it.loggedAt }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Screen(dayFormat.format(session.start), actions = {
        TextButton(onClick = { nav.navigate("session/${session.id}/log") }) { Text("Edit") }
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
        DropdownMenu(menu, { menu = false }) {
            if (gym != null) DropdownMenuItem({ Text("Gym details") }, { menu = false; nav.navigate("gym/${gym.id}") })
            DropdownMenuItem({ Text("Delete visit") }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }) {
        section {
            RowItem(divider = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    gym?.let { GymLogo(it, 48.dp) }
                    Column {
                        Text(gym?.name ?: "Unknown gym", style = MaterialTheme.typography.titleMedium)
                        Text(longDayFormat.format(session.start) + " " + session.start.atZone(London).year,
                            style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                }
            }
            LabeledRow("Time", timeFormat.format(session.start) + (session.end?.let { "–" + timeFormat.format(it) } ?: ""))
            LabeledRow("Length", formatDuration(session.durationSeconds()))
            LabeledRow("Entry", session.price?.let { formatPrice(it) } ?: "Membership / pass")
            if (session.feeling > 0) RowItem { Row { Text("Felt", Modifier.weight(1f)); Stars(session.feeling) } }
            if (gym != null) {
                val all = model.logbook.sessions.filter { it.gymID == gym.id }.sortedBy { it.start }
                val n = all.indexOfFirst { it.id == session.id } + 1
                if (n > 0) LabeledRow("Visit", "${ordinal(n)} of ${all.size} at ${gym.shortName}")
            }
        }
        if (session.notes.isNotEmpty()) section("Notes") { RowItem(divider = false) { Text(session.notes) } }
        if (climbs.isEmpty()) {
            section { RowItem(divider = false) { Text("No climbs logged.", color = secondaryText) } }
        } else {
            section("Summary") { RowItem(divider = false) { SessionTally(session) } }
            section("Climbs in order") {
                climbs.forEachIndexed { i, c -> RowItem(onClick = { nav.navigate("climb/${session.id}/${c.id}") }, divider = i > 0) { ClimbRow(c) } }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this visit and its ${session.climbs.size} climbs?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; nav.popBackStack(); model.logbook.delete(session.id) }) { Text("Delete", color = Warn) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** 1st, 2nd, 3rd, 11th… */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** One climb: change its grade or comp tag, or delete it. */
@Composable
fun ClimbScreen(sessionID: String, climbID: String) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val climb = model.logbook.session(sessionID)?.climbs?.firstOrNull { it.id == climbID }
        ?: return Screen("Climb") { section { RowItem(divider = false) { Text("This climb was deleted.") } } }
    fun change(f: (Climb) -> Climb) = model.logbook.update(sessionID) { s -> s.copy(climbs = s.climbs.map { if (it.id == climbID) f(it) else it }) }
    var gradeMenu by remember { mutableStateOf(false) }
    Screen(climb.label + " · " + climb.result.title) {
        section(footer = if (climb.comp && !climb.isGraded) "Add a grade to count this climb in grade stats, or to untag it as comp." else null) {
            RowItem(divider = false) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Outcome", style = MaterialTheme.typography.labelMedium, color = secondaryText)
                    Segmented(ClimbResult.entries, climb.result, { it.title }) { r -> change { it.copy(resultRaw = r.raw) } }
                }
            }
            LabeledRow("Logged", dayFormat.format(climb.loggedAt) + ", " + timeFormat.format(climb.loggedAt))
            RowItem(onClick = if (climb.comp && !climb.isGraded) null else ({ change { it.copy(isComp = !it.comp) } }), chevron = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comp route", Modifier.weight(1f))
                    Switch(climb.comp, { on -> change { it.copy(isComp = on) } }, enabled = !(climb.comp && !climb.isGraded))
                }
            }
            Box {
                LabeledRow(if (climb.comp) "Felt grade" else "Grade", climb.gradeLabel.let { if (it == "–") "No grade" else it }) { gradeMenu = true }
                DropdownMenu(gradeMenu, { gradeMenu = false }) {
                    if (climb.comp) DropdownMenuItem({ Text("No grade") }, { gradeMenu = false; change { it.copy(grade = Climb.UNGRADED) } })
                    climb.discipline.grades.forEachIndexed { i, g ->
                        DropdownMenuItem({ Text(g) }, { gradeMenu = false; change { it.copy(grade = i) } })
                    }
                }
            }
        }
        if (climb.routePhotoAssetID != null || climb.videoAssetID != null) {
            item { Explainer("This climb has a photo or video linked on an iPhone. The link is kept in backups but can't be opened on Android.") }
        }
        section {
            RowItem(onClick = {
                nav.popBackStack()
                model.logbook.update(sessionID) { s -> s.copy(climbs = s.climbs.filterNot { it.id == climbID }) }
            }, divider = false, chevron = false) { Text("Delete climb", color = Warn) }
        }
    }
}

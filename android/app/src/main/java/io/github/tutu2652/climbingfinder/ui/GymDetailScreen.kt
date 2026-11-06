package io.github.tutu2652.climbingfinder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.core.Discipline
import io.github.tutu2652.climbingfinder.core.Eligibility
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.GymEvent
import io.github.tutu2652.climbingfinder.core.Moment
import io.github.tutu2652.climbingfinder.core.PriceKind
import io.github.tutu2652.climbingfinder.core.dealLines
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.instagramURL
import io.github.tutu2652.climbingfinder.core.openState
import io.github.tutu2652.climbingfinder.core.shortName
import io.github.tutu2652.climbingfinder.core.typeLabel
import io.github.tutu2652.climbingfinder.core.weekdays

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GymDetailScreen(id: String) {
    val model = LocalModel.current
    val gym = model.gym(id) ?: return Screen("Gym") { section { RowItem(divider = false) { Text("This gym is no longer listed.") } } }
    var editing by remember { mutableStateOf(false) }
    val conc = model.concession
    val now = model.pricer.applicable(gym, model.moment, conc)
    val best = model.pricer.bestOnDay(gym, model.dayMoment, conc)
    val fav = model.isFavourite(gym.id)
    Screen(gym.name, actions = {
        IconButton(onClick = { model.toggleFavourite(gym.id) }) {
            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) "Remove favourite" else "Add favourite", tint = Pink)
        }
    }) {
        section {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GymLogo(gym, 56.dp)
                    Text(listOfNotNull(gym.brand, gym.area).joinToString(" · "), color = secondaryText)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(gym.typeLabel, secondaryText)
                    Pill("Zone ${gym.zone}", secondaryText)
                    OpenBadge(gym.openState(model.moment))
                    VerifyFlag(gym)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DirectionsButton(gym)
                    model.travelText(gym)?.let { IconText(Icons.Filled.Train, it, MaterialTheme.typography.bodyMedium) }
                }
                gym.temporarilyClosed?.let { IconText(Icons.Filled.Warning, it, MaterialTheme.typography.bodyMedium, Warn) }
                gym.notice?.let { IconText(Icons.Filled.Schedule, it, MaterialTheme.typography.bodyMedium, Amber) }
                gym.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        gymVisitsSection(model, gym)

        section(if (conc) "Your price · student / concession" else "Your price · adult") {
            WhenControls(showMode = false)
            if (best != null) {
                RowItem { PriceLine("Cheapest on ${longDayFormat.format(model.whenAt)}", best.price, best.label, best.kind, best.`when`) }
            }
            now.price?.let {
                RowItem {
                    PriceLine("At ${timeFormat.format(model.whenAt)}", it, now.label, now.kind,
                        model.pricer.windowForTime(gym, model.moment, conc))
                }
            }
        }

        section("Day-pass prices") {
            LabeledRow("Adult peak", gym.payg.adultPeak.priceOrDash(), divider = false)
            LabeledRow("Adult off-peak", gym.payg.adultOffpeak.priceOrDash())
            LabeledRow("Concession peak", gym.payg.concessionPeak.priceOrDash())
            LabeledRow("Concession off-peak", gym.payg.concessionOffpeak.priceOrDash())
            gym.registrationFee?.let { LabeledRow("Registration fee (first visit)", formatPrice(it)) }
        }

        val deals = gym.dealLines(conc)
        if (deals.isNotEmpty()) {
            section("Off-peak & cheap slots", footer = restrictedNote(gym, model.eligibility)) {
                deals.forEachIndexed { i, d ->
                    RowItem(divider = i > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.widthIn(min = 64.dp)) { Pill(formatPrice(d.price), if (d.magic) Amber else Brand) }
                            Column {
                                Text(d.label)
                                Text(d.`when` + d.extra + if (d.approx) " ~approx" else "", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                            }
                        }
                    }
                }
            }
        }

        gym.studentNote?.takeIf { it.isNotEmpty() }?.let { note ->
            section("Students & concessions") { RowItem(divider = false) { Text(note, style = MaterialTheme.typography.bodyMedium) } }
        }

        section("Membership & passes") {
            var first = true
            gym.membership?.fullMonthly?.let { full ->
                LabeledRow("Monthly", formatPrice(full), divider = false); first = false
                gym.membership?.concessionMonthly?.let { LabeledRow("Monthly (concession)", formatPrice(it)) }
            }
            val passes = gym.passes
            if (passes != null) RowItem(divider = !first) { Text(passes, style = MaterialTheme.typography.bodyMedium) }
            else if (first) RowItem(divider = false) { Text("—", color = secondaryText) }
        }

        val hours = gym.temporaryHours ?: gym.hours
        section("Opening hours" + if (gym.temporaryHours != null) " (temporary)" else if (gym.hoursAssumed) " (approx)" else "") {
            weekdays.forEachIndexed { i, d ->
                LabeledRow(d, hours[d]?.let { it[0] + "–" + it[1] } ?: "closed", divider = i > 0, bold = d == model.moment.weekday)
            }
        }

        if (gym.facilities != null || gym.acNote != null) {
            section("Facilities") {
                gym.facilities?.let { RowItem(divider = false) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                gym.acNote?.let { RowItem(divider = gym.facilities != null) { IconText(Icons.Filled.AcUnit, it, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface) } }
            }
        }

        gym.classInfo?.let { info ->
            section("Classes") {
                info.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, line ->
                    RowItem(divider = i > 0) { LinkedText(line) }
                }
            }
        }

        val events = gym.events.filter { it.isUpcoming(Moment.now().ymd) }
        if (events.isNotEmpty()) {
            section("Coming up") {
                events.forEachIndexed { i, e -> RowItem(divider = i > 0) { EventItem(e) } }
            }
        }

        if (gym.socials.isNotEmpty()) {
            section("Regular socials & sessions") {
                gym.socials.forEachIndexed { i, s -> RowItem(divider = i > 0) { LinkedText(s) } }
            }
        }

        section("Links") {
            val ctx = LocalContext.current
            val links = listOfNotNull(
                gym.pricesURL?.let { Triple("Prices page", it, Icons.Filled.Payments) },
                gym.website?.let { Triple("Website", it, Icons.Filled.Language) },
                gym.classesURL?.let { Triple("Classes", it, Icons.Filled.School) },
                gym.eventsURL?.let { Triple("Events", it, Icons.Filled.CalendarMonth) },
                gym.routeSettingURL?.let { Triple("Route-setting schedule", it, Icons.Filled.Loop) },
                gym.instagramURL?.let { Triple("@" + gym.instagram, it, Icons.Filled.CameraAlt) },
            )
            links.forEachIndexed { i, (title, url, icon) ->
                ActionRow(icon, title, divider = i > 0, chevron = false) { openUrl(ctx, url) }
            }
            gym.address?.let { RowItem(divider = links.isNotEmpty()) { IconText(Icons.Filled.Place, it, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurface) } }
        }

        section("Your note", footer = "Saved on this phone only. \"Add to Google Maps list\" copies your note and opens the gym in Google Maps: tap Save, pick a list, then paste the note." +
            (gym.lastChecked?.let { " Prices last checked $it." } ?: "")) {
            var first = true
            model.ratings[gym.id]?.let { RowItem(divider = false) { Stars(it) }; first = false }
            model.notes[gym.id]?.let { RowItem(divider = !first) { Text(it) }; first = false }
            val title = if (model.notes[gym.id] == null && model.ratings[gym.id] == null) "Add note & rating" else "Edit note & rating"
            ActionRow(Icons.Filled.Star, title, divider = !first, chevron = false) { editing = true }
            AddToGoogleListRow(gym)
        }
    }
    if (editing) NoteEditor(gym) { editing = false }
}

private fun Double?.priceOrDash() = this?.let { formatPrice(it) } ?: "—"

@Composable
private fun PriceLine(title: String, price: Double, label: String, kind: PriceKind, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = secondaryText)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatPrice(price), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Pill(label, kind.color)
        }
        if (detail.isNotEmpty()) IconText(Icons.Filled.Schedule, detail, MaterialTheme.typography.bodyMedium)
    }
}

/** On a gym page: your visit history there, and a button to start a session. */
private fun androidx.compose.foundation.lazy.LazyListScope.gymVisitsSection(model: AppModel, gym: Gym) {
    section("Your visits") {
        val nav = LocalNav.current
        val here = model.logbook.sessions.filter { it.gymID == gym.id }
        val active = model.logbook.active
        if (active != null && active.gymID == gym.id) {
            ActionRow(Icons.Filled.PlayCircle, "Back to your session", divider = false) { nav.navigate("session/${active.id}") }
        } else {
            ActionRow(Icons.Filled.PlayCircle, "Start a session here", divider = false) { nav.navigate("start?past=false&gym=${gym.id}") }
        }
        if (here.isNotEmpty()) {
            LabeledRow("Visits", "${here.size}")
            LabeledRow("Last visit", fullDateFormat.format(here[0].start))
            here.flatMap { it.sends }.filter { it.discipline == Discipline.BOULDER && it.isGraded }.maxOfOrNull { it.grade }?.let {
                LabeledRow("Hardest boulder here", "V$it")
            }
            for (s in here.take(3)) RowItem(onClick = { nav.navigate("session/${s.id}") }) { VisitRow(s) }
            RowItem(onClick = { nav.navigate("gymVisits/${gym.id}") }) {
                Text(if (here.size > 3) "See all ${here.size} visits" else "Visit history & totals", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Opens public-transport directions in Google Maps (the app if installed, else the website). */
@Composable
fun DirectionsButton(gym: Gym) {
    val model = LocalModel.current
    val ctx = LocalContext.current
    OutlinedButton(onClick = {
        val dest = gym.address ?: gym.postcode ?: "${gym.lat},${gym.lng}"
        val b = Uri.parse("https://www.google.com/maps/dir/").buildUpon().appendQueryParameter("api", "1")
        // With no start location set, Google Maps starts from where you are.
        model.origin?.let { b.appendQueryParameter("origin", "${it.lat},${it.lng}") }
        b.appendQueryParameter("destination", dest).appendQueryParameter("travelmode", "transit")
        openUrl(ctx, b.build().toString())
    }) {
        Icon(Icons.Filled.Directions, null, modifier = Modifier.size(18.dp))
        Text("  Directions")
    }
}

/**
 * Google Maps has no way for apps to add to your saved lists, so this does the
 * next best thing: copies your rating and note, then opens the gym in Google
 * Maps ready for Save → list → paste.
 */
@Composable
private fun AddToGoogleListRow(gym: Gym) {
    val model = LocalModel.current
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ActionRow(Icons.AutoMirrored.Filled.ListAlt, if (copied) "Note copied – opening Google Maps" else "Add to Google Maps list", chevron = false) {
        val parts = listOfNotNull(model.ratings[gym.id]?.let { starString(it) }, model.notes[gym.id])
        val text = if (parts.isEmpty()) gym.name else parts.joinToString(" ")
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Gym note", text))
        copied = true
        openUrl(ctx, Uri.parse("https://www.google.com/maps/search/").buildUpon().appendQueryParameter("api", "1")
            .appendQueryParameter("query", gym.name + ", " + (gym.postcode ?: "London")).build().toString())
    }
}

@Composable
private fun NoteEditor(gym: Gym, close: () -> Unit) {
    val model = LocalModel.current
    // Starts from the saved values, so opening and saving never loses them.
    var rating by remember { mutableIntStateOf(model.ratings[gym.id] ?: 0) }
    var note by remember { mutableStateOf(model.notes[gym.id] ?: "") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(gym.shortName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your rating", style = MaterialTheme.typography.labelLarge)
                StarPicker(rating) { rating = it }
                OutlinedTextField(note, { note = it }, label = { Text("Your note") },
                    placeholder = { Text("e.g. great cafe, quiet in the mornings…") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { model.setRating(rating, gym.id); model.setNote(note, gym.id); close() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/** Five tappable stars; tapping the current rating clears it. */
@Composable
fun StarPicker(value: Int, size: Int = 32, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in 1..5) {
            Icon(if (k <= value) Icons.Filled.Star else Icons.Filled.StarBorder, "$k star${if (k > 1) "s" else ""}", tint = Amber,
                modifier = Modifier.size(size.dp).clickable { onChange(if (value == k) 0 else k) })
        }
    }
}

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

/** Whether the women+ / local prices here count for you, and where to change that; null if the gym has none. */
private fun restrictedNote(gym: Gym, e: Eligibility): String? {
    if (gym.restrictedSlots.isEmpty()) return null
    val mine = gym.restrictedSlots.filter { e.allows(it) }.map { it.label }
    if (mine.size == gym.restrictedSlots.size) return "You qualify for all of these, so they count in your price."
    return (if (mine.isEmpty()) "" else "You qualify for " + mine.joinToString(", ") + ". ") +
        "Women+ and local-resident prices count in your price once your settings in More → Prices for you match."
}

/** A one-off event: what, when, details and a link. */
@Composable
fun EventItem(e: GymEvent) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(e.title, fontWeight = FontWeight.SemiBold)
        Text(e.whenText + (e.price?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        e.details?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        e.url?.let { u -> Text("Details", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { openUrl(ctx, u) }) }
    }
}

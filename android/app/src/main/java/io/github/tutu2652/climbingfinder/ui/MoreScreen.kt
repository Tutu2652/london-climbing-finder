package io.github.tutu2652.climbingfinder.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.Gender
import io.github.tutu2652.climbingfinder.core.Moment
import io.github.tutu2652.climbingfinder.DataSource
import io.github.tutu2652.climbingfinder.LocationService
import io.github.tutu2652.climbingfinder.Origin
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.instagramURL
import io.github.tutu2652.climbingfinder.core.shortName
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@Composable
fun MoreScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Screen("More", back = false) {
        model.update?.let { u ->
            section(footer = "Opens the download. Install it over this copy; your logbook and notes stay.") {
                ActionRow(Icons.Filled.SystemUpdate, "Update the app", divider = false, value = u.versionName?.let { "Version $it" }, chevron = false) {
                    openUrl(ctx, u.url)
                }
            }
        }
        section(footer = "Used for public-transport travel times and sorting by distance.") {
            ActionRow(Icons.Filled.LocationOn, "Your location", divider = false, value = model.origin?.label ?: "Not set") { nav.navigate("origin") }
        }
        section(footer = "Shows women+ session and local-resident prices you can get.") {
            val summary = listOfNotNull(model.gender.takeIf { it != Gender.UNSET }?.title, model.eligibility.district)
                .joinToString(" · ").ifEmpty { "Not set" }
            ActionRow(Icons.Filled.Person, "Prices for you", divider = false, value = summary) { nav.navigate("pricesForYou") }
        }
        section {
            ActionRow(Icons.Filled.LocalOffer, "Deals & offers", divider = false) { nav.navigate("deals") }
            ActionRow(Icons.Filled.CreditCard, "Memberships & passes") { nav.navigate("memberships") }
            ActionRow(Icons.Filled.School, "Classes") { nav.navigate("classes") }
            ActionRow(Icons.Filled.Groups, "Socials, events & route setting") { nav.navigate("events") }
        }
        section(footer = "The Google Maps file has every gym with your notes, ratings and favourites, for Google My Maps.") {
            ActionRow(Icons.Filled.Backup, "Back up logbook", divider = false) { nav.navigate("backup") }
            ActionRow(Icons.Filled.Share, "Export to Google Maps") { nav.navigate("export") }
        }
        section("About the data", footer = "Prices come from the website and are saved for offline use; the app checks for updates when you open it.") {
            LabeledRow("Prices", sourceSummary(model.dataSource), divider = false)
            model.data.lastResearchDate?.let { LabeledRow("Prices researched", it) }
            ActionRow(Icons.Filled.Refresh, "Check for new prices", chevron = false) { scope.launch { model.refreshData(force = true) } }
            LabeledRow("Gyms", "${model.data.gyms.size}")
            LabeledRow("App version", model.appVersion)
            ActionRow(Icons.Filled.Language, "Open the website", chevron = false) { openUrl(ctx, AppModel.SITE + "/") }
        }
        section {
            ActionRow(Icons.Filled.ReportProblem, "Report a wrong price or missing deal", divider = false, chevron = false) {
                openUrl(ctx, "https://docs.google.com/forms/d/e/1FAIpQLSdWqho0guto0OG0XumGWdg8GeAbNj2ulPUFpVbSth3MdW83sA/viewform")
            }
        }
        section("Price colours") {
            ColourRow("peak", PeakPurple, "evenings & weekends", false)
            ColourRow("off-peak", Brand, "weekday daytime", true)
            ColourRow("super off-peak", Amber, "special cheap slots & deals", true)
        }
    }
}

@Composable
private fun ColourRow(label: String, color: androidx.compose.ui.graphics.Color, text: String, divider: Boolean) {
    RowItem(divider = divider) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(label, color); Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun sourceSummary(s: DataSource): String = when (s) {
    DataSource.Bundled -> "Built into the app"
    is DataSource.Cached -> "From the website, " + ago(s.at)
    is DataSource.Website -> "From the website, checked " + timeFormat.format(s.at)
}

private fun ago(t: Instant): String {
    val m = Duration.between(t, Instant.now()).toMinutes()
    return when {
        m < 2 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} days ago"
    }
}

/** Gender (for women+ sessions) and home postcode (for local-resident days). */
@Composable
fun PricesForYouScreen() {
    val model = LocalModel.current
    val focus = LocalFocusManager.current
    var postcode by remember { mutableStateOf(model.homePostcode) }
    Screen("Prices for you") {
        section("Gender", footer = "Woman or non-binary adds women+ session prices, such as Sister Sessions, to your prices.") {
            Gender.entries.forEachIndexed { i, g ->
                RowItem(onClick = { model.chooseGender(g) }, divider = i > 0, chevron = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.title, modifier = Modifier.weight(1f))
                        if (model.gender == g) Icon(Icons.Filled.Check, "Selected", tint = Brand)
                    }
                }
            }
        }
        section("Home postcode", footer = "Adds local-resident days for your postcode district" +
            (model.eligibility.district?.let { " ($it)" } ?: "") + ". Separate from your travel start. Both settings stay on this phone.") {
            RowItem(divider = false) {
                OutlinedTextField(postcode, { postcode = it; model.chooseHomePostcode(it) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. SE16 4DG or SE16") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            }
        }
    }
}

/** Lets you set where travel times are measured from. */
@Composable
fun OriginScreen() {
    Screen("Your location") {
        section(footer = "Travel times come from TfL's Journey Planner; your location is only sent to TfL to plan the journeys.") { OriginPicker() }
    }
}

@Composable
fun OriginPicker() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    fun useCurrent() = scope.launch {
        busy = true; error = null
        val l = LocationService.currentLocation(ctx)
        if (l != null) model.chooseOrigin(Origin(Origin.MY_LOCATION, l.latitude, l.longitude))
        else error = if (LocationService.isAuthorized(ctx)) "Couldn't get your location. Check that location is on, or type a postcode instead."
        else "Location access is off. Allow it in Settings, or type a postcode instead."
        busy = false
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { useCurrent() }

    fun lookUp() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            busy = true; error = null
            try { model.chooseOrigin(LocationService.geocode(ctx, q)); query = ""; focus.clearFocus() } catch (e: Exception) { error = e.message }
            busy = false
        }
    }

    Column {
        model.origin?.let { LabeledRow("From", it.label, divider = false) }
        RowItem(divider = model.origin != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("Postcode or place, e.g. WC2N 5DU") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { lookUp() }))
                if (busy) CircularProgressIndicator(Modifier.padding(start = 10.dp).size(22.dp))
                else TextButton(onClick = { lookUp() }) { Text("Set") }
            }
        }
        ActionRow(Icons.Filled.MyLocation, "Use my current location", chevron = false) {
            if (LocationService.isAuthorized(ctx)) useCurrent()
            else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (model.origin != null) {
            RowItem(onClick = { model.chooseOrigin(null) }, chevron = false) { Text("Clear location", color = Warn) }
        }
        error?.let { RowItem(divider = false) { Text(it, style = MaterialTheme.typography.bodySmall, color = Warn) } }
    }
}

/**
 * Within a chain, sites with identical `signature` share one row ("All sites",
 * or a list of names). Independents are never merged.
 */
fun collapseByBrand(sites: List<Gym>, brand: String, signature: (Gym) -> String): List<Pair<String, Gym>> {
    if (brand == "Independent") return sites.map { it.shortName to it }
    val groups = sites.groupBy(signature).values
    return groups.map { grp ->
        (if (grp.size == sites.size) "All sites" else grp.joinToString(", ") { it.shortName }) to grp[0]
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MembershipsScreen() {
    val model = LocalModel.current
    Screen("Memberships") {
        for ((brand, sites) in brandGroups(model.data.gyms)) {
            section(brand) {
                collapseByBrand(sites, brand) { "${it.membership?.fullMonthly}|${it.membership?.concessionMonthly}|${it.passes}" }
                    .forEachIndexed { i, (label, rep) ->
                        RowItem(divider = i > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rep.membership?.fullMonthly?.let { Pill(formatPrice(it) + "/mo", PeakPurple) }
                                    rep.membership?.concessionMonthly?.let { Pill(formatPrice(it) + "/mo conc", Brand) }
                                }
                                rep.passes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText) }
                            }
                        }
                    }
            }
        }
        item {
            Explainer("Standard monthly rolling membership (unlimited climbing) plus multi-visit passes. Sites in a chain share a row when prices match. Annual memberships are usually cheaper than monthly.")
        }
    }
}

@Composable
fun ClassesScreen() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val rows = remember(model.data) {
        val out = mutableListOf<Triple<String, String?, String?>>()
        val seen = mutableSetOf<String>()
        for ((_, sites) in brandGroups(model.data.gyms)) for (g in sites) {
            if (g.brand == "Independent") { out.add(Triple(g.name, g.classInfo, g.classesURL)); continue }
            if (!seen.add(g.brand)) continue
            var rep = g
            var label = g.brand
            if (g.brand == "The Font") { rep = model.gym("the_font_wandsworth") ?: g; label = "The Font (Wandsworth)" }
            out.add(Triple(label, rep.classInfo, rep.classesURL))
        }
        out
    }
    Screen("Classes") {
        for ((label, info, url) in rows) {
            section(label) {
                (info ?: "Intro sessions & courses").split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, line ->
                    RowItem(divider = i > 0) { LinkedText(line) }
                }
                if (url != null) RowItem(onClick = { openUrl(ctx, url) }, chevron = false) { Text("Class details", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
fun EventsScreen() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val today = Moment.now().ymd
    val upcoming = model.data.gyms
        .flatMap { g -> g.events.filter { it.isUpcoming(today) }.map { g to it } }
        .sortedWith(compareBy({ it.second.date }, { it.second.start ?: "" }))
    Screen("Socials & events") {
        if (upcoming.isNotEmpty()) {
            section("Coming up") {
                upcoming.forEachIndexed { i, (g, e) ->
                    RowItem(onClick = { nav.navigate("gym/${g.id}") }, divider = i > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            EventItem(e)
                            Text(g.name, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        }
                    }
                }
            }
        }
        for ((brand, sites) in brandGroups(model.data.gyms)) {
            section(brand) {
                sites.forEachIndexed { i, g ->
                    RowItem(divider = i > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(g.shortName, style = MaterialTheme.typography.titleSmall)
                            for (s in g.socials) LinkedText("• $s")
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                g.eventsURL?.let { u -> TextButton(onClick = { openUrl(ctx, u) }) { Text("Events") } }
                                g.routeSettingURL?.let { u -> TextButton(onClick = { openUrl(ctx, u) }) { Text("Route setting") } }
                                g.instagramURL?.let { u -> TextButton(onClick = { openUrl(ctx, u) }) { Text("@" + g.instagram) } }
                            }
                        }
                    }
                }
            }
        }
        item {
            Explainer("Times can vary by site and season, so check the gym's events page or Instagram. Only a few gyms publish a route-setting schedule; the rest post resets on Instagram.")
        }
    }
}

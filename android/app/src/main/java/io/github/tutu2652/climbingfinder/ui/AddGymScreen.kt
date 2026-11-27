package io.github.tutu2652.climbingfinder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.LocationService
import io.github.tutu2652.climbingfinder.core.AddedGym
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.GymGrading
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

private val dayNames = mapOf("Mon" to "Monday", "Tue" to "Tuesday", "Wed" to "Wednesday", "Thu" to "Thursday",
    "Fri" to "Friday", "Sat" to "Saturday", "Sun" to "Sunday")

/** Passed back to the screen that opened the form (start a session), so it can choose the new gym. */
const val ADDED_GYM_RESULT = "addedGymID"

/**
 * Add a gym that isn't listed (usually one you're visiting while travelling, so you can log
 * sessions there), or edit one you added (`id`). It stays on this phone.
 *
 * Only a name and a pin are needed. "I'm here now" or a search drops the pin and fills in the
 * area, address, country and the gym's time zone, which you can still change.
 */
@Composable
fun AddGymScreen(id: String?, initialName: String = "") {
    val model = LocalModel.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = remember(id) { id?.let { model.addedGym(it) } }

    var name by rememberSaveable { mutableStateOf(editing?.name ?: initialName) }
    var area by rememberSaveable { mutableStateOf(editing?.area ?: "") }
    var country by rememberSaveable { mutableStateOf(editing?.country ?: "") }
    var postcode by rememberSaveable { mutableStateOf(editing?.postcode ?: "") }
    var address by rememberSaveable { mutableStateOf(editing?.address ?: "") }
    var timeZone by rememberSaveable { mutableStateOf(editing?.timeZone) }
    var pinLat by rememberSaveable { mutableStateOf(editing?.lat) }
    var pinLng by rememberSaveable { mutableStateOf(editing?.lng) }
    var type by rememberSaveable { mutableStateOf(editing?.type ?: "bouldering") }
    var boulderGrading by rememberSaveable {
        val default = if (editing != null && model.colourScale(editing.id).isNotEmpty()) GymGrading.COLOUR else GymGrading.V
        mutableStateOf(editing?.boulderGrading ?: default)
    }
    var ropeGrading by rememberSaveable { mutableStateOf(editing?.ropeGrading ?: GymGrading.FRENCH) }
    var boulderLabels by rememberSaveable { mutableStateOf(editing?.boulderGradeLabels.orEmpty().joinToString()) }
    var ropeLabels by rememberSaveable { mutableStateOf(editing?.ropeGradeLabels.orEmpty().joinToString()) }
    val boulderGrades = GymGrading.parseLabels(boulderLabels).takeIf { boulderGrading.usesEditableLabels && it.isNotEmpty() }
    val ropeGrades = GymGrading.parseLabels(ropeLabels).takeIf { ropeGrading.usesEditableLabels && it.isNotEmpty() }
    val validGrading = GymGrading.validLabels(boulderGrades) && GymGrading.validLabels(ropeGrades)
    var adultPrice by rememberSaveable { mutableStateOf(editing?.adultPrice?.let { priceText(it) } ?: "") }
    var concessionPrice by rememberSaveable { mutableStateOf(editing?.concessionPrice?.let { priceText(it) } ?: "") }
    val open = remember { mutableStateMapOf<String, Boolean>().apply { editing?.hours?.keys?.forEach { put(it, true) } } }
    val from = remember { mutableStateMapOf<String, String>().apply { editing?.hours?.forEach { (d, t) -> put(d, t[0]) } } }
    val to = remember { mutableStateMapOf<String, String>().apply { editing?.hours?.forEach { (d, t) -> put(d, t[1]) } } }
    var website by rememberSaveable { mutableStateOf(editing?.website ?: "") }
    var notes by rememberSaveable { mutableStateOf(editing?.notes ?: "") }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var findError by remember { mutableStateOf<String?>(null) }
    /** What the last lookup filled in, so a later one replaces those but never what you typed. */
    val filled = remember { mutableMapOf<String, String>() }

    /** The looked-up value for a field, unless you've typed something of your own there. */
    fun take(key: String, current: String, value: String?): String? {
        if (value == null || (current.isNotEmpty() && current != filled[key])) return null
        filled[key] = value
        return value
    }

    /** Moves the pin, then fills in the address, country and time zone from where it is. */
    fun place(lat: Double, lng: Double) {
        pinLat = lat; pinLng = lng
        scope.launch {
            val p = LocationService.describe(ctx, lat, lng) ?: return@launch
            if (pinLat != lat || pinLng != lng) return@launch   // moved again meanwhile
            p.timeZone?.let { timeZone = it }
            take("area", area, p.area)?.let { area = it }
            take("address", address, p.address)?.let { address = it }
            take("postcode", postcode, p.postcode)?.let { postcode = it }
            take("country", country, p.country)?.let { country = it }
        }
    }

    fun useMyLocation() = scope.launch {
        findError = null; busy = true
        val l = LocationService.currentLocation(ctx)
        if (l != null) {
            // You're standing there, so the phone's own time zone is right if the lookup fails.
            if (timeZone == null) timeZone = TimeZone.getDefault().id
            place(l.latitude, l.longitude)
        } else findError = if (LocationService.isAuthorized(ctx)) "Couldn't get your location. Search or tap the map instead."
            else "Location access is off. Search or tap the map instead."
        busy = false
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { useMyLocation() }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            findError = null; busy = true
            try { LocationService.geocode(ctx, q, inLondon = false).let { place(it.lat, it.lng) } }
            catch (e: Exception) { findError = "Couldn't find that. Try the address or city, or tap the map." }
            busy = false
        }
    }

    fun hoursFor(d: String) = listOf(from[d] ?: "09:00", to[d] ?: "22:00")
    val badDays = AddedGym.DAYS.filter { open[it] == true && hoursFor(it).let { h -> h[0] >= h[1] } }

    /** The gym as entered, or null while the name or pin is missing, or something is wrong. */
    val draft: AddedGym? = run {
        val lat = pinLat ?: return@run null
        val lng = pinLng ?: return@run null
        val adult = parsePrice(adultPrice) ?: return@run null
        val conc = parsePrice(concessionPrice) ?: return@run null
        if (badDays.isNotEmpty()) return@run null
        var site = website.trim().ifEmpty { null }
        if (site != null && "://" !in site) site = "https://$site"
        AddedGym(
            id = editing?.id ?: "${AddedGym.ID_PREFIX}new",   // the real id is made on save
            name = name.trim(), area = area.trim(), country = country.trim().ifEmpty { null },
            postcode = postcode.trim().uppercase(Locale.ROOT).ifEmpty { null }, address = address.trim().ifEmpty { null },
            lat = lat, lng = lng, timeZone = timeZone, type = type,
            adultPrice = adult.value, concessionPrice = conc.value,
            hours = AddedGym.DAYS.filter { open[it] == true }.associateWith { hoursFor(it) },
            website = site, notes = notes.trim().ifEmpty { null },
            boulderGrading = if (type == "ropes") editing?.boulderGrading else boulderGrading,
            ropeGrading = ropeGrading.takeUnless { it == GymGrading.FRENCH },
            boulderGradeLabels = boulderGrades, ropeGradeLabels = ropeGrades,
        ).takeIf { it.isValid }
    }

    Screen(if (editing == null) "Add a gym" else "Edit gym", actions = {
        TextButton(enabled = draft != null, onClick = {
            draft?.let { d ->
                val g = if (editing == null) d.copy(id = AddedGym.newId(model.takenGymIDs)) else d
                model.saveAddedGym(g)
                nav.previousBackStackEntry?.savedStateHandle?.set(ADDED_GYM_RESULT, g.id)
                nav.popBackStack()
            }
        }) { Text("Save") }
    }) {
        section {
            Column(Modifier.padding(14.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Gym name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }
        }

        section("Where is it?", footer = if (pinLat == null) "Use your location if you're at the gym, search for it, or tap the map."
            else "Tap the map to move the pin.") {
            ActionRow(Icons.Filled.MyLocation, "I'm here now: use my location", divider = false, chevron = false) {
                if (LocationService.isAuthorized(ctx)) useMyLocation()
                else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            RowItem {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("Or search: address or city") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }))
                    if (busy) CircularProgressIndicator(Modifier.padding(start = 10.dp).size(22.dp))
                    else if (query.isNotBlank()) TextButton(onClick = { search() }) { Text("Find") }
                }
            }
            findError?.let { RowItem { Text(it, style = MaterialTheme.typography.bodySmall, color = Warn) } }
            RowItem(padding = PaddingValues(0.dp)) {
                PinMap(pinLat, pinLng, name) { lat, lng -> place(lat, lng) }
            }
        }

        if (pinLat != null) {
            section("Filled in from the pin", footer = "Change anything that's wrong.") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(area, { area = it }, label = { Text("Area or city") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(postcode, { postcode = it }, label = { Text("Postcode") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    OutlinedTextField(country, { country = it }, label = { Text("Country") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                timeZone?.let { tz -> localTimeText(tz)?.let { LabeledRow("Local time", it) } }
            }
        }

        section("Details (optional)", footer = "Prices are in pounds, and fill in a session's entry price. " +
            "A gym outside London isn't compared with London gyms in Cheapest.") {
            for ((value, title) in listOf("bouldering" to "Bouldering", "ropes+bouldering" to "Ropes + bouldering", "ropes" to "Ropes")) {
                RowItem(onClick = { type = value }, chevron = false, divider = value != "bouldering") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(type == value, null)
                        Text(title, Modifier.padding(start = 10.dp))
                    }
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(adultPrice, { adultPrice = it }, label = { Text("Adult day pass") }, singleLine = true, prefix = { Text("£") },
                    isError = parsePrice(adultPrice) == null, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(concessionPrice, { concessionPrice = it }, label = { Text("Student / concession") }, singleLine = true,
                    prefix = { Text("£") }, isError = parsePrice(concessionPrice) == null, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(website, { website = it }, label = { Text("Website") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        }

        section("Grading") {
            if (type != "ropes") {
                GymGradingField("Bouldering", GymGrading.boulderChoices, boulderGrading, { boulderGrading = it }, boulderLabels, { boulderLabels = it })
            }
            if (type.contains("ropes")) {
                GymGradingField("Ropes", GymGrading.ropeChoices, ropeGrading, { ropeGrading = it }, ropeLabels, { ropeLabels = it })
            }
            if (type != "ropes" && boulderGrading == GymGrading.COLOUR) {
                RowItem { Text("Set up the circuit colours when you log your first session.", style = MaterialTheme.typography.bodySmall) }
            }
        }

        section("Opening hours (optional)", footer = if (badDays.isNotEmpty()) "Closing time must be after opening time (${badDays.joinToString()})."
            else "In the gym's local time. Leave every day off if you don't know; once any day is on, days left off count as closed.") {
            AddedGym.DAYS.forEachIndexed { i, d ->
                RowItem(divider = i > 0, onClick = { open[d] = !(open[d] ?: false) }, chevron = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dayNames.getValue(d), Modifier.weight(1f))
                        if (open[d] == true) {
                            TimeButton(from[d] ?: "09:00") { from[d] = it }
                            Text("to", color = secondaryText)
                            TimeButton(to[d] ?: "22:00") { to[d] = it }
                        }
                        Switch(open[d] == true, { open[d] = it })
                    }
                }
            }
            val first = AddedGym.DAYS.firstOrNull { open[it] == true }
            if (first != null && AddedGym.DAYS.any { open[it] != true }) {
                RowItem(onClick = {
                    val h = hoursFor(first)
                    for (d in AddedGym.DAYS) { open[d] = true; from[d] = h[0]; to[d] = h[1] }
                }, chevron = false) { Text("Same hours every day", color = MaterialTheme.colorScheme.primary) }
            }
        }

        item { Explainer("Kept on this phone only, and in your logbook backups. It isn't sent anywhere or added to the gym list.") }
        if (draft == null) {
            item { Explainer(if (name.isBlank() || pinLat == null) "Save needs a name and a pin on the map." else if (!validGrading) "Check the grade labels." else "Check the prices and opening hours.") }
        }
    }
}

@Composable
private fun GymGradingField(
    title: String, choices: List<GymGrading>, grading: GymGrading, onGrading: (GymGrading) -> Unit,
    labels: String, onLabels: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        LabeledRow(title, grading.title) { menu = true }
        DropdownMenu(menu, { menu = false }) {
            for (choice in choices) {
                DropdownMenuItem({ Text(choice.title) }, { menu = false; onGrading(choice) })
            }
        }
    }
    if (grading.usesEditableLabels) {
        val parsed = GymGrading.parseLabels(labels)
        val valid = GymGrading.validLabels(parsed.ifEmpty { null })
        RowItem {
            OutlinedTextField(labels, onLabels, label = { Text("$title grade labels, easiest first") },
                modifier = Modifier.fillMaxWidth(), isError = !valid, supportingText = {
                    Text(when {
                        !valid -> "Use up to 60 distinct labels, with at most 24 characters per label."
                        grading == GymGrading.BANANA -> "Defaults to 0–20. Enter your branch’s labels separated by commas to change them."
                        else -> "Separate labels with commas, for example 1, 2, 3, 4. Defaults to 1–10."
                    })
                })
        }
    }
}

/** "" is no price (an EnteredPrice holding null); anything that isn't a sensible price is null. */
private fun parsePrice(s: String): EnteredPrice? {
    val t = s.replace("£", "").replace(",", ".").trim()
    if (t.isEmpty()) return EnteredPrice(null)
    val p = t.toDoubleOrNull() ?: return null
    return if (p.isFinite() && p >= 0 && p < 1000) EnteredPrice(p) else null
}

private class EnteredPrice(val value: Double?)

private fun priceText(p: Double) = String.format(Locale.UK, "%.2f", p)

/** "14:05 (1 hour ahead of you)", or null if the zone is unknown or the same as the phone's. */
fun localTimeText(timeZone: String, now: Instant = Instant.now()): String? {
    val zone = runCatching { ZoneId.of(timeZone) }.getOrNull() ?: return null
    val mine = ZoneId.systemDefault()
    if (zone.normalized() == mine.normalized() || zone.id == mine.id) return null
    val diff = (zone.rules.getOffset(now).totalSeconds - mine.rules.getOffset(now).totalSeconds) / 60
    val time = DateTimeFormatter.ofPattern("HH:mm").format(now.atZone(zone))
    if (diff == 0) return time
    val h = Math.abs(diff) / 60.0
    val amount = if (h == Math.floor(h)) "${h.toInt()} hour${if (h == 1.0) "" else "s"}" else String.format(Locale.UK, "%.1f hours", h)
    return "$time ($amount ${if (diff > 0) "ahead of" else "behind"} you)"
}

@Composable
private fun TimeButton(time: String, set: (String) -> Unit) {
    val ctx = LocalContext.current
    TextButton(onClick = {
        val (h, m) = time.split(":").map { it.toInt() }
        TimePickerDialog(ctx, { _, hh, mm -> set(String.format(Locale.UK, "%02d:%02d", hh, mm)) }, h, m, true).show()
    }) { Text(time) }
}

/** An OpenStreetMap map (works worldwide) you tap to place, or move, the gym's pin. */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun PinMap(lat: Double?, lng: Double?, name: String, onTap: (Double, Double) -> Unit) {
    val ctx = LocalContext.current
    val model = LocalModel.current
    val tap = rememberUpdatedState(onTap)
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            val start = if (lat != null && lng != null) GeoPoint(lat, lng)
                else model.origin?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(51.515, -0.12)
            controller.setZoom(if (lat != null || model.origin != null) 15.0 else 11.0)
            controller.setCenter(start)
            overlays.add(CopyrightOverlay(ctx))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { tap.value(p.latitude, p.longitude); return true }
                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            // Panning the map shouldn't scroll the page.
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose { lifecycle.removeObserver(obs); map.onPause() }
    }
    // The last pin drawn; not state, as it's only read when drawing.
    val shown = remember { arrayOfNulls<Pair<Double, Double>>(1) }
    AndroidView({ map }, Modifier.fillMaxWidth().height(220.dp)) { m ->
        m.overlays.removeAll { it is Marker }
        if (lat != null && lng != null) {
            m.overlays.add(Marker(m).apply {
                position = GeoPoint(lat, lng)
                title = name.ifBlank { "Gym" }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
            // Follow a pin found by search or your location.
            if (shown[0] != lat to lng && !m.boundingBox.contains(GeoPoint(lat, lng))) m.controller.animateTo(GeoPoint(lat, lng), 15.0, 400L)
            shown[0] = lat to lng
        }
        m.invalidate()
    }
}

/** On the page of a gym you added: its local time if that differs, and Edit / Delete. */
fun LazyListScope.addedGymSection(model: AppModel, gym: Gym) {
    section("Added by you", footer = "Opening hours are the gym's local time. Only on this phone; it isn't shared or added to the gym list for anyone else.") {
        val nav = LocalNav.current
        var confirm by remember { mutableStateOf(false) }
        val local = model.addedGym(gym.id)?.timeZone?.let { localTimeText(it) }
        if (local != null) LabeledRow("Local time there", local, divider = false)
        ActionRow(Icons.Filled.Edit, "Edit this gym", divider = local != null) { nav.navigate("addGym?id=${gym.id}") }
        ActionRow(Icons.Filled.Delete, "Delete this gym", tint = Warn, chevron = false) { confirm = true }
        if (confirm) {
            val visits = model.logbook.sessions.count { it.gymID == gym.id }
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Delete ${gym.name}?") },
                text = {
                    Text(if (visits == 0) "Your note and rating for it go too."
                    else "Your $visits logged visit${if (visits == 1) "" else "s"} here stay in your logbook, shown as an unknown gym.")
                },
                confirmButton = {
                    TextButton(onClick = { confirm = false; model.deleteAddedGym(gym.id); nav.popBackStack() }) { Text("Delete gym", color = Warn) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            )
        }
    }
}

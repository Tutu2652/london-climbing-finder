package io.github.tutu2652.climbingfinder.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.LogbookBackup
import io.github.tutu2652.climbingfinder.core.Moment
import io.github.tutu2652.climbingfinder.core.bestOffpeak
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.typeLabel

/** Exports the logbook as a JSON file, and adds sessions back from one (from this app or the iPhone app). */
@Composable
fun LogbookBackupScreen() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val sessions = model.logbook.sessions
    var message by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) message = if (write(ctx, uri, model.logbook.backupText())) "Saved." else "Couldn't save that file."
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = try {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val added = model.logbook.restore(LogbookBackup.decode(text))
            if (added == 0) "Nothing new in that file." else "Added $added session${if (added == 1) "" else "s"}."
        } catch (e: Exception) {
            "Couldn't read that file: " + (e.message ?: "it isn't a logbook backup.")
        }
    }
    Screen("Back up logbook") {
        section(footer = "Your logbook is kept on this phone. Uninstalling the app deletes it, so save a backup to Google Drive or Files now and then. " +
            "Restoring only adds sessions you don't already have, and reads backups from the iPhone app too.") {
            LabeledRow("Sessions", "${sessions.size}", divider = false)
            LabeledRow("Climbs", "${sessions.sumOf { it.climbs.size }}")
            if (sessions.isNotEmpty()) {
                ActionRow(Icons.Filled.Upload, "Export backup file", chevron = false) { save.launch("Climbing logbook ${Moment.now().ymd}.json") }
            }
            ActionRow(Icons.Filled.Download, "Restore from a backup file", chevron = false) { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            message?.let { RowItem { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText) } }
        }
    }
}

private fun write(ctx: Context, uri: Uri, text: String): Boolean = runCatching {
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
}.isSuccess

/**
 * Exports gyms, with your notes, ratings and favourites, as a KML file that
 * Google My Maps can import. The map then shows up in Google Maps.
 */
@Composable
fun ExportScreen() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("climbing", Context.MODE_PRIVATE) }
    var onlyMine by remember { mutableStateOf(prefs.getBoolean("exportOnlyMine", false)) }
    var message by remember { mutableStateOf<String?>(null) }
    val gyms = model.data.gyms.filter { g -> !onlyMine || model.isFavourite(g.id) || g.id in model.notes || g.id in model.ratings }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri ->
        if (uri != null) message = if (write(ctx, uri, kml(model, gyms))) "Saved." else "Couldn't save that file."
    }
    Screen("Export to Google Maps") {
        section(footer = "Each gym's pin includes your rating, note, favourite heart, prices and address, grouped by chain.") {
            RowItem(onClick = { onlyMine = !onlyMine; prefs.edit().putBoolean("exportOnlyMine", onlyMine).apply() }, divider = false, chevron = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Only favourites and gyms with notes", Modifier.weight(1f))
                    Switch(onlyMine, { onlyMine = it; prefs.edit().putBoolean("exportOnlyMine", it).apply() })
                }
            }
            LabeledRow("Gyms in the file", "${gyms.size}")
            if (gyms.isNotEmpty()) ActionRow(Icons.Filled.Upload, "Export for Google My Maps", chevron = false) { save.launch("London climbing gyms.kml") }
            message?.let { RowItem { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText) } }
        }
        section("Getting it into Google Maps") {
            RowItem(divider = false) { Text("1. Tap Export and save the file to Google Drive (or send it to a computer).") }
            RowItem { Text("2. On a computer, open mymaps.google.com, create a map, then choose Import on its layer and pick the file.") }
            RowItem { Text("3. In the Google Maps app the map appears under You → Maps.") }
        }
    }
}

private fun kml(model: AppModel, gyms: List<Gym>): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val out = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>London climbing gyms</name>\n")
    for ((brand, sites) in brandGroups(gyms)) {
        out.append("<Folder><name>${esc(brand)}</name>\n")
        for (g in sites) {
            val lines = mutableListOf<String>()
            val mine = listOfNotNull(if (model.isFavourite(g.id)) "♥ Favourite" else null, model.ratings[g.id]?.let { starString(it) })
            if (mine.isNotEmpty()) lines.add(mine.joinToString(" · "))
            model.notes[g.id]?.let { lines.add("My note: $it") }
            lines.add("Peak " + formatPrice(g.payg.adultPeak) + " (" + formatPrice(g.payg.concessionPeak) + " conc)" +
                (g.bestOffpeak(false)?.let { " · deals from " + formatPrice(it) } ?: ""))
            lines.add(g.typeLabel + " · Zone ${g.zone}")
            g.address?.let { lines.add(it) }
            g.website?.let { lines.add(it) }
            out.append("<Placemark><name>${esc(g.name)}</name><description>${esc(lines.joinToString("\n"))}</description>")
            out.append("<Point><coordinates>${g.lng},${g.lat},0</coordinates></Point></Placemark>\n")
        }
        out.append("</Folder>\n")
    }
    return out.append("</Document></kml>\n").toString()
}

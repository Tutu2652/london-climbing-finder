package io.github.tutu2652.climbingfinder.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.MagicSlot
import io.github.tutu2652.climbingfinder.core.Moment
import io.github.tutu2652.climbingfinder.core.Promotion
import io.github.tutu2652.climbingfinder.core.slots
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.shortName
import io.github.tutu2652.climbingfinder.core.weekdays

private enum class DealsTab { Offers, Students, Weekly }

@Composable
fun DealsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("climbing", Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf(runCatching { DealsTab.valueOf(prefs.getString("dealsSection", "Offers")!!) }.getOrDefault(DealsTab.Offers)) }
    Screen("Deals") {
        item {
            Segmented(DealsTab.entries, tab, { it.name }, Modifier.padding(horizontal = 14.dp)) {
                tab = it; prefs.edit().putString("dealsSection", it.name).apply()
            }
        }
        when (tab) {
            DealsTab.Offers -> offers()
            DealsTab.Students -> students()
            DealsTab.Weekly -> weekly()
        }
    }
}

// --- Limited-time offers

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.offers() {
    item {
        val model = LocalModel.current
        val ctx = LocalContext.current
        val today = Moment.now().ymd
        val all = model.data.limitedPromotions
        val live = all.filter { !it.isExpired(today) }
        val hidden = all.size - live.size
        Column {
            GroupCard(null) {
                if (live.isEmpty()) RowItem(divider = false) { Text("No limited-time offers running right now.", color = secondaryText) }
                live.forEachIndexed { i, p ->
                    RowItem(divider = i > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        logoGym(model.data.gyms, p)?.let { GymLogo(it, 36.dp) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(p.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                if (p.status == "unverified") Text("verify", style = MaterialTheme.typography.labelSmall, color = Warn, fontWeight = FontWeight.SemiBold)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.price?.let { Pill(it, Amber) }
                                Text(p.brand, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                            }
                            p.details?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            p.valid?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Amber) }
                            p.expires?.let { Text("Ends $it", style = MaterialTheme.typography.bodySmall, color = secondaryText) }
                            p.source?.let { s -> TextButton(onClick = { openUrl(ctx, s) }) { Text("View offer") } }
                        }
                        }
                    }
                }
            }
            Explainer("Seasonal and limited-time offers, separate from the standing deals on each gym's page." +
                (if (hidden > 0) " $hidden offer${if (hidden == 1) "" else "s"} past the published end date ${if (hidden == 1) "is" else "are"} hidden." else "") +
                " Most gyms don't publish an end date, so check the link before you buy.")
        }
    }
}

/**
 * The gym whose logo stands for an offer: any site of its chain, or for an independent,
 * the gym the title starts with ("Substation: …" → Substation Brixton).
 */
private fun logoGym(gyms: List<Gym>, p: Promotion): Gym? {
    if (p.brand != "Independent") gyms.firstOrNull { it.brand == p.brand }?.let { return it }
    val key = p.title.substringBefore(":").takeIf { it != p.title } ?: return null
    return gyms.firstOrNull { it.brand == "Independent" && (it.name.startsWith(key) || key.startsWith(it.name)) }
}

// --- Student discounts

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.students() {
    item(key = "students") {
        val model = LocalModel.current
        val nav = LocalNav.current
        val ctx = LocalContext.current
        Column {
            for ((brand, sites) in brandGroups(model.data.gyms)) {
                GroupCard(brand) {
                    collapseByBrand(sites, brand) { "${it.payg.concessionPeak}|${it.payg.concessionOffpeak}|${it.studentNote}" }
                        .forEachIndexed { i, (label, rep) ->
                            RowItem(onClick = { nav.navigate("gym/${rep.id}") }, divider = i > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GymLogo(rep, 36.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = MaterialTheme.typography.titleSmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill("peak " + formatPrice(rep.payg.concessionPeak), PeakPurple)
                                        rep.payg.concessionOffpeak?.let { Pill("off-peak " + formatPrice(it), Brand) }
                                    }
                                    rep.studentNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText) }
                                }
                                }
                            }
                        }
                }
            }
            if (model.data.studentPromotions.isNotEmpty()) {
                GroupCard("Group-wide student promotions") {
                    model.data.studentPromotions.forEachIndexed { i, p ->
                        RowItem(divider = i > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(p.offer, fontWeight = FontWeight.SemiBold)
                                Text(p.scope, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                                p.source?.let { s -> TextButton(onClick = { openUrl(ctx, s) }) { Text("Source") } }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Weekly deals calendar

private val fullDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun androidx.compose.foundation.lazy.LazyListScope.weekly() {
    section("Subscribe in Google Calendar", footer = "Adds every special cheap slot as weekly repeating events, and updates when they change.") {
        val ctx = LocalContext.current
        ActionRow(Icons.Filled.EditCalendar, "All deals", divider = false, chevron = false) { subscribe(ctx, "london-climbing-deals-all.ics") }
        ActionRow(Icons.Filled.EditCalendar, "Without student-only deals", chevron = false) { subscribe(ctx, "london-climbing-deals-no-student.ics") }
    }
    for (i in 0 until 7) {
        item(key = "day$i") {
            val model = LocalModel.current
            val nav = LocalNav.current
            val day = (model.moment.weekdayIndex + i) % 7
            // Women+ and local-resident slots appear here once your settings qualify for them.
            val items = model.data.gyms.flatMap { g -> g.slots(model.eligibility).filter { weekdays[day] in it.days }.map { g to it } }
                .sortedBy { it.second.price }
            GroupCard(if (i == 0) "Today · " + fullDays[day] else fullDays[day]) {
                if (items.isEmpty()) RowItem(divider = false) { Text("—", color = secondaryText) }
                items.forEachIndexed { k, (g, s) ->
                    RowItem(onClick = { nav.navigate("gym/${g.id}") }, divider = k > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GymLogo(g, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(s.label.replaceFirst(" (concession)", "") + " · " + g.shortName)
                                Text(detail(s), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                            }
                            Pill(formatPrice(s.price), Amber)
                        }
                    }
                }
            }
        }
    }
}

private fun detail(s: MagicSlot) =
    (if (s.isAllDay) "all day" else s.start + "–" + s.end) +
        (if (s.prebook) " (pre-book)" else "") + (if (s.isConcession) " · conc only" else "") +
        (if (s.exceptBankHolidays) " · not bank hols" else "") +
        (if (s.condition.isEmpty()) "" else " · ${s.condition} only")

/** Google Calendar's "add by URL" page, already filled in with the feed. */
private fun subscribe(ctx: Context, file: String) {
    val feed = "webcal://" + AppModel.SITE.removePrefix("https://") + "/" + file
    openUrl(ctx, "https://calendar.google.com/calendar/r?cid=" + Uri.encode(feed))
}

/** The same card as `section`, for use inside an item that already has its own data. */
@Composable
fun GroupCard(header: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = if (header == null) 12.dp else 18.dp)) {
        if (header != null) {
            Text(header.uppercase(), style = MaterialTheme.typography.labelMedium, color = secondaryText,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 6.dp))
        }
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
    }
}

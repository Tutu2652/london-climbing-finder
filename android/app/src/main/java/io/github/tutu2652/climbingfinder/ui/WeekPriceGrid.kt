package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.PriceBand
import io.github.tutu2652.climbingfinder.core.dayBands
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.hhmm
import io.github.tutu2652.climbingfinder.core.shortPrice
import io.github.tutu2652.climbingfinder.core.week

/** Block colours, cheapest first: background to text. Same as the website's gym pages. */
private val ramp = listOf(
    Color(0xFF75CA91) to Color(0xFF06210F), Color(0xFF5AA877) to Color(0xFF06210F), Color(0xFF2F7350) to Color.White,
    Color(0xFF2B5A4A) to Color.White, Color(0xFF2F4A4D) to Color.White, Color(0xFF343A47) to Color(0xFFE8EBF0),
)

private val dayWidth = 54.dp
private val rowHeight = 30.dp

/** Which days of the shown week are bank holidays, for the section footer. */
fun bankHolidayNote(model: AppModel): String {
    val days = model.dayMoment.week.filter { model.pricer.isBankHoliday(it) }.map { "${it.weekday} ${it.day}" }
    if (days.isEmpty()) return ""
    return " " + days.joinToString(" and ") + (if (days.size > 1) " are bank holidays" else " is a bank holiday") + ", so some deals don't apply."
}

/**
 * The gym page's "Prices through the week": one row per day, each split into blocks of one
 * price across the opening hours. Tap a block for its times and what the rate is. It shows
 * the week of the date picked above, so bank holidays are priced correctly.
 */
@Composable
fun WeekPriceGrid(gym: Gym) {
    val model = LocalModel.current
    val days = model.dayMoment.week
    val week = days.map { model.pricer.dayBands(gym, it, model.concession) }
    val all = week.flatten()
    val cheapest = all.minByOrNull { it.price }
    if (cheapest == null) {
        Text("No opening hours this week.", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        return
    }
    var picked by remember { mutableStateOf<PriceBand?>(null) }
    val shown = picked?.takeIf { it in all }
    val prices = all.map { it.price }.distinct().sorted()
    val first = all.minOf { it.start } / 60 * 60
    val last = (all.maxOf { it.end } + 59) / 60 * 60
    val span = (last - first).toFloat()
    fun colour(price: Double): Pair<Color, Color> {
        val i = prices.indexOf(price)
        if (prices.size < 2 || i < 0) return ramp.last()
        return ramp[Math.round(i * (ramp.size - 1) / (prices.size - 1).toFloat())]
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val band = shown ?: cheapest
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (shown == null) "Cheapest this week" else "Selected", style = MaterialTheme.typography.bodySmall, color = secondaryText)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatPrice(band.price), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Pill(band.label, band.kind.color)
            }
            IconText(Icons.Filled.Schedule, "${band.day.weekday} ${band.day.day} · ${hhmm(band.start)}–${hhmm(band.end)}", MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = dayWidth).height(16.dp).clearAndSetSemantics {}) {
                for (h in ((first + 179) / 180 * 3)..(last / 60) step 3) {
                    val x = maxWidth * ((h * 60 - first) / span)
                    if (x < maxWidth - 14.dp) {
                        Row(Modifier.offset(x = x), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(1.dp).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Text("$h", style = MaterialTheme.typography.labelSmall, color = secondaryText, modifier = Modifier.padding(start = 3.dp))
                        }
                    }
                }
            }
            days.forEachIndexed { i, day ->
                val today = day == model.dayMoment
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.weekday} ${day.day}", style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                        color = if (model.pricer.isBankHoliday(day)) Amber else if (today) MaterialTheme.colorScheme.onSurface else secondaryText,
                        modifier = Modifier.width(dayWidth))
                    if (week[i].isEmpty()) {
                        Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                            Text("Closed", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        }
                    } else {
                        BoxWithConstraints(Modifier.weight(1f).height(rowHeight)) {
                            for (b in week[i]) {
                                val width = (maxWidth * ((b.end - b.start) / span) - 2.dp).coerceAtLeast(4.dp)
                                val text = shortPrice(b.price)
                                val (fill, ink) = colour(b.price)
                                val shape = RoundedCornerShape(4.dp)
                                Box(
                                    Modifier.offset(x = maxWidth * ((b.start - first) / span)).width(width).height(rowHeight)
                                        .clip(shape).background(fill)
                                        .then(if (b == shown) Modifier.border(2.dp, ink, shape) else Modifier)
                                        .clickable(role = Role.Button) { picked = b }
                                        .semantics {
                                            contentDescription = "${b.day.weekday} ${hhmm(b.start)} to ${hhmm(b.end)}, ${formatPrice(b.price)}, ${b.label}"
                                            selected = b == shown
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (width >= (text.length * 7 + 6).dp) {
                                        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ink,
                                            maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.clearAndSetSemantics {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.core.Climb
import io.github.tutu2652.climbingfinder.core.ClimbResult
import io.github.tutu2652.climbingfinder.core.ColourGrade
import io.github.tutu2652.climbingfinder.core.Discipline
import io.github.tutu2652.climbingfinder.core.HoldColour

/** A small dot in a hold colour. */
@Composable
fun ColourSwatch(name: String, size: Dp = 12.dp) {
    Box(
        Modifier.size(size)
            .background(parseHex(HoldColour.hex(name)), CircleShape)
            .border(1.dp, secondaryText.copy(alpha = 0.45f), CircleShape),
    )
}

/** Set up the colours a gym grades its boulders by, and the V range each covers. */
@Composable
fun ColourScaleScreen(gymID: String) {
    val model = LocalModel.current
    val scale = model.colourScale(gymID)
    fun save(new: List<ColourGrade>) = model.setColourScale(gymID, new)
    fun change(i: Int, f: (ColourGrade) -> ColourGrade) = save(scale.mapIndexed { j, c -> if (j == i) f(c) else c })
    var adding by remember { mutableStateOf(false) }
    val unused = HoldColour.all.map { it.first }.filter { n -> scale.none { it.name == n } }

    Screen("Colour grades") {
        section(
            if (scale.isEmpty()) "Colours" else "Easiest first",
            footer = "Copy the grade chart on the gym's wall. Each climb keeps the range it was logged with, so changing " +
                "these after a reset won't rewrite past sessions. Climbs count in stats as the bottom of their range " +
                "unless you set how they felt. Colours with no V grade count as sends, but not in grade stats.",
        ) {
            scale.forEachIndexed { i, c ->
                RowItem(divider = i > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColourSwatch(c.name, 16.dp)
                            Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(c.range.ifEmpty { "No V grade" }, color = secondaryText)
                            IconButton(onClick = { save(scale.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Move ${c.name} up")
                            }
                            IconButton(onClick = { save(scale.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < scale.size - 1) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Move ${c.name} down")
                            }
                            IconButton(onClick = { save(scale.filterIndexed { j, _ -> j != i }) }) {
                                Icon(Icons.Filled.Delete, "Remove ${c.name}", tint = Warn)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // A range has both ends or neither, and "To" can't be easier than "From":
                            // choosing one end fills in or moves the other.
                            GradeChooser("From", c.low) { g ->
                                change(i) { if (g == null) it.copy(low = null, high = null) else it.copy(low = g, high = maxOf(it.high ?: g, g)) }
                            }
                            GradeChooser("To", c.high) { g ->
                                change(i) { if (g == null) it.copy(low = null, high = null) else it.copy(high = g, low = minOf(it.low ?: g, g)) }
                            }
                        }
                    }
                }
            }
            if (unused.isNotEmpty()) {
                Box {
                    ActionRow(Icons.Filled.AddCircle, "Add a colour", divider = scale.isNotEmpty(), chevron = false) { adding = true }
                    DropdownMenu(adding, { adding = false }) {
                        for (name in unused) {
                            DropdownMenuItem({ Text(name) }, {
                                adding = false
                                val (low, high) = ColourGrade.nextRange(scale)
                                save(scale + ColourGrade(name, low, high))
                            }, leadingIcon = { ColourSwatch(name) })
                        }
                    }
                }
            }
        }
        val chart = model.gymColourChart(gymID)
        if (model.hasOwnColourScale(gymID) && chart.isNotEmpty()) {
            section(footer = "You've changed these from the gym's chart in the app's data.") {
                RowItem(onClick = { model.resetColourScale(gymID) }, divider = false, chevron = false) {
                    Text("Use the gym's chart", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun GradeChooser(label: String, grade: Int?, onChange: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val grades = Discipline.BOULDER.grades
    Box {
        TextButton(onClick = { open = true }) { Text("$label ${grade?.let { grades[it] } ?: "none"}") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("None") }, { open = false; onChange(null) })
            grades.forEachIndexed { i, g -> DropdownMenuItem({ Text(g) }, { open = false; onChange(i) }) }
        }
    }
}

/** On a gym's visits page: sends there by hold colour, and a way to set the colours up. */
fun LazyListScope.colourSendsSection(gymID: String, sends: List<Climb>, scale: List<ColourGrade>, onEdit: () -> Unit) {
    val coloured = sends.filter { it.colour != null }
    val order = scale.map { it.name }
    val names = coloured.mapNotNull { it.colour }.distinct()
        .sortedWith(compareBy({ order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
    section(
        if (names.isEmpty()) "Colour grades" else "Sends by colour",
        footer = if (scale.isEmpty() && names.isEmpty()) "If this gym grades by hold colour, add its colours to log climbs by colour." else null,
        key = "colours-$gymID",
    ) {
        names.forEachIndexed { i, name ->
            val cs = coloured.filter { it.colour == name }
            val flashes = cs.count { it.result == ClimbResult.FLASH }
            RowItem(divider = i > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColourSwatch(name)
                    Text(name)
                    scale.firstOrNull { it.name == name }?.range?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                    Spacer(Modifier.weight(1f))
                    if (flashes > 0) IconText(Icons.Filled.Bolt, "$flashes", MaterialTheme.typography.bodyMedium, Amber)
                    IconText(Icons.Filled.Check, "${cs.size - flashes}", MaterialTheme.typography.bodyMedium, Brand)
                }
            }
        }
        ActionRow(Icons.Filled.Palette, if (scale.isEmpty()) "Set up colour grades" else "Edit colour grades",
            divider = names.isNotEmpty(), onClick = onEdit)
    }
}

/** A colour dot (for colour climbs) and the grade that counts: "● V1–V3", "● V2 felt" or "V4". */
@Composable
fun ClimbGradeLabel(climb: Climb) {
    val spoken = listOfNotNull(climb.colour, climb.listGrade, if (climb.listGradeIsFelt) "felt" else null)
        .filter { it.isNotEmpty() }.joinToString(" ")
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        climb.colour?.let { ColourSwatch(it, 14.dp) }
        if (climb.listGrade.isNotEmpty()) Text(climb.listGrade, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (climb.listGradeIsFelt) Text("felt", style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}

/** The colour name, swatch and range of a colour climb. */
@Composable
fun ColourLabel(climb: Climb, detail: Boolean = true) {
    val colour = climb.colour ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ColourSwatch(colour)
        Text(colour, fontWeight = FontWeight.SemiBold)
        if (detail) climb.colourDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText) }
    }
}


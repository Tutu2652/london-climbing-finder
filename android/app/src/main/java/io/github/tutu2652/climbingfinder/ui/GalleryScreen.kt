package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tutu2652.climbingfinder.core.ClimbMedia
import io.github.tutu2652.climbingfinder.core.ClimbResult
import io.github.tutu2652.climbingfinder.core.London
import io.github.tutu2652.climbingfinder.core.MediaKind
import io.github.tutu2652.climbingfinder.core.linkedMedia
import io.github.tutu2652.climbingfinder.core.mediaSummary
import io.github.tutu2652.climbingfinder.core.shortName
import java.time.Instant

// Highlights: every route photo and video linked to a logged climb, as a strip at the
// top of the logbook and a full grid. Tapping one opens that climb's own screen, where a
// missing original can be relinked; tapping a video also plays it full screen, muted, over
// that screen. Matches the iPhone app's ClimbGalleryView.

/** "Sat 12 Oct" this year, "12 Oct 2025" before. */
fun mediaDate(t: Instant): String =
    if (t.atZone(London).year == Instant.now().atZone(London).year) dayFormat.format(t) else fullDateFormat.format(t)

private fun climbRoute(item: ClimbMedia) = "climb/${item.session.id}/${item.climb.id}?play=${item.kind == MediaKind.VIDEO}"

/** The ten newest photos and videos, with See all. Nothing at all when no climb has media. */
fun LazyListScope.highlights(items: List<ClimbMedia>, seeAll: () -> Unit) {
    if (items.isEmpty()) return
    item(key = "highlights") {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(Modifier.padding(start = 28.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Highlights", style = MaterialTheme.typography.labelLarge, color = secondaryText, modifier = Modifier.weight(1f))
                TextButton(onClick = seeAll) { Text("See all") }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            ) {
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.key }) { ClimbMediaCell(it, Modifier.width(112.dp)) }
                }
            }
        }
    }
}

private enum class KindFilter(val title: String, val kind: MediaKind?) {
    ALL("All", null), PHOTOS("Photos", MediaKind.PHOTO), VIDEOS("Videos", MediaKind.VIDEO)
}

/** Every linked photo and video in a grid, filtered by kind, gym and sends. */
@Composable
fun GalleryScreen() {
    val model = LocalModel.current
    val ctx = LocalContext.current.applicationContext
    val all = linkedMedia(model.logbook.sessions)
    var kind by rememberSaveable { mutableStateOf(KindFilter.ALL) }
    var gymID by rememberSaveable { mutableStateOf<String?>(null) }
    var sendsOnly by rememberSaveable { mutableStateOf(false) }
    var gymMenu by remember { mutableStateOf(false) }
    val shown = all.filter { item ->
        (kind.kind == null || item.kind == kind.kind) &&
            (gymID == null || item.session.gymID == gymID) &&
            (!sendsOnly || item.climb.result.isSend)
    }
    val gyms = all.map { it.session.gymID }.distinct().mapNotNull { model.gym(it) }.sortedBy { it.name }
    val resumed = rememberResumeCount()
    // Check every link, so a missing original is noted even before you scroll to it.
    LaunchedEffect(all.map { it.uri }, resumed, MediaAccess.revision) { MediaThumbnails.check(ctx, all) }
    // Only mention problems you can see with the current filters.
    val problems = shown.mapNotNull { MediaThumbnails.problems[it.uri] }.toSet()
    // Links restored after a reinstall (or from another phone) that access to the gallery would find again.
    val findable = shown.filter { MediaThumbnails.problems[it.uri] == MediaProblem.NO_ACCESS && it.file != null }
    val photoLevel = rememberAccessLevel(MediaKind.PHOTO)
    val videoLevel = rememberAccessLevel(MediaKind.VIDEO)
    val level = findable.map { if (it.kind == MediaKind.PHOTO) photoLevel else videoLevel }.minByOrNull { -it.ordinal }
    val request = rememberAccessRequest(level ?: MediaAccess.Level.NONE)

    Screen("Highlights", body = { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp),
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "kind") {
                Segmented(KindFilter.entries, kind, { it.title }) { kind = it }
            }
            if (problems.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "missing") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(when {
                        level == MediaAccess.Level.PARTIAL -> "Some items aren't in the photos and videos you've allowed this app to see."
                        level == MediaAccess.Level.NONE -> "Links restored from a backup need access to your photos and videos to open again. " +
                            "Or tap one to link it again."
                        MediaProblem.NO_ACCESS in problems -> "Android needs you to link some items again: links can't be opened after " +
                            "reinstalling the app, or from another phone's backup. Tap one to relink or remove it."
                        else -> "Some originals are missing from your gallery. Tap one to relink or remove it."
                    }, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    if (level != null && level != MediaAccess.Level.FULL) {
                        TextButton(onClick = request.run, contentPadding = PaddingValues(0.dp)) {
                            Text(request.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        FilterChip(gymID?.let { model.gym(it)?.shortName ?: "Unknown gym" } ?: "All gyms", gymID != null,
                            Icons.Filled.Apartment, trailing = Icons.Filled.KeyboardArrowDown) { gymMenu = true }
                        DropdownMenu(gymMenu, { gymMenu = false }) {
                            DropdownMenuItem({ Text("All gyms") }, { gymID = null; gymMenu = false },
                                leadingIcon = { if (gymID == null) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) })
                            for (g in gyms) {
                                DropdownMenuItem({ Text(g.name) }, { gymID = g.id; gymMenu = false },
                                    leadingIcon = { if (gymID == g.id) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) })
                            }
                        }
                    }
                    // Flashes count as sends; attempts are hidden.
                    FilterChip("Sends only", sendsOnly, if (sendsOnly) Icons.Filled.CheckCircle else Icons.Outlined.Circle) { sendsOnly = !sendsOnly }
                }
            }
            items(shown, key = { it.key }) { ClimbMediaCell(it) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "summary") {
                if (shown.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = secondaryText, modifier = Modifier.size(40.dp))
                        Text(if (all.isEmpty()) "No photos or videos yet" else "Nothing matches", style = MaterialTheme.typography.titleMedium)
                        Text(if (all.isEmpty()) "Open a climb in a session and link a route photo or video from your gallery."
                        else "Try showing all gyms, or attempts too.",
                            style = MaterialTheme.typography.bodyMedium, color = secondaryText, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(mediaSummary(shown), style = MaterialTheme.typography.bodySmall, color = secondaryText,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    })
}

/** A square thumbnail with the climb under it: colour and grade, outcome, gym and date. Opens the climb. */
@Composable
fun ClimbMediaCell(item: ClimbMedia, modifier: Modifier = Modifier) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val climb = item.climb
    val gym = model.gym(item.session.gymID)
    val state by rememberThumbnail(item.uri, item.kind, item.file)
    val missing = state is MediaState.Failed
    val spoken = listOfNotNull(
        if (item.kind == MediaKind.PHOTO) "Route photo" else "Video",
        if (missing) "unavailable" else null,
        listOfNotNull(climb.colour, climb.listGrade).filter { it.isNotEmpty() }.joinToString(" "),
        climb.result.title, gym?.shortName, fullDateFormat.format(climb.loggedAt),
    ).filter { it.isNotEmpty() }.joinToString(", ")
    val open = { nav.navigate(climbRoute(item)) }
    Column(
        modifier.clickable(onClick = open).clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
            onClick(if (item.kind == MediaKind.VIDEO) "Plays the video" else "Opens the climb") { open(); true }
        },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            when (val s = state) {
                is MediaState.Ready -> Image(s.thumb.bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                is MediaState.Failed -> Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    val noAccess = s.problem == MediaProblem.NO_ACCESS
                    Icon(if (noAccess) Icons.Filled.Lock else Icons.Filled.ReportProblem, null, tint = secondaryText, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(if (noAccess) (if (item.file != null) "No access" else "Link again") else if (item.kind == MediaKind.PHOTO) "Photo missing" else "Video missing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold, color = secondaryText, textAlign = TextAlign.Center)
                }
                MediaState.Loading -> {}
            }
            if (item.kind == MediaKind.VIDEO) VideoBadge((state as? MediaState.Ready)?.thumb?.durationMs, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(0.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { ClimbGradeLabel(climb) } }
            Icon(climb.result.icon, null, modifier = Modifier.size(15.dp),
                tint = if (climb.result == ClimbResult.FLASH) Amber else if (climb.result == ClimbResult.SEND) Brand else secondaryText)
        }
        Text(gym?.shortName ?: "Unknown gym", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Text(mediaDate(climb.loggedAt), style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1)
    }
}

/** A capsule filter: white when off, brand green with white text when on. */
@Composable
private fun FilterChip(label: String, on: Boolean, icon: ImageVector, trailing: ImageVector? = null, onClick: () -> Unit) {
    val content = if (on) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(CircleShape).background(if (on) Brand else MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick).semantics { selected = on }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = if (on) Color.White else Brand, modifier = Modifier.size(16.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailing != null) Icon(trailing, null, tint = content, modifier = Modifier.size(16.dp))
    }
}

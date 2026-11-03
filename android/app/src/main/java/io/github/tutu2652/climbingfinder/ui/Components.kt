package io.github.tutu2652.climbingfinder.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.tutu2652.climbingfinder.AppModel
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.OpenState
import io.github.tutu2652.climbingfinder.core.PriceKind
import io.github.tutu2652.climbingfinder.core.shortName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

val Brand = Color(0xFF5DB07A)
val Amber = Color(0xFFE2A13C)
val PeakPurple = Color(0xFFA77BC0)
val Warn = Color(0xFFC96A5A)
val Pink = Color(0xFFE85A8C)

val PriceKind.color: Color
    get() = when (this) {
        PriceKind.OFF -> Brand
        PriceKind.MAGIC -> Amber
        PriceKind.PEAK, PriceKind.NONE -> PeakPurple
    }

val LocalModel = compositionLocalOf<AppModel> { error("no model") }
val LocalNav = compositionLocalOf<NavController> { error("no nav") }

@Composable
fun ClimbingTheme(dark: Boolean, content: @Composable () -> Unit) {
    // Selected segments and the current tab use a soft green, not Material's default lilac.
    val colors = if (dark) darkColorScheme(
        primary = Brand, secondary = Brand, tertiary = PeakPurple,
        secondaryContainer = Color(0xFF2C5238), onSecondaryContainer = Color(0xFFD6F0DF),
    )
    else lightColorScheme(
        primary = Color(0xFF3F8F5C), secondary = Color(0xFF3F8F5C), tertiary = PeakPurple,
        secondaryContainer = Color(0xFFD3EEDC), onSecondaryContainer = Color(0xFF14361F),
        surfaceContainer = Color(0xFFF4F6F4), surfaceContainerLow = Color(0xFFF4F6F4), background = Color(0xFFEEF1EE), surface = Color(0xFFEEF1EE),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

val secondaryText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Coloured capsule: green off-peak, amber super off-peak / deal, purple peak. */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun OpenBadge(state: OpenState?) {
    if (state != null) Pill(state.text, if (state.open) Brand else secondaryText)
}

@Composable
fun VerifyFlag(gym: Gym) {
    if (!gym.verified) Text("verify", style = MaterialTheme.typography.labelSmall, color = Warn, fontWeight = FontWeight.SemiBold)
}

/** Small filled heart next to a favourite gym's name. */
@Composable
fun FavouriteMark(gymID: String) {
    if (LocalModel.current.isFavourite(gymID)) {
        Icon(Icons.Filled.Favorite, "Favourite", tint = Pink, modifier = Modifier.size(14.dp))
    }
}

fun starString(r: Int) = "★".repeat(r) + "☆".repeat(5 - r)

@Composable
fun Stars(rating: Int) {
    Text(starString(rating), color = Amber, style = MaterialTheme.typography.bodySmall)
}

/** Personal note + rating summary shown under a gym's name in lists. */
@Composable
fun NoteSummary(gymID: String) {
    val model = LocalModel.current
    model.ratings[gymID]?.let { Stars(it) }
    model.notes[gymID]?.let {
        Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = Brand, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val markdownLink = Regex("""\[([^\]]+)\]\((https?://[^)\s]+)\)""")

/** Renders the data's `[label](https://url)` snippets as tappable links. */
@Composable
fun LinkedText(source: String, style: TextStyle = MaterialTheme.typography.bodyMedium, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(source, linkColor) {
        buildAnnotatedString {
            var i = 0
            for (m in markdownLink.findAll(source)) {
                append(source.substring(i, m.range.first))
                withLink(LinkAnnotation.Url(m.groupValues[2], TextLinkStyles(SpanStyle(color = linkColor)))) {
                    append(m.groupValues[1])
                }
                i = m.range.last + 1
            }
            append(source.substring(i))
        }
    }
    Text(text, style = style, modifier = modifier)
}

/** Footer-style explanation text under a section. */
@Composable
fun Explainer(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = secondaryText,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
}

/** A grouped block, like a section in an iOS list: optional header, rounded card, optional footer. */
fun LazyListScope.section(
    header: String? = null,
    footer: String? = null,
    key: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        Column(Modifier.fillMaxWidth().padding(top = if (header == null) 12.dp else 18.dp)) {
            if (header != null) {
                Text(header.uppercase(), style = MaterialTheme.typography.labelMedium, color = secondaryText,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 6.dp))
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp), content = content)
            }
            if (footer != null) Explainer(footer)
        }
    }
}

/** One row inside a section: padded, optionally tappable, with a divider above when not first. */
@Composable
fun RowItem(
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    chevron: Boolean = onClick != null,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable () -> Unit,
) {
    Column {
        if (divider) HorizontalDivider(Modifier.padding(start = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { content() }
            if (chevron) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = secondaryText.copy(alpha = 0.6f))
            }
        }
    }
}

/** "Label ........ value" row. */
@Composable
fun LabeledRow(label: String, value: String, divider: Boolean = true, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    RowItem(onClick = onClick, divider = divider) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = if (bold) FontWeight.SemiBold else null, modifier = Modifier.weight(1f))
            Text(value, color = secondaryText, fontWeight = if (bold) FontWeight.SemiBold else null)
        }
    }
}

/** Icon and text in one tappable row, for navigation and actions. */
@Composable
fun ActionRow(icon: ImageVector, title: String, divider: Boolean = true, value: String? = null, tint: Color = MaterialTheme.colorScheme.primary,
              chevron: Boolean = true, onClick: () -> Unit) {
    RowItem(onClick = onClick, divider = divider, chevron = chevron) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = secondaryText)
        }
    }
}

/** Icon and text sitting close together, for inline metadata like times and travel. */
@Composable
fun IconText(icon: ImageVector, text: String, style: TextStyle = MaterialTheme.typography.bodySmall, color: Color = secondaryText) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size((style.fontSize.value + 2).dp))
        Text(text, style = style, color = color)
    }
}

/** A segmented control for a handful of choices. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                label = { Text(label(o), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp) },
            )
        }
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

fun parseHex(hex: String): Color = runCatching {
    Color(0xFF000000 or hex.trimStart('#').toLong(16))
}.getOrDefault(Color.White)

/**
 * The gym's official logo (in gyms.json), built into the app from assets/logos or
 * downloaded once and cached. Gyms without one get a lettered badge.
 */
@Composable
fun GymLogo(gym: Gym, size: Dp = 40.dp) {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val logo = model.logo(gym)
    var image by remember(logo?.first) { mutableStateOf(logo?.first?.let { LogoCache.memory[it] }) }
    LaunchedEffect(logo?.first) {
        if (image == null && logo != null) image = LogoCache.load(ctx, logo.first)?.asImageBitmap()?.also { LogoCache.memory[logo.first] = it }
    }
    val shape = RoundedCornerShape(size * 0.22f)
    val bg = if (image != null) logo?.second?.let { parseHex(it) } ?: Color.White else Brand
    Box(
        Modifier.size(size).clip(shape).background(bg).border(0.5.dp, Color.Black.copy(alpha = 0.08f), shape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, null, modifier = Modifier.padding(size * 0.08f).fillMaxWidth())
        } else {
            val initials = gym.shortName.split(" ").filter { it.firstOrNull()?.isLetter() == true && it != "The" }
                .take(2).joinToString("") { it.first().toString() }.uppercase()
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

object LogoCache {
    val memory = mutableMapOf<String, ImageBitmap>()

    /** "assets/logos/x.png" is built in; anything else is downloaded once into the cache folder. */
    suspend fun load(ctx: Context, path: String): Bitmap? = withContext(Dispatchers.IO) {
        if (path.startsWith("assets/logos/")) {
            runCatching { ctx.assets.open("logos/" + path.substringAfterLast('/')).use { BitmapFactory.decodeStream(it) } }
                .getOrNull()?.let { return@withContext it }
        }
        val url = if (path.startsWith("http")) path else AppModel.SITE + "/" + path
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(File(ctx.cacheDir, "logos").apply { mkdirs() }, name)
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return@withContext it }
        runCatching {
            val bytes = URL(url).openStream().use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { file.writeBytes(bytes) }
        }.getOrNull()
    }
}

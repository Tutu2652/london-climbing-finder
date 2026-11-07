package io.github.tutu2652.climbingfinder.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.tutu2652.climbingfinder.LocationService
import io.github.tutu2652.climbingfinder.Origin
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.formatPrice
import io.github.tutu2652.climbingfinder.core.openState
import io.github.tutu2652.climbingfinder.core.shortName
import android.view.MotionEvent
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Locale

@Composable
fun MapScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var located by remember { mutableStateOf(LocationService.isAuthorized(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        located = LocationService.isAuthorized(ctx)
    }
    // Asks once for location access (used for the blue dot).
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("climbing", Context.MODE_PRIVATE)
        if (!located && !prefs.getBoolean("askedLocation", false)) {
            prefs.edit().putBoolean("askedLocation", true).apply()
            ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val gyms = model.data.gyms.filter { model.matchesFilters(it) }
    // Pricing every gym takes a moment, so only redo it when prices, filters or the time
    // change, not each time a tap changes `selected`.
    val best by remember { derivedStateOf { model.cheapest().firstOrNull()?.gym?.id } }
    val origin = model.origin
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
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(51.515, -0.12))
            overlays.add(CopyrightOverlay(ctx))
        }
    }
    val myLocation = remember(located) {
        if (located) MyLocationNewOverlay(GpsMyLocationProvider(ctx), map) else null
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, myLocation) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { map.onResume(); myLocation?.enableMyLocation() }
            if (e == Lifecycle.Event.ON_PAUSE) { map.onPause(); myLocation?.disableMyLocation() }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        myLocation?.enableMyLocation()
        onDispose { lifecycle.removeObserver(obs); map.onPause(); myLocation?.disableMyLocation() }
    }

    Screen("Map", back = false, actions = {
        FilterButton()
        IconButton(onClick = { nav.navigate("nearby") }) { Icon(Icons.AutoMirrored.Filled.List, "Nearest gyms") }
    }, body = { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView({ map }, Modifier.fillMaxSize()) { m ->
                m.overlays.removeAll { it is Marker || it is MyLocationNewOverlay }
                if (myLocation != null) m.overlays.add(myLocation)
                if (origin != null && origin.label != Origin.MY_LOCATION) {
                    m.overlays.add(Marker(m).apply {
                        position = GeoPoint(origin.lat, origin.lng)
                        title = origin.label
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    })
                }
                // The selected and cheapest gyms go on top.
                for (g in gyms.sortedBy { (if (it.id == selected) 2 else 0) + (if (it.id == best) 1 else 0) }) {
                    m.overlays.add(QuickTapMarker(m) { selected = g.id }.apply {
                        position = GeoPoint(g.lat, g.lng)
                        icon = BitmapDrawable(ctx.resources, markerBitmap(ctx, model.logo(g), g, g.id == best, g.id == selected))
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = g.shortName
                    })
                }
                m.invalidate()
            }
            selected?.let { model.gym(it) }?.let { g ->
                MapGymCard(g, Modifier.align(Alignment.BottomCenter).padding(12.dp)) { selected = null }
            }
        }
    })
}

/** Card shown over the map for the tapped marker. */
@Composable
private fun MapGymCard(gym: Gym, modifier: Modifier, close: () -> Unit) {
    val model = LocalModel.current
    val nav = LocalNav.current
    val best = model.pricer.bestOnDay(gym, model.dayMoment, model.concession)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GymLogo(gym, 36.dp)
                Text(gym.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FavouriteMark(gym.id)
                IconButton(onClick = close) { Icon(Icons.Filled.Close, "Close", tint = secondaryText) }
            }
            if (best != null) {
                Text(model.climberLabel + " · cheapest today", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatPrice(best.price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Pill(best.label, best.kind.color)
                    Text(best.`when`, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OpenBadge(gym.openState(model.moment))
                model.travelText(gym)?.let { IconText(Icons.Filled.Train, it) }
                model.distanceKm(gym)?.let { Text(String.format(Locale.UK, "%.1f km", it), style = MaterialTheme.typography.bodySmall, color = secondaryText) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("gym/${gym.id}") }) { Text("Details") }
                DirectionsButton(gym)
            }
        }
    }
}

private val markerCache = mutableMapOf<String, Bitmap>()

/**
 * A marker that reacts on the first tap. osmdroid's own Marker only reports a tap once a
 * double-tap has been ruled out (about a third of a second), so the gym card appeared late.
 */
private class QuickTapMarker(map: MapView, private val onTap: () -> Unit) : Marker(map) {
    override fun onSingleTapUp(e: MotionEvent, mapView: MapView): Boolean {
        if (!hitTest(e, mapView)) return false
        onTap()
        return true
    }
    // Already handled on touch-up; just stop the tap reaching the map or opening an info window.
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean = hitTest(e, mapView)
}

/** A rounded tile with the gym's logo (or initials), green-edged for the cheapest gym. */
private fun markerBitmap(ctx: Context, logo: Pair<String, String?>?, gym: Gym, best: Boolean, selected: Boolean): Bitmap {
    val key = "${gym.id}|$best|$selected|${logo?.first}"
    markerCache[key]?.let { return it }
    val d = ctx.resources.displayMetrics.density
    val size = (if (selected) 44 else 32) * d
    val bmp = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val outer = RectF(0f, 0f, size, size)
    paint.color = if (best) android.graphics.Color.parseColor("#5DB07A") else android.graphics.Color.WHITE
    c.drawRoundRect(outer, size * 0.25f, size * 0.25f, paint)
    val inner = RectF(2.5f * d, 2.5f * d, size - 2.5f * d, size - 2.5f * d)
    val image = logo?.first?.takeIf { it.startsWith("assets/logos/") }?.let { path ->
        runCatching { ctx.assets.open("logos/" + path.substringAfterLast('/')).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    paint.color = when {
        image != null -> logo.second?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() } ?: android.graphics.Color.WHITE
        else -> android.graphics.Color.parseColor("#3F8F5C")
    }
    c.drawRoundRect(inner, size * 0.2f, size * 0.2f, paint)
    if (image != null) {
        val pad = inner.width() * 0.08f
        val box = RectF(inner.left + pad, inner.top + pad, inner.right - pad, inner.bottom - pad)
        val scale = minOf(box.width() / image.width, box.height() / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
        c.drawBitmap(image, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    } else {
        val initials = gym.shortName.split(" ").filter { it.firstOrNull()?.isLetter() == true && it != "The" }
            .take(2).joinToString("") { it.first().toString() }.uppercase()
        paint.color = android.graphics.Color.WHITE
        paint.textSize = inner.height() * 0.4f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        c.drawText(initials, inner.centerX(), inner.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    }
    return bmp.also { markerCache[key] = it }
}

/** Gyms sorted by distance from your start location. */
@Composable
fun NearbyScreen() {
    val model = LocalModel.current
    val nav = LocalNav.current
    val gyms = model.data.gyms.filter { model.matchesFilters(it) }
        .sortedWith(compareBy({ model.distanceKm(it) ?: Double.MAX_VALUE }, { it.name }))
    Screen("Nearest gyms") {
        section { OriginPicker() }
        section(footer = "Travel = public-transport journey time from TfL's Journey Planner; distance is as the crow flies.") {
            gyms.forEachIndexed { i, g ->
                RowItem(onClick = { nav.navigate("gym/${g.id}") }, divider = i > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name)
                            Text(g.area ?: "", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            model.travelText(g)?.let { Text(it) }
                            model.distanceKm(g)?.let { Text(String.format(Locale.UK, "%.1f km", it), style = MaterialTheme.typography.bodySmall, color = secondaryText) }
                        }
                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

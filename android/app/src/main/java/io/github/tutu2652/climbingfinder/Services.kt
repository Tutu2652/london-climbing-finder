package io.github.tutu2652.climbingfinder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.London
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.coroutines.resume

/** A plain GET; null on any failure or non-200 answer. */
suspend fun httpGet(url: String, timeoutMs: Int = 15_000): String? = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.useCaches = false
        c.setRequestProperty("User-Agent", "LondonClimbingFinder-Android/1.0")
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

/** TfL Journey Planner (free, no key): the same requests the website and iPhone app make. */
object TravelService {
    suspend fun journeyMinutes(fromLat: Double, fromLng: Double, gyms: List<Gym>): Map<String, Int> = coroutineScope {
        val from = String.format(Locale.ROOT, "%.5f,%.5f", fromLat, fromLng)
        val depart = departParams()
        val gate = Semaphore(4)   // at most 4 requests in flight
        fun path(to: String) = "https://api.tfl.gov.uk/Journey/JourneyResults/${enc(from)}/to/${enc(to)}" +
            if (depart.isEmpty()) "" else "?$depart"
        gyms.map { g ->
            async {
                gate.withPermit {
                    // Postcode first; TfL answers "ambiguous" for some (e.g. SE1 9BL), so fall back to the pin.
                    val mins = g.postcode?.let { duration(path(it)) } ?: duration(path("${g.lat},${g.lng}"))
                    mins?.let { g.id to it }
                }
            }
        }.mapNotNull { it.await() }.toMap()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%2C", ",")

    private suspend fun duration(url: String): Int? {
        val body = httpGet(url) ?: return null
        return runCatching {
            Json.parseToJsonElement(body).jsonObject["journeys"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("duration")?.jsonPrimitive?.int
        }.getOrNull()
    }

    /**
     * Real-time departure between 08:00 and 23:00; otherwise a stable weekday
     * 10:00 departure so overnight numbers stay sensible.
     */
    private fun departParams(): String {
        val now = ZonedDateTime.now(London)
        if (now.hour in 8..22) return ""
        var d = now.withHour(10).withMinute(0)
        if (now.hour >= 23) d = d.plusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
        return String.format(Locale.ROOT, "date=%04d%02d%02d&time=1000&timeIs=Departing", d.year, d.monthValue, d.dayOfMonth)
    }
}

/** One-shot "use my location", plus postcode / place lookup. */
object LocationService {
    class Failure(message: String) : Exception(message)

    fun isAuthorized(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The current position, or null if location is off or nothing arrives in 15 seconds. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(ctx: Context): Location? {
        if (!isAuthorized(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
        }
        for (p in providers.sortedBy { if (it == LocationManager.NETWORK_PROVIDER) 0 else 1 }) {
            val loc = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    val cancel = androidx.core.os.CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    LocationManagerCompat.getCurrentLocation(lm, p, cancel, ContextCompat.getMainExecutor(ctx)) {
                        if (cont.isActive) cont.resume(it)
                    }
                }
            }
            if (loc != null) return loc
        }
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    private val postcode = Regex("^[A-Z]{1,2}[0-9][A-Z0-9]?\\s*[0-9][A-Z]{2}$", RegexOption.IGNORE_CASE)

    /**
     * UK postcodes via postcodes.io (free, exact); anything else via the phone's geocoder, in
     * London unless `inLondon` is false (a gym you're adding may be anywhere).
     */
    suspend fun geocode(ctx: Context, query: String, inLondon: Boolean = true): Origin {
        val q = query.trim()
        if (postcode.matches(q)) {
            val body = httpGet("https://api.postcodes.io/postcodes/" + URLEncoder.encode(q, "UTF-8").replace("+", "%20"))
            val r = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject["result"]?.jsonObject }.getOrNull() }
            if (r != null) {
                return Origin(q.uppercase(), r.getValue("latitude").jsonPrimitive.double, r.getValue("longitude").jsonPrimitive.double)
            }
        }
        val hit = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(ctx, Locale.UK).getFromLocationName(if (inLondon) "$q, London, UK" else q, 1)?.firstOrNull()
            }.getOrNull()
        } ?: throw Failure("Couldn't find that place. Try a postcode, e.g. WC2N 5DU.")
        return Origin(q, hit.latitude, hit.longitude)
    }

    /** What's at a point, to fill in a gym you're adding. Fields the phone can't tell are null. */
    data class Place(val area: String?, val address: String?, val postcode: String?, val country: String?, val timeZone: String?)

    /** The phone's geocoder, for a gym anywhere in the world; null offline or when nothing is found. */
    suspend fun describe(ctx: Context, lat: Double, lng: Double): Place? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()?.let { a ->
            Place(
                area = a.locality ?: a.subAdminArea ?: a.adminArea,
                address = listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").ifEmpty { null },
                postcode = a.postalCode,
                country = a.countryName,
                timeZone = timeZoneFor(a.countryCode, lng),
            )
        }
    }

    /**
     * The country's time zone, e.g. "Europe/Paris". Where a country has several (the US, Australia),
     * the one whose offset best matches the longitude: right for most places, not for all.
     */
    fun timeZoneFor(countryCode: String?, lng: Double): String? {
        val code = countryCode?.uppercase(Locale.ROOT) ?: return null
        val ids = android.icu.util.TimeZone.getAvailableIDs(code)
            .filter { runCatching { java.time.ZoneId.of(it) }.isSuccess }
        val now = System.currentTimeMillis()
        return ids.minByOrNull { Math.abs(java.util.TimeZone.getTimeZone(it).getOffset(now) - lng * 240_000) }
    }
}

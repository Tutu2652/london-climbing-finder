package io.github.tutu2652.climbingfinder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tutu2652.climbingfinder.core.ClimbingData
import io.github.tutu2652.climbingfinder.core.ColourGrade
import io.github.tutu2652.climbingfinder.core.DealOption
import io.github.tutu2652.climbingfinder.core.Gym
import io.github.tutu2652.climbingfinder.core.Moment
import io.github.tutu2652.climbingfinder.core.PriceKind
import io.github.tutu2652.climbingfinder.core.Pricer
import io.github.tutu2652.climbingfinder.core.haversineKm
import io.github.tutu2652.climbingfinder.core.matchesAC
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale

enum class CheapestMode(val title: String) { DAY("Best price all day"), TIME("At selected time") }

/** Where travel times and distances are measured from. */
data class Origin(val label: String, val lat: Double, val lng: Double) {
    companion object { const val MY_LOCATION = "My location" }
}

/** Which copy of gyms.json the app is showing. */
sealed interface DataSource {
    data object Bundled : DataSource
    data class Cached(val at: Instant) : DataSource
    data class Website(val at: Instant) : DataSource
}

class AppModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("climbing", Context.MODE_PRIVATE)
    private val cacheFile = File(app.filesDir, "gyms.json")

    var data: ClimbingData by mutableStateOf(ClimbingData(null, emptyList(), emptyList(), emptyList())); private set
    var dataSource: DataSource by mutableStateOf(DataSource.Bundled); private set
    var pricer by mutableStateOf(Pricer()); private set
    private var lastDataCheck: Instant? = null
    /** Logos from the copy built into the app, used when a downloaded copy lacks them. */
    private val bundledLogos: Map<String, Pair<String, String?>>
    /** Colour charts from the copy built into the app, used when a downloaded copy lacks them. */
    private val bundledColourGrades: Map<String, List<ColourGrade>>

    val logbook = LogbookStore(File(app.filesDir, "logbook.json"), viewModelScope)

    // Filters shared by the Cheapest, Map and Gyms tabs.
    /** The date and time being priced. Follows the clock unless you pick one yourself. */
    var whenAt: Instant by mutableStateOf(Instant.now()); private set
    var whenPinned by mutableStateOf(false); private set
    var mode by mutableStateOf(CheapestMode.DAY)
    var concession by mutableStateOf(prefs.getBoolean("concession", false)); private set
    var typeFilter by mutableStateOf("")      // "" | "bouldering" | "ropes"
    var zoneFilter by mutableStateOf<Int?>(null)
    var acFilter by mutableStateOf("")        // "" | "ac" | "full"
    var favouritesOnly by mutableStateOf(prefs.getBoolean("favouritesOnly", false)); private set

    // Personal favourites, notes & 1-5 ratings, kept on this phone.
    var favourites: Set<String> by mutableStateOf(prefs.getStringSet("favourites", emptySet())!!.toSet()); private set
    var notes: Map<String, String> by mutableStateOf(loadMap("gymNotes") { it.jsonPrimitive.content }); private set
    var ratings: Map<String, Int> by mutableStateOf(loadMap("gymRatings") { it.jsonPrimitive.int }); private set

    var origin: Origin? by mutableStateOf(loadOrigin()); private set
    var travel: Map<String, Int> by mutableStateOf(emptyMap()); private set   // gym id -> minutes by public transport
    var travelLoading by mutableStateOf(false); private set
    private var travelFor: Origin? = null
    private var travelFetchedAt: Instant? = null

    init {
        val bundled = ClimbingData.parse(app.assets.open("gyms.json").bufferedReader().use { it.readText() })
        bundledLogos = bundled.gyms.mapNotNull { g -> g.logo?.let { g.id to (it to g.logoBackground) } }.toMap()
        bundledColourGrades = bundled.gyms.filter { it.colourGrades.isNotEmpty() }.associate { it.id to it.colourGrades }
        // Start from the last website download unless this build carries newer research.
        val cached = runCatching { ClimbingData.parse(cacheFile.readText()) }.getOrNull()
        if (cached != null && cached.gyms.isNotEmpty() && (cached.lastResearchDate ?: "") >= (bundled.lastResearchDate ?: "")) {
            data = cached
            dataSource = DataSource.Cached(Instant.ofEpochMilli(cacheFile.lastModified()))
        } else {
            data = bundled
        }
        var bh = prefs.getStringSet("bankHolidays", emptySet())!!.toSet()
        if (bh.isEmpty()) bh = parseBankHolidays(app.assets.open("bank_holidays.json").bufferedReader().use { it.readText() })
        pricer = Pricer(bh)
    }

    val moment: Moment get() = Moment.of(whenAt)
    val dayMoment: Moment get() = moment.at(0)
    val isBankHoliday: Boolean get() = pricer.isBankHoliday(moment)
    val climberLabel: String get() = if (concession) "Student / concession" else "Adult"

    fun chooseConcession(on: Boolean) { concession = on; prefs.edit().putBoolean("concession", on).apply() }
    fun chooseFavouritesOnly(on: Boolean) { favouritesOnly = on; prefs.edit().putBoolean("favouritesOnly", on).apply() }

    val filtersActive: Boolean get() = typeFilter.isNotEmpty() || zoneFilter != null || acFilter.isNotEmpty() || favouritesOnly
    val zones: List<Int> get() = data.gyms.map { it.zone }.toSortedSet().toList()

    fun matchesFilters(g: Gym): Boolean {
        if (favouritesOnly && g.id !in favourites) return false
        if (typeFilter.isNotEmpty() && !g.type.contains(typeFilter)) return false
        if (zoneFilter != null && g.zone != zoneFilter) return false
        return g.matchesAC(acFilter)
    }

    fun clearFilters() { typeFilter = ""; zoneFilter = null; acFilter = ""; chooseFavouritesOnly(false) }

    fun gym(id: String): Gym? = data.gyms.firstOrNull { it.id == id }

    fun logo(g: Gym): Pair<String, String?>? = g.logo?.let { it to g.logoBackground } ?: bundledLogos[g.id]

    /** The gym's own colour chart from gyms.json, falling back to the copy built into the app. */
    fun gymColourChart(gymID: String): List<ColourGrade> =
        gym(gymID)?.colourGrades?.takeIf { it.isNotEmpty() } ?: bundledColourGrades[gymID].orEmpty()

    /** The colours to log by at a gym: yours if you've set them, else the gym's chart. */
    fun colourScale(gymID: String): List<ColourGrade> = logbook.colourScales[gymID] ?: gymColourChart(gymID)

    /** You've changed this gym's colours from its chart (or it has no chart). */
    fun hasOwnColourScale(gymID: String): Boolean = gymID in logbook.colourScales

    fun setColourScale(gymID: String, scale: List<ColourGrade>) =
        logbook.setColourScale(gymID, scale.takeUnless { it == gymColourChart(gymID) })

    /** Go back to the gym's own chart. */
    fun resetColourScale(gymID: String) = logbook.setColourScale(gymID, null)

    // --- Date & time

    /** A date or time you picked stays put until you tap Now or the day has passed. */
    fun pickWhen(t: Instant) { whenAt = t; whenPinned = true }

    fun resetToNow() { whenAt = Instant.now(); whenPinned = false }

    /** Called every minute and whenever the app comes back to the screen. */
    fun followClock() {
        if (!whenPinned || Moment.of(whenAt).ymd < Moment.now().ymd) resetToNow()
    }

    fun becameActive() {
        followClock()
        travelFetchedAt?.let { if (Duration.between(it, Instant.now()).toMinutes() > 60) travel = emptyMap() }  // departures move on
        viewModelScope.launch {
            refreshData()
            refreshMyLocation()
            refreshTravel()
        }
    }

    // --- Cheapest ranking

    data class Ranked(
        val gym: Gym,
        val price: Double,
        val label: String,
        val kind: PriceKind,
        val `when`: String,
        val alts: List<DealOption>,
    )

    fun cheapest(): List<Ranked> {
        val rows = mutableListOf<Ranked>()
        val day = dayMoment
        val now = moment
        for (g in data.gyms) {
            if (!matchesFilters(g) || g.temporarilyClosed != null) continue
            when (mode) {
                CheapestMode.DAY -> {
                    if (g.hours.isNotEmpty() && g.hours[day.weekday] == null) continue   // shut all day, e.g. Mount Palace on Mondays
                    val b = pricer.bestOnDay(g, day, concession) ?: continue
                    val seen = mutableSetOf(key(b.price))
                    val alts = mutableListOf<DealOption>()
                    for (o in pricer.slotsOnDay(g, day, concession)) {
                        if (!seen.add(key(o.price))) continue
                        alts.add(o)
                        if (alts.size >= 2) break
                    }
                    rows.add(Ranked(g, b.price, b.label, b.kind, b.`when`, alts))
                }
                CheapestMode.TIME -> {
                    val r = pricer.applicable(g, now, concession)
                    val p = r.price ?: continue
                    rows.add(Ranked(g, p, r.label, r.kind, pricer.windowForTime(g, now, concession), emptyList()))
                }
            }
        }
        return rows.sortedBy { it.price }
    }

    private fun key(p: Double) = String.format(Locale.ROOT, "%.2f", p)

    // --- Favourites, notes & ratings

    fun isFavourite(id: String) = id in favourites

    fun toggleFavourite(id: String) {
        favourites = if (id in favourites) favourites - id else favourites + id
        prefs.edit().putStringSet("favourites", favourites).apply()
    }

    fun setNote(text: String, id: String) {
        val t = text.trim()
        notes = if (t.isEmpty()) notes - id else notes + (id to t)
        prefs.edit().putString("gymNotes", JsonObject(notes.mapValues { JsonPrimitive(it.value) }).toString()).apply()
    }

    fun setRating(r: Int, id: String) {
        ratings = if (r == 0) ratings - id else ratings + (id to r)
        prefs.edit().putString("gymRatings", JsonObject(ratings.mapValues { JsonPrimitive(it.value) }).toString()).apply()
    }

    private fun <T> loadMap(key: String, value: (kotlinx.serialization.json.JsonElement) -> T): Map<String, T> =
        runCatching { Json.parseToJsonElement(prefs.getString(key, null)!!).jsonObject.mapValues { value(it.value) } }
            .getOrDefault(emptyMap())

    // --- Live prices (gyms.json from the website, cached for offline use)

    /**
     * Downloads the deployed gyms.json; keeps the current copy if that fails.
     * Checks at most every 30 minutes unless `force` (pull to refresh).
     */
    suspend fun refreshData(force: Boolean = false) {
        val last = lastDataCheck
        if (!force && last != null && Duration.between(last, Instant.now()).toMinutes() < 30) return
        val text = httpGet(REMOTE_URL) ?: return
        val fresh = runCatching { ClimbingData.parse(text) }.getOrNull() ?: return
        if (fresh.gyms.isEmpty()) return
        lastDataCheck = Instant.now()
        // Never swap in a website copy older than the data we already have (e.g. a
        // build carrying a new gym before the site is redeployed).
        if ((fresh.lastResearchDate ?: "") < (data.lastResearchDate ?: "")) return
        data = fresh
        dataSource = DataSource.Website(Instant.now())
        runCatching {
            val tmp = File(cacheFile.parentFile, "gyms.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(cacheFile)
        }
    }

    // --- Location, distance & travel

    fun chooseOrigin(o: Origin?) {
        if (o == origin) return
        origin = o
        val e = prefs.edit()
        if (o == null) e.remove("origin") else e.putString("origin", "${o.lat}|${o.lng}|${o.label}")
        e.apply()
        travel = emptyMap(); travelFor = null
        viewModelScope.launch { refreshTravel() }
    }

    private fun loadOrigin(): Origin? = prefs.getString("origin", null)?.split("|", limit = 3)?.let { p ->
        runCatching { Origin(p[2], p[0].toDouble(), p[1].toDouble()) }.getOrNull()
    }

    fun distanceKm(g: Gym): Double? = origin?.let { haversineKm(it.lat, it.lng, g.lat, g.lng) }

    fun travelText(g: Gym): String? {
        if (origin == null) return null
        travel[g.id]?.let { return "$it min" }
        return if (travelLoading) "…" else null
    }

    /**
     * Public-transport journey time to every gym via the free TfL Journey Planner.
     * Only fetches gyms still missing a time, so a failed lookup is retried next time.
     */
    suspend fun refreshTravel() {
        val o = origin ?: return
        if (travelLoading) return
        if (travelFor != o) { travel = emptyMap(); travelFor = o }
        val missing = data.gyms.filter { it.id !in travel }
        if (missing.isEmpty()) return
        travelLoading = true
        val results = TravelService.journeyMinutes(o.lat, o.lng, missing)
        if (origin == o) {
            if (travel.isEmpty()) travelFetchedAt = Instant.now()
            travel = travel + results
        }
        travelLoading = false
        if (origin != o) refreshTravel()   // location changed while we were fetching
    }

    /** Keeps "My location" current when you've moved, without asking for permission again. */
    suspend fun refreshMyLocation() {
        val o = origin ?: return
        if (o.label != Origin.MY_LOCATION || !LocationService.isAuthorized(ctx)) return
        val l = LocationService.currentLocation(ctx) ?: return
        if (haversineKm(o.lat, o.lng, l.latitude, l.longitude) > 0.3) chooseOrigin(Origin(Origin.MY_LOCATION, l.latitude, l.longitude))
    }

    // --- Bank holidays (England & Wales, live from gov.uk, cached)

    fun refreshBankHolidays() {
        viewModelScope.launch {
            val text = httpGet("https://www.gov.uk/bank-holidays.json") ?: return@launch
            val set = parseBankHolidays(text)
            if (set.isNotEmpty()) {
                pricer = Pricer(set)
                prefs.edit().putStringSet("bankHolidays", set).apply()
            }
        }
    }

    private fun parseBankHolidays(text: String): Set<String> = runCatching {
        Json.parseToJsonElement(text).jsonObject.getValue("england-and-wales").jsonObject.getValue("events").jsonArray
            .map { it.jsonObject.getValue("date").jsonPrimitive.content }.toSet()
    }.getOrDefault(emptySet())

    companion object {
        const val SITE = "https://london-climbing-finder.pages.dev"
        private const val REMOTE_URL = "$SITE/gyms.json"
    }
}

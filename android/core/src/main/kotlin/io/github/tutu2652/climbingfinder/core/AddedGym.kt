package io.github.tutu2652.climbingfinder.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Serializable
enum class GymGrading(val title: String) {
    @SerialName("v") V("V scale"),
    @SerialName("font") FONT("Fontainebleau"),
    @SerialName("colour") COLOUR("Colour circuits"),
    @SerialName("banana") BANANA("Banana Climbing (香蕉攀岩)"),
    @SerialName("french") FRENCH("French sport"),
    @SerialName("yds") YDS("Yosemite Decimal"),
    @SerialName("uiaa") UIAA("UIAA"),
    @SerialName("ewbank") EWBANK("Ewbank"),
    @SerialName("custom") CUSTOM("Custom labels");

    val usesEditableLabels: Boolean get() = this == CUSTOM || this == BANANA

    fun labels(custom: List<String>?): List<String> = when (this) {
        FONT -> listOf("1", "2", "3", "3+", "4", "4+", "5", "5+", "6A", "6A+", "6B", "6B+", "6C", "6C+",
            "7A", "7A+", "7B", "7B+", "7C", "7C+", "8A", "8A+", "8B", "8B+", "8C", "8C+", "9A")
        BANANA -> custom ?: (0..20).map(Int::toString)
        YDS -> (1..9).map { "5.$it" } + (10..15).flatMap { n -> listOf("a", "b", "c", "d").map { "5.$n$it" } }
        UIAA -> listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
            .flatMap { listOf(it + "−", it, it + "+") }
        EWBANK -> (1..39).map(Int::toString)
        CUSTOM -> custom ?: (1..10).map(Int::toString)
        V, COLOUR, FRENCH -> emptyList()
    }

    companion object {
        val boulderChoices = listOf(V, FONT, COLOUR, BANANA, CUSTOM)
        val ropeChoices = listOf(FRENCH, YDS, UIAA, EWBANK, CUSTOM)

        fun parseLabels(input: String): List<String> =
            input.split(',', '，', '\n', '\r', '\u0085', '\u2028', '\u2029').map(String::trim).filter(String::isNotEmpty)

        fun validLabel(label: String): Boolean =
            label.isNotEmpty() && label.length <= 24 && label == label.trim() &&
                label.none { it.isISOControl() || it == '\u2028' || it == '\u2029' || it == ',' || it == '，' }

        fun validLabels(labels: List<String>?): Boolean =
            labels == null || (labels.size in 1..60 && labels.toSet().size == labels.size && labels.all(::validLabel))
    }
}

/**
 * A gym you added yourself, anywhere in the world. It is kept on this phone (and in logbook
 * backups) only: it is never uploaded or added to gyms.json. The JSON is the same in the iPhone
 * app (ios/ClimbingFinder/Core/AddedGyms.swift), so a backup from either app restores on the other.
 */
@Serializable
data class AddedGym(
    /** Always starts with [ID_PREFIX]; no gym in gyms.json may (tests/test_added_gyms.py checks). */
    val id: String,
    val name: String,
    /** Town or city; may be blank. */
    val area: String = "",
    /** e.g. "France". Filled in from the pin, and can be changed. */
    val country: String? = null,
    val postcode: String? = null,
    val address: String? = null,
    /** Where the pin is: latitude and longitude, in degrees. */
    val lat: Double,
    val lng: Double,
    /** The gym's time zone, e.g. "Europe/Paris", found from the pin. Its opening hours are in this local time. */
    val timeZone: String? = null,
    /** One of [TYPES], as in gyms.json. */
    val type: String = "bouldering",
    /** A day pass at the usual (peak) price, in pounds. */
    val adultPrice: Double? = null,
    val concessionPrice: Double? = null,
    /**
     * Opening hours by day in the gym's local time, as in gyms.json: {"Mon": ["09:00", "22:00"]}.
     * A missing day is closed; none at all means not known.
     */
    val hours: Map<String, List<String>> = emptyMap(),
    val website: String? = null,
    val notes: String? = null,
    val boulderGrading: GymGrading? = null,
    val ropeGrading: GymGrading? = null,
    val boulderGradeLabels: List<String>? = null,
    val ropeGradeLabels: List<String>? = null,
) {
    fun grading(discipline: Discipline): GymGrading =
        if (discipline == Discipline.BOULDER) boulderGrading ?: GymGrading.V else ropeGrading ?: GymGrading.FRENCH

    fun alternateGrades(discipline: Discipline): List<String> =
        grading(discipline).labels(if (discipline == Discipline.BOULDER) boulderGradeLabels else ropeGradeLabels)

    val hasGradingSettings: Boolean get() = boulderGrading != null || ropeGrading != null

    /**
     * Everything needed to show, map and price it safely. Only a name and a pin are required:
     * the rest is filled in from the pin when possible, but may be blank (e.g. offline abroad).
     */
    val isValid: Boolean
        get() = id.startsWith(ID_PREFIX) && id.length > ID_PREFIX.length &&
            name.isNotBlank() &&
            lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 &&
            (timeZone == null || zone != null) && type in TYPES &&
            grading(Discipline.BOULDER) in GymGrading.boulderChoices && grading(Discipline.ROPE) in GymGrading.ropeChoices &&
            GymGrading.validLabels(boulderGradeLabels) && GymGrading.validLabels(ropeGradeLabels) &&
            listOfNotNull(adultPrice, concessionPrice).all { it.isFinite() && it >= 0 && it < 1000 } &&
            hours.all { (day, t) -> day in DAYS && t.size == 2 && t.all { TIME.matches(it) } && t[0] < t[1] }

    /** The time zone, if it's one this phone knows. */
    val zone: ZoneId? get() = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** The date and time at the gym, for its opening hours. London time if its time zone isn't known. */
    fun localMoment(at: Instant): Moment {
        val t = at.atZone(zone ?: London)
        return Moment(t.year, t.monthValue, t.dayOfMonth, t.hour * 60 + t.minute)
    }

    /** As a gym from gyms.json, so the map, lists, logbook and Pricer treat it like any other. */
    fun toGym(): Gym = Gym.from(JsonObject(mapOf(
        "id" to JsonPrimitive(id),
        "name" to JsonPrimitive(name),
        "brand" to JsonPrimitive(BRAND),
        "type" to JsonPrimitive(type),
        "area" to text(area),
        // An added gym has no travelcard zone; its country goes where a London gym's borough does,
        // which lists only search (so you can search by country). See Gym.zoneLabel.
        "zone" to JsonPrimitive(0),
        "borough" to text(country),
        "postcode" to text(postcode),
        "address" to text(address),
        "lat" to JsonPrimitive(lat),
        "lng" to JsonPrimitive(lng),
        "website" to text(website),
        "about" to text(notes),
        "payg" to JsonObject(mapOf(
            "adult_peak" to (adultPrice?.let { JsonPrimitive(it) } ?: JsonNull),
            "concession_peak" to (concessionPrice?.let { JsonPrimitive(it) } ?: JsonNull),
        )),
        "hours" to JsonObject(hours.mapValues { (_, t) -> JsonArray(t.map { JsonPrimitive(it) }) }),
        "verified" to JsonPrimitive(false),
    )))

    companion object {
        const val ID_PREFIX = "added-"
        /** Shown as the gym's chain, so lists group your gyms under it. */
        const val BRAND = "Added by you"
        val TYPES = listOf("bouldering", "ropes+bouldering", "ropes")
        val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        /** An added gym this far from Charing Cross or more counts as outside London. */
        const val LONDON_KM = 25.0
        private val TIME = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

        private fun text(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull

        /** A new id that isn't in `taken` (gyms.json ids and gyms you've already added). */
        fun newId(taken: Set<String>): String {
            while (true) {
                val id = ID_PREFIX + UUID.randomUUID().toString().replace("-", "").take(8)
                if (id !in taken) return id
            }
        }

        /**
         * Gyms from gyms.json followed by yours. One of yours whose id is somehow already taken,
         * or that isn't valid, is left out rather than replacing a listed gym.
         */
        fun merge(listed: List<Gym>, added: List<AddedGym>): List<Gym> {
            val ids = listed.map { it.id }.toSet()
            return listed + added.filter { it.isValid && it.id !in ids }.mapNotNull { runCatching { it.toGym() }.getOrNull() }
        }

        /** Gyms from a backup that aren't here yet (matched by id); yours are kept as they are. */
        fun mergeFromBackup(existing: List<AddedGym>, fromBackup: List<AddedGym>?): List<AddedGym> {
            val have = existing.map { it.id }.toMutableSet()
            return existing + fromBackup.orEmpty().filter { have.add(it.id) }
        }
    }
}

/** A gym you added on this phone, not one from gyms.json. */
val Gym.isAddedByYou: Boolean get() = id.startsWith(AddedGym.ID_PREFIX)

/**
 * A gym you added outside London (elsewhere in the UK, or abroad). It is left out of the
 * Cheapest ranking and TfL travel times, and only priced on its own page.
 */
val Gym.outsideLondon: Boolean
    get() = isAddedByYou && haversineKm(51.5080, -0.1247, lat, lng) >= AddedGym.LONDON_KM

/** "Zone 2"; for a gym you added, its country (or "Added by you" if none is set). */
val Gym.zoneLabel: String get() = if (isAddedByYou) borough ?: AddedGym.BRAND else "Zone $zone"

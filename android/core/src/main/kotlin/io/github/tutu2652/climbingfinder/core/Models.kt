package io.github.tutu2652.climbingfinder.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

// Read straight from gyms.json (the repo's single source of truth, bundled
// into the app at build time). Field names mirror the JSON; see README.md.
// Mirrors ios/ClimbingFinder/Core/Models.swift.

data class ClimbingData(
    val lastResearchDate: String?,
    val gyms: List<Gym>,
    val studentPromotions: List<StudentPromotion>,
    val limitedPromotions: List<Promotion>,
) {
    companion object {
        fun parse(text: String): ClimbingData {
            val root = Json.parseToJsonElement(text).jsonObject
            return ClimbingData(
                lastResearchDate = root.obj("_meta")?.str("last_research_date"),
                gyms = root.getValue("gyms").jsonArray.map { Gym.from(it.jsonObject) },
                studentPromotions = root.objects("student_promotions").map {
                    StudentPromotion(it.req("scope"), it.req("offer"), it.str("source"))
                },
                limitedPromotions = root.objects("limited_promotions").map {
                    Promotion(
                        id = it.req("id"), brand = it.req("brand"), title = it.req("title"),
                        details = it.str("details"), price = it.str("price"), valid = it.str("valid"),
                        expires = it.str("expires"), expiresSource = it.str("expires_source"),
                        source = it.str("source"), status = it.req("status"),
                    )
                },
            )
        }
    }
}

data class Gym(
    val id: String,
    val name: String,
    val brand: String,
    val type: String,
    val area: String?,
    val borough: String?,
    val zone: Int,
    val postcode: String?,
    val lat: Double,
    val lng: Double,
    val website: String?,
    val pricesURL: String?,
    val payg: Payg,
    val offpeakWindows: List<OffpeakWindow>,
    val offpeakAssumed: Boolean,
    val magicSlots: List<MagicSlot>,
    val studentNote: String?,
    val registrationFee: Double?,
    val verified: Boolean,
    val lastChecked: String?,
    val facilities: String?,
    val membership: Membership?,
    val address: String?,
    val passes: String?,
    val hours: Map<String, List<String>>,
    val hoursAssumed: Boolean,
    val instagram: String?,
    val eventsURL: String?,
    val classesURL: String?,
    val classInfo: String?,
    val routeSettingURL: String?,
    val socials: List<String>,
    val ac: String?,
    val acNote: String?,
    val logo: String?,
    val logoBackground: String?,
    /** What the place is like, in the gym's own words (set for smaller, less-known gyms). */
    val about: String?,
    /** Set while a gym is shut until further notice; it is left out of rankings. */
    val temporarilyClosed: String?,
    /** A temporary note such as reduced hours, with the hours that apply meanwhile. */
    val notice: String?,
    val temporaryHours: Map<String, List<String>>?,
    /** The gym's bouldering colours, easiest first, from its grade chart; empty if it doesn't grade by colour. */
    val colourGrades: List<ColourGrade> = emptyList(),
    /**
     * Cheap slots only some climbers can get (women+ sessions, local residents). Priced only for
     * climbers who qualify; see [Eligibility]. Kept apart from [magicSlots] so older app versions,
     * which don't know the conditions, never offer them to everyone.
     */
    val restrictedSlots: List<MagicSlot> = emptyList(),
    /** One-off events (comps, watch parties). Each hides itself once its day has passed. */
    val events: List<GymEvent> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject): Gym {
            val payg = o.obj("payg")
            // offpeak_window may be a single object, a list of objects, or null.
            val windows = when (val w = o["offpeak_window"]) {
                is JsonArray -> w.map { OffpeakWindow.from(it.jsonObject) }
                is JsonObject -> listOf(OffpeakWindow.from(w))
                else -> emptyList()
            }
            val membership = o.obj("membership")
            return Gym(
                id = o.req("id"),
                name = o.req("name"),
                brand = o.req("brand"),
                type = o.req("type"),
                area = o.str("area"),
                borough = o.str("borough"),
                zone = o.int("zone") ?: error("${o["id"]}: zone missing"),
                postcode = o.str("postcode"),
                lat = o.num("lat") ?: error("${o["id"]}: lat missing"),
                lng = o.num("lng") ?: error("${o["id"]}: lng missing"),
                website = o.str("website"),
                pricesURL = o.str("prices_url"),
                payg = Payg(
                    adultPeak = payg?.num("adult_peak"),
                    adultOffpeak = payg?.num("adult_offpeak"),
                    concessionPeak = payg?.num("concession_peak"),
                    concessionOffpeak = payg?.num("concession_offpeak"),
                ),
                offpeakWindows = windows,
                offpeakAssumed = o.bool("offpeak_assumed") ?: false,
                magicSlots = o.objects("magic_slots").map { MagicSlot.from(it) },
                studentNote = o.str("student_note"),
                registrationFee = o.num("registration_fee"),
                verified = o.bool("verified") ?: false,
                lastChecked = o.str("last_checked"),
                facilities = o.str("facilities"),
                membership = membership?.let { Membership(it.num("full_monthly"), it.num("concession_monthly")) },
                address = o.str("address"),
                passes = o.str("passes"),
                hours = hoursFrom(o.obj("hours")) ?: emptyMap(),
                hoursAssumed = o.bool("hours_assumed") ?: false,
                instagram = o.str("instagram"),
                eventsURL = o.str("events_url"),
                classesURL = o.str("classes_url"),
                classInfo = o.str("class_info"),
                routeSettingURL = o.str("route_setting_url"),
                socials = o.strings("socials") ?: emptyList(),
                ac = o.str("ac"),
                acNote = o.str("ac_note"),
                // A colour whose range can't be read is still offered, without a range.
                colourGrades = o.objects("colour_grades").mapNotNull { e ->
                    val name = e.str("colour") ?: return@mapNotNull null
                    val r = e.str("grades")?.let { ColourGrade.parseRange(it) }
                    ColourGrade(name, r?.first, r?.second)
                },
                logo = o.str("logo"),
                logoBackground = o.str("logo_background"),
                about = o.str("about"),
                temporarilyClosed = o.str("temporarily_closed"),
                notice = o.str("notice"),
                temporaryHours = hoursFrom(o.obj("temporary_hours")),
                // Entries this version can't read are dropped rather than failing the gym.
                restrictedSlots = o.objects("restricted_slots")
                    .mapNotNull { runCatching { MagicSlot.from(it) }.getOrNull() }.filter { it.isRestricted },
                events = o.objects("events").mapNotNull { runCatching { GymEvent.from(it) }.getOrNull() },
            )
        }

        /** {"Mon": ["08:00", "22:00"], "Tue": null, ...}; days that are null or malformed are closed. */
        private fun hoursFrom(o: JsonObject?): Map<String, List<String>>? = o?.let { obj ->
            obj.mapNotNull { (day, v) ->
                val pair = (v as? JsonArray)?.map { (it as JsonPrimitive).content }
                if (pair != null && pair.size == 2) day to pair else null
            }.toMap()
        }
    }

    override fun equals(other: Any?) = other is Gym && other.id == id
    override fun hashCode() = id.hashCode()
}

data class Payg(
    val adultPeak: Double?,
    val adultOffpeak: Double?,
    val concessionPeak: Double?,
    val concessionOffpeak: Double?,
)

data class OffpeakWindow(
    val days: List<String>,
    val start: String,
    val end: String,
    val concessionOnly: Boolean,
    val exceptBankHolidays: Boolean,
) {
    companion object {
        fun from(o: JsonObject) = OffpeakWindow(
            days = o.strings("days") ?: error("offpeak_window without days"),
            start = o.req("start"),
            end = o.req("end"),
            concessionOnly = o.bool("concession_only") ?: false,
            exceptBankHolidays = o.bool("except_bank_holidays") ?: false,
        )
    }
}

data class MagicSlot(
    val days: List<String>,
    val start: String,
    val end: String,
    val price: Double,
    val label: String,
    val months: List<Int>?,
    val prebook: Boolean,
    val exceptBankHolidays: Boolean,
    /** Only for women+ climbers (restricted slots only). */
    val womenOnly: Boolean = false,
    /** Only for people living in these postcode districts, e.g. ["SE16"] (restricted slots only). */
    val postcodes: List<String> = emptyList(),
) {
    val isConcession: Boolean get() = label.contains("concession", ignoreCase = true)
    val isAllDay: Boolean get() = start == "00:00" && end == "23:59"
    val isRestricted: Boolean get() = womenOnly || postcodes.isNotEmpty()

    /** Who can get it, e.g. "women+" or "SE16 residents"; empty for everyone. */
    val condition: String get() = listOfNotNull(
        "women+".takeIf { womenOnly },
        postcodes.takeIf { it.isNotEmpty() }?.let { it.joinToString("/") + " residents" },
    ).joinToString(", ")

    companion object {
        fun from(o: JsonObject) = MagicSlot(
            days = o.strings("days") ?: error("magic slot without days"),
            start = o.req("start"),
            end = o.req("end"),
            price = o.num("price") ?: error("magic slot without price"),
            label = o.str("label") ?: "magic",
            months = (o["months"] as? JsonArray)?.map { (it as JsonPrimitive).intOrNull ?: error("bad month") },
            prebook = o.bool("prebook") ?: false,
            exceptBankHolidays = o.bool("except_bank_holidays") ?: false,
            womenOnly = o.bool("women_only") ?: false,
            postcodes = o.strings("postcodes")?.map { it.uppercase() } ?: emptyList(),
        )
    }
}

/** A one-off event at a gym. Shown until the end of [endDate] (or [date]), then hidden. */
data class GymEvent(
    val date: String,
    val endDate: String? = null,
    val start: String? = null,
    val end: String? = null,
    val title: String,
    val details: String? = null,
    val url: String? = null,
    val price: String? = null,
) {
    val id: String get() = date + title

    /** Still to come (or on) the given London day, "YYYY-MM-DD". */
    fun isUpcoming(today: String): Boolean = (endDate ?: date) >= today

    /** "Sat 14 Nov · from 18:00". */
    val whenText: String get() {
        fun day(ymd: String): String {
            val p = ymd.split("-").mapNotNull { it.toIntOrNull() }
            if (p.size != 3) return ymd
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            return "${Moment(p[0], p[1], p[2]).weekday} ${p[2]} ${months[p[1] - 1]}"
        }
        var s = day(date)
        if (endDate != null && endDate != date) s += " – " + day(endDate)
        when {
            start != null && end != null -> s += " · $start–$end"
            start != null -> s += " · from $start"
        }
        return s
    }

    companion object {
        fun from(o: JsonObject) = GymEvent(
            date = o.req("date"),
            endDate = o.str("end_date"),
            start = o.str("start"),
            end = o.str("end"),
            title = o.req("title"),
            details = o.str("details"),
            url = o.str("url"),
            price = o.str("price"),
        )
    }
}

data class Membership(val fullMonthly: Double?, val concessionMonthly: Double?)

data class StudentPromotion(val scope: String, val offer: String, val source: String?)

data class Promotion(
    val id: String,
    val brand: String,
    val title: String,
    val details: String?,
    val price: String?,
    val valid: String?,
    val expires: String?,
    val expiresSource: String?,
    val source: String?,
    val status: String,
) {
    /**
     * Past it if marked so, or if its published end date has gone by. A null
     * `expires` means "no end date known", not "runs forever".
     */
    fun isExpired(today: String): Boolean {
        if (status == "expired") return true
        return expires != null && expires < today
    }
}

// --- JSON helpers: missing and null are the same, like Swift's decodeIfPresent.

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
internal fun JsonObject.str(key: String): String? = prim(key)?.takeIf { it.isString }?.content
internal fun JsonObject.req(key: String): String = str(key) ?: error("missing \"$key\" in ${this["id"] ?: this}")
internal fun JsonObject.num(key: String): Double? = prim(key)?.doubleOrNull
internal fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
internal fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
internal fun JsonObject.strings(key: String): List<String>? = (this[key] as? JsonArray)?.map { (it as JsonPrimitive).content }

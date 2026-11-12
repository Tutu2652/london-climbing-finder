package io.github.tutu2652.climbingfinder.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Price logic. Mirrors assets/app.js and ios/ClimbingFinder/Core/Pricing.swift
// (which mirror london_climbing_prices.py); tests/test_pricing.py checks all
// four agree.

val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

val London: ZoneId = ZoneId.of("Europe/London")

/**
 * A wall-clock moment in London: the calendar day plus minutes since midnight.
 * Kept separate from Instant so pricing never depends on time-zone arithmetic.
 */
data class Moment(val year: Int, val month: Int, val day: Int, val minutes: Int = 0) {
    fun at(minutes: Int) = Moment(year, month, day, minutes)

    val ymd: String get() = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    /** 0 = Monday ... 6 = Sunday. */
    val weekdayIndex: Int get() = LocalDate.of(year, month, day).dayOfWeek.value - 1
    val weekday: String get() = weekdays[weekdayIndex]

    companion object {
        fun of(instant: Instant): Moment {
            val t = instant.atZone(London)
            return Moment(t.year, t.monthValue, t.dayOfMonth, t.hour * 60 + t.minute)
        }

        fun now() = of(Instant.now())
    }
}

fun toMin(s: String): Int {
    val p = s.split(":").mapNotNull { it.toIntOrNull() }
    return p[0] * 60 + p[1]
}

fun hhmm(m: Int): String = String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)

fun inWindow(day: String, mins: Int, days: List<String>, start: String, end: String): Boolean =
    day in days && toMin(start) <= mins && mins < toMin(end)

enum class PriceKind { PEAK, OFF, MAGIC, NONE }

data class PriceResult(val price: Double?, val label: String, val kind: PriceKind)

data class DayBest(val price: Double, val label: String, val kind: PriceKind, val `when`: String)

data class DealOption(val price: Double, val label: String, val `when`: String)

private val boardOnly = Regex("board[- ]?only", RegexOption.IGNORE_CASE)
private val offPeakLabel = Regex("off[- ]?peak", RegexOption.IGNORE_CASE)

/**
 * Who the climber is, for the cheap slots only some people can get. Set in More → Prices for
 * you and kept on the phone. Nobody qualifies by default, which matches the website and the
 * command line (they never price restricted slots).
 */
data class Eligibility(val women: Boolean = false, val postcode: String = "") {
    /** The postcode district ("SE16" from "se16 4dg" or "SE16"), or null if too short. */
    val district: String? get() {
        val c = postcode.uppercase().filter { it.isLetterOrDigit() }
        if (c.length < 2) return null
        return if (c.length >= 5) c.dropLast(3) else c
    }

    fun allows(s: MagicSlot): Boolean {
        if (s.womenOnly && !women) return false
        if (s.postcodes.isNotEmpty()) {
            val d = district ?: return false
            if (d !in s.postcodes) return false
        }
        return true
    }
}

/** Every cheap slot this climber can get: the open ones plus restricted ones they qualify for. */
fun Gym.slots(e: Eligibility): List<MagicSlot> = magicSlots + restrictedSlots.filter { e.allows(it) }

/** Holds the bank-holiday set and the climber's eligibility the pricing consults. */
data class Pricer(val bankHolidays: Set<String> = emptySet(), val eligibility: Eligibility = Eligibility()) {

    fun isBankHoliday(m: Moment) = m.ymd in bankHolidays

    fun applicable(gym: Gym, `when`: Moment, conc: Boolean): PriceResult {
        val day = `when`.weekday
        val mins = `when`.minutes
        val p = gym.payg
        val bh = isBankHoliday(`when`)
        var bestMagic: Pair<Double, String>? = null
        for (s in gym.slots(eligibility)) {
            if (!inWindow(day, mins, s.days, s.start, s.end)) continue
            if (s.months != null && `when`.month !in s.months) continue
            if (s.exceptBankHolidays && bh) continue
            val lbl = s.label
            if (lbl.lowercase().contains("concession") && !conc) continue
            if (boardOnly.containsMatchIn(lbl)) continue   // board-only isn't standard entry
            val full = if (offPeakLabel.containsMatchIn(lbl)) lbl + (if (s.prebook) " (pre-book)" else "")
            else "super off-peak (" + lbl + (if (s.prebook) ", pre-book" else "") + ")"
            if (bestMagic == null || s.price < bestMagic.first) bestMagic = s.price to full
        }
        if (bestMagic != null) return PriceResult(bestMagic.first, bestMagic.second, PriceKind.MAGIC)
        val wins = gym.offpeakWindows
        val isOff = wins.any { inWindow(day, mins, it.days, it.start, it.end) && !(it.exceptBankHolidays && bh) }
        val concOnly = wins.any { it.concessionOnly }
        val off: Double?
        val peak: Double?
        if (conc) {
            off = p.concessionOffpeak; peak = p.concessionPeak
        } else {
            off = if (concOnly) null else p.adultOffpeak; peak = p.adultPeak
        }
        if (isOff && off != null) return PriceResult(off, "off-peak", PriceKind.OFF)
        if (peak != null) return PriceResult(peak, "peak", PriceKind.PEAK)
        if (off != null && wins.isEmpty()) return PriceResult(off, "off-peak", PriceKind.OFF)
        return PriceResult(null, "no data", PriceKind.NONE)
    }

    /** Cheapest price achievable any time on the day, and the window(s) it applies. */
    fun bestOnDay(gym: Gym, date: Moment, conc: Boolean, step: Int = 15): DayBest? {
        data class Sample(val m: Int, val price: Double, val label: String, val kind: PriceKind)
        val samples = (0 until 24 * 60 step step).mapNotNull { m ->
            val r = applicable(gym, date.at(m), conc)
            r.price?.let { Sample(m, it, r.label, r.kind) }
        }
        val minp = samples.minOfOrNull { it.price } ?: return null
        val first = samples.first { it.price == minp }
        val ranges = mutableListOf<Pair<Int, Int>>()
        var cur: Pair<Int, Int>? = null
        for (s in samples) {
            if (s.price == minp) cur = cur?.let { it.first to s.m } ?: (s.m to s.m)
            else if (cur != null) { ranges.add(cur); cur = null }
        }
        if (cur != null) ranges.add(cur)
        val w = if (ranges.size == 1 && ranges[0].first == 0 && ranges[0].second >= 24 * 60 - step) "any time"
        else ranges.joinToString("; ") { hhmm(it.first) + "-" + hhmm(minOf(it.second + step, 24 * 60 - 1)) }
        return DayBest(minp, first.label, first.kind, w)
    }

    /** Every distinct deal option on the day (magic slots + off-peak), cheapest first. */
    fun slotsOnDay(gym: Gym, `when`: Moment, conc: Boolean): List<DealOption> {
        val day = `when`.weekday
        val bh = isBankHoliday(`when`)
        val p = gym.payg
        val opts = mutableListOf<DealOption>()
        for (s in gym.slots(eligibility)) {
            if (day !in s.days) continue
            if (s.months != null && `when`.month !in s.months) continue
            if (s.exceptBankHolidays && bh) continue
            if (s.isConcession && !conc) continue
            val w = if (s.isAllDay) "any time" else (s.start + "-" + s.end) + (if (s.prebook) " (pre-book)" else "")
            opts.add(DealOption(s.price, s.label.replaceFirst(" (concession)", ""), w))
        }
        val wins = gym.offpeakWindows
        val w = wins.firstOrNull { day in it.days && !(it.exceptBankHolidays && bh) }
        val concOnly = wins.any { it.concessionOnly }
        var off = if (conc) (p.concessionOffpeak ?: p.adultOffpeak) else p.adultOffpeak
        if (!conc && concOnly) off = null
        if (w != null && off != null) opts.add(DealOption(off, "off-peak", w.start + "-" + w.end))
        return opts.sortedBy { it.price }   // stable, like the Swift and JS
    }

    /** The contiguous window on the day during which the price at `when` stays available. */
    fun windowForTime(gym: Gym, `when`: Moment, conc: Boolean, step: Int = 15): String {
        val target = applicable(gym, `when`, conc)
        if (target.price == null) return ""
        fun same(m: Int): Boolean {
            val r = applicable(gym, `when`.at(m), conc)
            return r.price == target.price && r.label == target.label
        }
        val m0 = (`when`.minutes / step) * step
        var lo = m0
        var hi = m0
        while (lo - step >= 0 && same(lo - step)) lo -= step
        while (hi + step < 24 * 60 && same(hi + step)) hi += step
        if (lo <= 0 && hi >= 24 * 60 - step) return "any time"
        return hhmm(lo) + "-" + hhmm(minOf(hi + step, 24 * 60 - 1))
    }
}

// --- Prices through the week

/** A stretch of one day's opening time at one price, for the gym page's week grid. */
data class PriceBand(val day: Moment, val start: Int, val end: Int, val price: Double, val label: String, val kind: PriceKind)

/** The same time of day `n` days later (or earlier, if negative). */
fun Moment.plusDays(n: Long): Moment {
    val d = LocalDate.of(year, month, day).plusDays(n)
    return Moment(d.year, d.monthValue, d.dayOfMonth, minutes)
}

/** Monday to Sunday of the week this day falls in, each at midnight. */
val Moment.week: List<Moment> get() = (0..6).map { plusDays((it - weekdayIndex).toLong()).at(0) }

/**
 * The day's opening hours split into bands of one price each; empty if the gym is shut.
 * Mirrors `week_bands` in london_climbing_prices.py, but for a real date.
 */
fun Pricer.dayBands(gym: Gym, date: Moment, conc: Boolean, step: Int = 15): List<PriceBand> {
    val t = (gym.temporaryHours ?: gym.hours)[date.weekday]?.takeIf { it.size == 2 } ?: return emptyList()
    val closes = toMin(t[1])
    val bands = mutableListOf<PriceBand>()
    var m = toMin(t[0])
    while (m < closes) {
        val r = applicable(gym, date.at(m), conc)
        val end = minOf(m + step, closes)
        val last = bands.lastOrNull()
        if (r.price != null) {
            if (last != null && last.end == m && last.price == r.price && last.label == r.label) bands[bands.size - 1] = last.copy(end = end)
            else bands.add(PriceBand(date.at(0), m, end, r.price, r.label, r.kind))
        }
        m += step
    }
    return bands
}

/** £8 rather than £8.00, to fit inside a week-grid block; pence are kept (£10.50). */
fun shortPrice(p: Double): String = if (p == Math.rint(p)) "£" + p.toInt() else formatPrice(p)

// --- Display helpers (mirror the site's view code)

fun formatPrice(p: Double?): String = if (p == null) "n/a" else "£" + String.format(Locale.ROOT, "%.2f", p)

/** [Mon,Tue,Wed,Thu,Fri] -> "Mon-Fri"; [Mon,Fri] -> "Mon,Fri". */
fun formatDays(days: List<String>): String {
    val idx = days.map { weekdays.indexOf(it) }.filter { it >= 0 }.sorted()
    val runs = mutableListOf<MutableList<Int>>()
    for (i in idx) {
        val last = runs.lastOrNull()?.lastOrNull()
        if (last != null && i == last + 1) runs.last().add(i) else runs.add(mutableListOf(i))
    }
    return runs.joinToString(",") { r ->
        if (r.size >= 3) weekdays[r.first()] + "-" + weekdays[r.last()] else r.joinToString(",") { weekdays[it] }
    }
}

data class OpenState(val open: Boolean, val text: String)

val Gym.acLevel: String get() = ac ?: "none"

fun Gym.matchesAC(filter: String): Boolean = when {
    filter.isEmpty() -> true
    filter == "full" -> acLevel == "full"
    else -> acLevel != "none"
}

val Gym.shortName: String
    get() = name.replaceFirst(" (LCC)", "")
        .replaceFirst("Climbing District - ", "")
        .replaceFirst("City Bouldering ", "")
        .replaceFirst("The Font ", "Font ")
        .replaceFirst(" (City Bouldering)", "")

val Gym.typeLabel: String get() = type.replace("+", " + ")

val Gym.instagramURL: String? get() = instagram?.let { "https://www.instagram.com/$it/" }

val Gym.brandRank: Int get() = if (brand == "London Climbing Centres") 0 else if (brand == "Independent") 2 else 1

fun Gym.openState(`when`: Moment): OpenState? {
    if (temporarilyClosed != null) return OpenState(false, "temporarily closed")
    val hours = temporaryHours ?: this.hours
    if (hours.isEmpty()) return null
    val t = hours[`when`.weekday] ?: return OpenState(false, "closed today")
    val o = toMin(t[0])
    val c = toMin(t[1])
    if (`when`.minutes < o) return OpenState(false, "opens " + t[0])
    if (`when`.minutes >= c) return OpenState(false, "closed")
    return OpenState(true, "open till " + t[1])
}

val Gym.hoursSummary: String
    get() {
        if (hours.isEmpty()) return "—"
        val parts = mutableListOf<String>()
        var i = 0
        while (i < 7) {
            val cur = hours[weekdays[i]]
            var j = i
            while (j + 1 < 7 && hours[weekdays[j + 1]] == cur) j++
            val lbl = if (i == j) weekdays[i] else weekdays[i] + "-" + weekdays[j]
            parts.add(lbl + " " + (cur?.let { it[0] + "-" + it[1] } ?: "closed"))
            i = j + 1
        }
        return parts.joinToString("; ") + if (hoursAssumed) " (approx)" else ""
    }

fun Gym.peakPrice(conc: Boolean): Double? = if (conc) payg.concessionPeak else payg.adultPeak

/** Cheapest off-peak / deal price the selected climber can actually use. */
fun Gym.bestOffpeak(conc: Boolean, eligibility: Eligibility = Eligibility()): Double? {
    val ps = mutableListOf<Double>()
    (if (conc) (payg.concessionOffpeak ?: payg.adultOffpeak) else payg.adultOffpeak)?.let { ps.add(it) }
    for (s in slots(eligibility)) if (conc || !s.isConcession) ps.add(s.price)
    return ps.minOrNull()
}

data class DealLine(
    val price: Double,
    val label: String,
    val `when`: String,
    val extra: String,
    val approx: Boolean,
    val magic: Boolean,
)

/**
 * Off-peak plus every special cheap slot, one line each, cheapest first. Restricted slots are
 * always listed, with who can get them, so you can see them before you qualify.
 */
fun Gym.dealLines(conc: Boolean): List<DealLine> {
    val items = mutableListOf<DealLine>()
    val ao = payg.adultOffpeak
    val co = payg.concessionOffpeak
    if (ao != null || co != null) {
        var extra = ""
        val main: Double
        if (ao == null) { main = co!!; extra = " conc" }
        else if (co == null || co == ao) main = ao
        else {
            main = if (conc) co else ao
            extra = if (conc) " (${formatPrice(ao)} adult)" else " (${formatPrice(co)} conc)"
        }
        val whens = offpeakWindows.map { w ->
            formatDays(w.days) + " " + w.start + "-" + w.end + if (w.exceptBankHolidays) " (not bank hols)" else ""
        }
        if (whens.isNotEmpty()) {
            items.add(DealLine(main, "off-peak", whens.joinToString(", "), extra, offpeakAssumed, false))
        }
    }
    data class Group(val price: Double, val label: String, val prebook: Boolean, val conc: Boolean,
                     val condition: String, val whens: MutableList<String>)
    val groups = mutableListOf<Group>()
    for (s in magicSlots + restrictedSlots) {
        var w = formatDays(s.days) + if (s.isAllDay) " all day" else " " + s.start + "-" + s.end
        if (s.exceptBankHolidays) w += " (not bank hols)"
        val label = s.label.replaceFirst(" (concession)", "")
        val g = groups.firstOrNull {
            it.label == label && it.price == s.price && it.prebook == s.prebook && it.conc == s.isConcession && it.condition == s.condition
        }
        if (g != null) g.whens.add(w) else groups.add(Group(s.price, label, s.prebook, s.isConcession, s.condition, mutableListOf(w)))
    }
    for (g in groups) {
        items.add(DealLine(g.price, g.label, g.whens.joinToString(", "),
            (if (g.prebook) " (pre-book)" else "") + (if (g.conc) " conc" else "")
                + (if (g.condition.isEmpty()) "" else " · ${g.condition} only"), approx = false, magic = true))
    }
    return items.sortedBy { it.price }
}

fun haversineKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val s = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(s), sqrt(1 - s))
}

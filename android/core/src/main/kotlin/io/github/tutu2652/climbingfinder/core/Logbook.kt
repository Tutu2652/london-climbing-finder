package io.github.tutu2652.climbingfinder.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

// Your climbing log: sessions at a gym, and the climbs in each. The app keeps
// it as one JSON file in the iPhone app's backup format (version 4), so a
// backup from either app can be restored on the other. Mirrors
// ios/ClimbingFinder/Core/Logbook.swift.

enum class Discipline(val raw: String, val title: String) {
    BOULDER("boulder", "Bouldering"),
    ROPE("rope", "Ropes");

    /** Grades in difficulty order; a climb stores its index into this list. */
    val grades: List<String>
        get() = when (this) {
            BOULDER -> (0..17).map { "V$it" }
            ROPE -> listOf("4", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
                "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c")
        }

    /** Grades shown on the logging grid before "More grades". */
    val commonCount: Int get() = if (this == BOULDER) 11 else 14

    companion object {
        fun of(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

enum class ClimbResult(val raw: String, val title: String) {
    FLASH("flash", "Flash"),
    SEND("send", "Send"),
    ATTEMPT("attempt", "Attempt");

    /** A flash is a send on the first go. */
    val isSend: Boolean get() = this != ATTEMPT

    companion object {
        fun of(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

/** ISO 8601 in UTC without fractions ("2026-07-06T09:30:00Z"), which is what the iPhone app reads and writes. */
object IsoInstant : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("IsoInstant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

/** Swift writes UUIDs in upper case; compare them that way. */
object UpperId : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("UpperId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value.uppercase())
    override fun deserialize(decoder: Decoder): String = decoder.decodeString().uppercase()
}

fun newId(): String = UUID.randomUUID().toString().uppercase()

/** Wall-clock times are kept to the second so they survive a round trip through a backup. */
fun nowSecond(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

@Serializable
data class Climb(
    @Serializable(UpperId::class) val id: String = newId(),
    @SerialName("discipline") val disciplineRaw: String = Discipline.BOULDER.raw,
    val grade: Int = 0,
    @SerialName("result") val resultRaw: String = ClimbResult.SEND.raw,
    @Serializable(IsoInstant::class) val loggedAt: Instant = nowSecond(),
    /** Photos references from the iPhone app; kept so a backup round trip doesn't drop them. */
    val videoAssetID: String? = null,
    val routePhotoAssetID: String? = null,
    /**
     * A route photo and video linked on Android: content:// links to files in the phone's
     * gallery, which stay where they are. Only the link is saved, and it only opens on the
     * phone that made it. The iPhone app doesn't read these.
     */
    val androidPhotoURI: String? = null,
    val androidVideoURI: String? = null,
    /**
     * Details of the linked file, so the app can find the same photo or video again after
     * it's reinstalled and the backup restored (Android forgets picked items then).
     */
    val androidPhotoFile: MediaFile? = null,
    val androidVideoFile: MediaFile? = null,
    /** A competition problem. Its grade is how it felt, and may be left out. Missing in version 1 and 2 backups. */
    val isComp: Boolean? = null,
    /**
     * Set when logged by hold colour at a colour-graded gym: the colour and the V range
     * it stood for that day, kept even if the gym changes its colours later. `grade` is
     * what counts in stats: the bottom of the range, unless you've said how it felt.
     * Colours with no V range (a gym that only ranks its colours) start ungraded.
     * Missing before version 4.
     */
    val colour: String? = null,
    val colourLow: Int? = null,
    val colourHigh: Int? = null,
    /** Present (true) only when you picked how a colour climb felt, even the bottom of its range. */
    val feltGradeSet: Boolean? = null,
) {
    val discipline: Discipline get() = Discipline.of(disciplineRaw) ?: Discipline.BOULDER
    val result: ClimbResult get() = ClimbResult.of(resultRaw) ?: ClimbResult.SEND
    val comp: Boolean get() = isComp == true
    val isGraded: Boolean get() = grade in discipline.grades.indices
    val gradeLabel: String get() = if (isGraded) discipline.grades[grade] else if (grade == UNGRADED) "–" else "?"
    /** The colour and range this climb was logged with, if it was logged by colour. */
    val colourGrade: ColourGrade? get() = colour?.let { ColourGrade(it, colourLow, colourHigh) }
    /**
     * A felt grade has been set on a colour climb, rather than it counting as the bottom of
     * its range. Climbs from before felt grades were remembered count if the grade moved.
     */
    val hasFeltGrade: Boolean get() = colourGrade?.let { c -> feltGradeSet == true || grade != (c.low ?: UNGRADED) } ?: false

    /** How a colour climb felt, or null when it counts as the bottom of its range (or, with no range, isn't graded). */
    val feltGrade: Int? get() = if (hasFeltGrade && isGraded) grade else null

    /**
     * The grade a list shows next to a colour dot: the felt grade if you set one, else the
     * colour's range ("" for a colour with no V grades). Plain climbs show their grade.
     */
    val listGrade: String get() = colourGrade?.let { c -> if (hasFeltGrade && isGraded) gradeLabel else c.range } ?: gradeLabel
    /** Show "felt" after `listGrade`. */
    val listGradeIsFelt: Boolean get() = colourGrade != null && hasFeltGrade && isGraded

    /** With this felt grade, or (null) counting as the bottom of the range again. */
    fun withFeltGrade(g: Int?): Climb {
        val c = colourGrade ?: return this
        return if (g != null) copy(grade = g, feltGradeSet = true) else copy(grade = c.low ?: UNGRADED, feltGradeSet = null)
    }
    /** "V2–V3", "V2–V3 · felt V3" or "felt V3" for a colour climb; null otherwise or when there's nothing to say. */
    val colourDetail: String?
        get() = colourGrade?.let { c ->
            listOf(c.range, if (hasFeltGrade) "felt $gradeLabel" else "").filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    /**
     * Graded by a colour from the gym's chart (counting as the bottom of its range), or,
     * with null, by V grade again (keeping the grade it counts as).
     */
    fun withColour(c: ColourGrade?): Climb = when {
        c != null && c == colourGrade -> this
        c != null -> copy(colour = c.name, colourLow = c.low, colourHigh = c.high, disciplineRaw = Discipline.BOULDER.raw,
            isComp = false, grade = c.low ?: UNGRADED, feltGradeSet = null)
        colour == null -> this
        else -> copy(colour = null, colourLow = null, colourHigh = null, feltGradeSet = null).let { if (it.isGraded) it else it.copy(grade = 0) }
    }

    /** Moved to boulders or ropes, keeping the grade where it can. Ropes have no colours. */
    fun withDiscipline(d: Discipline): Climb {
        if (d == discipline) return this
        val base = if (d == Discipline.ROPE) withColour(null) else this
        val g = if (base.grade >= 0) base.grade.coerceIn(0, d.grades.size - 1) else base.grade
        return base.copy(disciplineRaw = d.raw, grade = g)
    }

    /** The photo or video linked to this climb on Android, if any. */
    fun mediaURI(kind: MediaKind): String? = if (kind == MediaKind.PHOTO) androidPhotoURI else androidVideoURI

    /** Details of the file linked on Android, if known. */
    fun mediaFile(kind: MediaKind): MediaFile? = if (kind == MediaKind.PHOTO) androidPhotoFile else androidVideoFile

    /** With this Android photo or video link and its file, or (null) without either. */
    fun withMedia(kind: MediaKind, uri: String?, file: MediaFile? = null): Climb {
        val f = file.takeIf { uri != null }
        return if (kind == MediaKind.PHOTO) copy(androidPhotoURI = uri, androidPhotoFile = f)
        else copy(androidVideoURI = uri, androidVideoFile = f)
    }

    val hasAndroidMedia: Boolean get() = androidPhotoURI != null || androidVideoURI != null

    /** "V4", "Comp V4", "Comp" or, for a colour climb, "Blue". */
    val label: String
        get() = colour ?: if (comp) (if (isGraded) "Comp $gradeLabel" else "Comp") else gradeLabel

    companion object {
        /** Grade value for a comp problem logged without a grade. */
        const val UNGRADED = -1

        fun of(discipline: Discipline, grade: Int, result: ClimbResult, at: Instant, comp: Boolean) =
            Climb(disciplineRaw = discipline.raw, grade = grade, resultRaw = result.raw, loggedAt = at, isComp = comp)

        /** A boulder logged by its colour; counts as the bottom of the colour's range, if it has one. */
        fun of(colour: ColourGrade, result: ClimbResult, at: Instant) =
            Climb(grade = colour.low ?: UNGRADED, resultRaw = result.raw, loggedAt = at, isComp = false,
                colour = colour.name, colourLow = colour.low, colourHigh = colour.high)
    }
}

/**
 * A session's climbs grouped for its tally: boulders before ropes, regular before comp,
 * hardest first, ungraded last. Colour climbs group by colour and, if set, felt grade.
 * Colours that tie on grade (every colour, at a gym that only ranks its colours) follow
 * the gym's scale, hardest first; a colour no longer on the scale goes after them.
 */
fun tallyGroups(climbs: List<Climb>, scale: List<ColourGrade>): List<List<Climb>> {
    val rank = scale.withIndex().reversed().associate { it.value.name to it.index }
    return climbs.groupBy { listOf(it.disciplineRaw, it.grade, it.comp, it.colour, it.listGradeIsFelt) }.values
        .sortedWith(compareBy<List<Climb>>({ if (it[0].discipline == Discipline.ROPE) 1 else 0 }, { if (it[0].comp) 1 else 0 },
            { -it[0].grade }, { -(it[0].colour?.let(rank::get) ?: -1) }, { (it[0].colour ?: "") + it[0].listGrade }))
}

/**
 * What identifies a linked file, to find it on the phone again: its size in bytes, when it
 * was taken (epoch milliseconds) and its file name, when known. Android's photo picker
 * hides the real name, so it's often missing.
 */
@Serializable
data class MediaFile(val size: Long, val taken: Long? = null, val name: String? = null) {
    /** Could this file, found on the phone, be the linked one? The size must match; the date and name too where both are known. */
    fun matches(size: Long, taken: Long?, name: String?): Boolean =
        size == this.size && (this.taken == null || taken == null || taken == this.taken) &&
            (this.name == null || name == null || name == this.name)
}

enum class MediaKind(val title: String) {
    PHOTO("Route photo"),
    VIDEO("Climb video"),
}

/** One photo or video linked to a climb on Android, for the gallery. */
data class ClimbMedia(val session: ClimbSession, val climb: Climb, val kind: MediaKind, val uri: String) {
    val file: MediaFile? get() = climb.mediaFile(kind)
    /** Stable across recompositions, and unique even when a climb has both a photo and a video. */
    val key: String get() = "${climb.id}/${kind.name}"
}

/**
 * Every photo and video linked on Android, newest climb first; a climb's photo comes
 * before its video. Links made on an iPhone can't be opened here, so they're left out.
 */
fun linkedMedia(sessions: List<ClimbSession>, kind: MediaKind? = null): List<ClimbMedia> =
    sessions.flatMap { s ->
        s.climbs.flatMap { c ->
            listOfNotNull(
                c.androidPhotoURI?.let { ClimbMedia(s, c, MediaKind.PHOTO, it) },
                c.androidVideoURI?.let { ClimbMedia(s, c, MediaKind.VIDEO, it) },
            )
        }
    }.filter { kind == null || it.kind == kind }
        .sortedWith(compareByDescending<ClimbMedia> { it.climb.loggedAt }.thenBy { it.climb.id }.thenBy { it.kind.ordinal })

/** Every Android link in the logbook, so a link still in use elsewhere isn't released. */
fun linkedMediaURIs(sessions: List<ClimbSession>): Set<String> =
    sessions.flatMapTo(mutableSetOf()) { s -> s.climbs.flatMap { listOfNotNull(it.androidPhotoURI, it.androidVideoURI) } }

/** "3 photos and 1 video from 2 climbs", under the gallery. */
fun mediaSummary(items: List<ClimbMedia>): String {
    val photos = items.count { it.kind == MediaKind.PHOTO }
    val videos = items.size - photos
    val climbs = items.map { it.climb.id }.toSet().size
    fun n(k: Int, word: String) = "$k $word${if (k == 1) "" else "s"}"
    val parts = listOfNotNull(n(photos, "photo").takeIf { photos > 0 }, n(videos, "video").takeIf { videos > 0 })
    return parts.joinToString(" and ") + " from " + n(climbs, "climb")
}

/** Hold colours offered when setting up a gym's colour grades, with a swatch for each. */
object HoldColour {
    val all: List<Pair<String, String>> = listOf(
        "White" to "F2F2F2", "Yellow" to "F5C518", "Orange" to "F28C28", "Green" to "2EA043",
        "Teal" to "22B8C8", "Blue" to "2F6FDB", "Purple" to "8250DF", "Pink" to "E85AAD",
        "Red" to "D93636", "Grey" to "8C8C8C", "Brown" to "8B5A2B", "Black" to "1F1F1F",
    )
    fun hex(name: String): String = all.firstOrNull { it.first == name }?.second ?: "8C8C8C"
}

/**
 * One colour on a gym's circuit and the V grades it covers, as indexes into
 * `Discipline.BOULDER.grades`; no range for a gym that only ranks its colours.
 * A gym's scale is a list of these, easiest first.
 */
@Serializable
data class ColourGrade(val name: String, val low: Int? = null, val high: Int? = null) {
    /** "V2–V3", "V9+", or "" when the colour has no V range. */
    val range: String get() = if (low != null && high != null) gradeRange(low, high) else ""
    val hasRange: Boolean get() = low != null
    val isValid: Boolean
        get() {
            val g = Discipline.BOULDER.grades.indices
            if (name.isEmpty()) return false
            if (low == null && high == null) return true
            return low != null && high != null && low in g && high in g && low <= high
        }

    companion object {
        /** A gym's scale is usable if every colour is valid and appears once. */
        fun isValidScale(scale: List<ColourGrade>) = scale.all { it.isValid } && scale.map { it.name }.toSet().size == scale.size

        /** The range a newly added colour starts with: just above the hardest one so far. */
        fun nextRange(after: List<ColourGrade>): Pair<Int, Int> {
            val top = Discipline.BOULDER.grades.size - 1
            val last = after.mapNotNull { it.high }.maxOrNull() ?: return 0 to 1
            val low = minOf(top, last + 1)
            return low to minOf(top, low + 1)
        }

        /**
         * Reads a range as gyms.json writes it: "VB", "V3", "V2-V5" or "V9+". VB counts as V0,
         * the easiest grade the logbook has; "+" runs to the top. null when unreadable.
         */
        fun parseRange(text: String): Pair<Int, Int>? {
            val top = Discipline.BOULDER.grades.size - 1
            fun v(s: String): Int? {
                val t = s.trim().uppercase()
                if (t == "VB") return 0
                return t.removePrefix("V").takeIf { t.startsWith("V") }?.toIntOrNull()?.takeIf { it in 0..top }
            }
            val t = text.trim()
            if (t.endsWith("+")) return v(t.dropLast(1))?.let { it to top }
            val parts = t.split("-")
            if (parts.size !in 1..2) return null
            val l = v(parts.first()) ?: return null
            val h = v(parts.last()) ?: return null
            return if (l <= h) l to h else null
        }
    }
}

/** "V3", "V2–V3", or "V9+" for a range that runs to the top grade. */
fun gradeRange(low: Int, high: Int): String {
    val g = Discipline.BOULDER.grades
    if (low !in g.indices || high !in g.indices) return "?"
    if (low == high) return g[low]
    return if (high == g.size - 1) "${g[low]}+" else "${g[low]}–${g[high]}"
}

@Serializable
data class ClimbSession(
    @Serializable(UpperId::class) val id: String = newId(),
    val gymID: String,
    @Serializable(IsoInstant::class) val start: Instant,
    /** null while the session is still going. */
    @Serializable(IsoInstant::class) val end: Instant? = null,
    val notes: String = "",
    /** What you paid to get in; null when on a membership or pass. */
    val price: Double? = null,
    /** How the session felt, 1-5; 0 = not rated. */
    val feeling: Int = 0,
    val climbs: List<Climb> = emptyList(),
) {
    val isActive: Boolean get() = end == null
    fun durationSeconds(now: Instant = Instant.now()): Long = maxOf(0, Duration.between(start, end ?: now).seconds)
    val sends: List<Climb> get() = climbs.filter { it.result.isSend }

    /** The climb logged last, which Undo removes. */
    val lastClimb: Climb? get() = climbs.maxByOrNull { it.loggedAt }

    /**
     * When a climb tapped now is logged: now in a live session, else the session's end.
     * Always at least a second after the last climb, so the order (and Undo) stays clear
     * even for quick taps or a past session, and survives a backup's whole-second times.
     */
    fun nextLogTime(now: Instant = nowSecond()): Instant {
        val base = if (isActive) now else minOf(end ?: start, now)
        val latest = climbs.maxOfOrNull { it.loggedAt } ?: return base
        return if (latest.plusSeconds(1) > base) latest.plusSeconds(1) else base
    }

    /** Hardest graded send in the session (grade index). */
    fun hardest(d: Discipline): Int? = sends.filter { it.discipline == d && it.isGraded }.maxOfOrNull { it.grade }
}

fun formatDuration(seconds: Long): String {
    val m = maxOf(0, seconds) / 60
    return if (m >= 60) "${m / 60}h ${String.format(java.util.Locale.ROOT, "%02d", m % 60)}m" else "${m}m"
}

class RestoreError(message: String) : Exception(message)

/** The backup file, and the app's own store. */
@Serializable
data class LogbookBackup(
    val version: Int = 4,
    @Serializable(IsoInstant::class) val exported: Instant = nowSecond(),
    val sessions: List<ClimbSession>,
    /** Each gym's colour grades, by gym id. Missing before version 4. */
    val colourScales: Map<String, List<ColourGrade>>? = null,
) {
    /** Colour scales from the file for gyms that don't have one yet; existing scales are kept. */
    fun mergedColourScales(existing: Map<String, List<ColourGrade>>): Map<String, List<ColourGrade>> =
        (colourScales ?: emptyMap()) + existing

    /** Validate the whole file before changing the logbook. */
    fun validate() {
        if (version !in 1..4) throw RestoreError("This backup version isn't supported.")
        val invalid = RestoreError("This backup contains invalid dates, prices or climbs. Nothing was imported.")
        for ((gymID, scale) in colourScales ?: emptyMap()) {
            if (gymID.isEmpty() || !ColourGrade.isValidScale(scale)) throw invalid
        }
        for (s in sessions) {
            if (s.gymID.isEmpty() || s.feeling !in 0..5) throw invalid
            if (s.end != null && s.end < s.start) throw invalid
            if (s.price != null && (!s.price.isFinite() || s.price < 0)) throw invalid
            for (c in s.climbs) {
                val d = Discipline.of(c.disciplineRaw) ?: throw invalid
                // Only comp climbs and colours without a V range may be ungraded.
                val mayBeUngraded = c.isComp == true || (c.colour != null && c.colourLow == null)
                if (!(c.grade in d.grades.indices || (mayBeUngraded && c.grade == Climb.UNGRADED))) throw invalid
                ClimbResult.of(c.resultRaw) ?: throw invalid
                // A colour climb is a boulder with a valid colour; a range needs a colour.
                val cg = c.colourGrade
                if (cg != null) {
                    if (d != Discipline.BOULDER || !cg.isValid) throw invalid
                } else if (c.colourLow != null || c.colourHigh != null || c.feltGradeSet == true) throw invalid
            }
        }
    }

    /**
     * Sessions from this backup that aren't already in `existing` (matched by id). Live
     * sessions come back finished at their last logged climb (or start if empty).
     */
    fun newSessions(existing: List<ClimbSession>): List<ClimbSession> {
        validate()
        val have = existing.map { it.id }.toMutableSet()
        return sessions.filter { have.add(it.id) }.map { s ->
            val seen = mutableSetOf<String>()
            s.copy(
                end = s.end ?: maxOf(s.start, s.climbs.maxOfOrNull { it.loggedAt } ?: s.start),
                climbs = s.climbs.filter { seen.add(it.id) },
            )
        }
    }

    fun encode(): String = json.encodeToString(serializer(), copy(sessions = sessions.map { s ->
        s.copy(climbs = s.climbs.sortedBy { it.loggedAt })
    }))

    companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun decode(text: String): LogbookBackup = json.decodeFromString(serializer(), text)
    }
}

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
// it as one JSON file in the iPhone app's backup format (version 3), so a
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
    /** A competition problem. Its grade is how it felt, and may be left out. Missing in version 1 and 2 backups. */
    val isComp: Boolean? = null,
) {
    val discipline: Discipline get() = Discipline.of(disciplineRaw) ?: Discipline.BOULDER
    val result: ClimbResult get() = ClimbResult.of(resultRaw) ?: ClimbResult.SEND
    val comp: Boolean get() = isComp == true
    val isGraded: Boolean get() = grade in discipline.grades.indices
    val gradeLabel: String get() = if (isGraded) discipline.grades[grade] else if (grade == UNGRADED) "–" else "?"
    /** "V4", "Comp V4" or "Comp". */
    val label: String get() = if (comp) (if (isGraded) "Comp $gradeLabel" else "Comp") else gradeLabel

    companion object {
        /** Grade value for a comp problem logged without a grade. */
        const val UNGRADED = -1

        fun of(discipline: Discipline, grade: Int, result: ClimbResult, at: Instant, comp: Boolean) =
            Climb(disciplineRaw = discipline.raw, grade = grade, resultRaw = result.raw, loggedAt = at, isComp = comp)
    }
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
    val version: Int = 3,
    @Serializable(IsoInstant::class) val exported: Instant = nowSecond(),
    val sessions: List<ClimbSession>,
) {
    /** Validate the whole file before changing the logbook. */
    fun validate() {
        if (version !in 1..3) throw RestoreError("This backup version isn't supported.")
        val invalid = RestoreError("This backup contains invalid dates, prices or climbs. Nothing was imported.")
        for (s in sessions) {
            if (s.gymID.isEmpty() || s.feeling !in 0..5) throw invalid
            if (s.end != null && s.end < s.start) throw invalid
            if (s.price != null && (!s.price.isFinite() || s.price < 0)) throw invalid
            for (c in s.climbs) {
                val d = Discipline.of(c.disciplineRaw) ?: throw invalid
                if (!(c.grade in d.grades.indices || (c.isComp == true && c.grade == Climb.UNGRADED))) throw invalid
                ClimbResult.of(c.resultRaw) ?: throw invalid
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

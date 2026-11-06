package io.github.tutu2652.climbingfinder.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogbookTest {
    // Shaped like a backup from the iPhone app: sorted keys, upper-case UUIDs,
    // whole-second ISO dates, nil fields left out. Version 2 has no isComp.
    private val iosV2 = """
    {
      "exported" : "2026-10-01T18:00:00Z",
      "sessions" : [
        {
          "climbs" : [
            { "discipline" : "boulder", "grade" : 4, "id" : "6F1C2A9E-0B7D-4C1E-9A5B-1D2E3F405162",
              "loggedAt" : "2026-09-30T18:20:00Z", "result" : "flash", "routePhotoAssetID" : "ABC/L0/001" },
            { "discipline" : "rope", "grade" : 6, "id" : "7A2D3B0F-1C8E-4D2F-8B6C-2E3F40516273",
              "loggedAt" : "2026-09-30T18:40:00Z", "result" : "attempt" }
          ],
          "end" : "2026-09-30T20:00:00Z", "feeling" : 4, "gymID" : "castle",
          "id" : "11111111-2222-3333-4444-555555555555", "notes" : "Good one", "price" : 18,
          "start" : "2026-09-30T18:00:00Z"
        },
        {
          "climbs" : [
            { "discipline" : "boulder", "grade" : 2, "id" : "8B3E4C10-2D9F-4E30-9C7D-3F4051627384",
              "loggedAt" : "2026-10-01T09:30:00Z", "result" : "send" }
          ],
          "feeling" : 0, "gymID" : "mileend", "id" : "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "notes" : "",
          "start" : "2026-10-01T09:00:00Z"
        }
      ],
      "version" : 2
    }
    """.trimIndent()

    @Test fun readsIosBackup() {
        val b = LogbookBackup.decode(iosV2)
        val added = b.newSessions(emptyList())
        assertEquals(2, added.size)
        val first = added[0]
        assertEquals(18.0, first.price)
        assertEquals(ClimbResult.FLASH, first.climbs[0].result)
        assertEquals("V4", first.climbs[0].label)
        assertEquals("6b", first.climbs[1].gradeLabel)
        assertEquals("ABC/L0/001", first.climbs[0].routePhotoAssetID)
        assertFalse(first.climbs[0].comp)
        // A live session comes back finished at its last climb, and ids are upper case.
        assertEquals(Instant.parse("2026-10-01T09:30:00Z"), added[1].end)
        assertEquals("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", added[1].id)
        assertEquals(null, added[1].price)
    }

    @Test fun skipsSessionsAlreadyThere() {
        val b = LogbookBackup.decode(iosV2)
        val once = b.newSessions(emptyList())
        assertEquals(0, b.newSessions(once).size)
    }

    @Test fun roundTripKeepsEverything() {
        val sessions = LogbookBackup.decode(iosV2).newSessions(emptyList())
        val text = LogbookBackup(sessions = sessions).encode()
        assertTrue("\"version\": 4" in text)
        assertTrue("colour" !in text, "V-graded climbs and no scales write no colour fields")
        assertTrue(".000" !in text && "\"end\": null" !in text)
        assertEquals(sessions, LogbookBackup.decode(text).sessions)
    }

    @Test fun ungradedCompClimbIsValidOnlyAsComp() {
        val comp = Climb.of(Discipline.BOULDER, Climb.UNGRADED, ClimbResult.SEND, Instant.parse("2026-10-01T10:00:00Z"), comp = true)
        val s = ClimbSession(gymID = "castle", start = Instant.parse("2026-10-01T09:00:00Z"), climbs = listOf(comp))
        LogbookBackup(sessions = listOf(s)).validate()
        assertEquals("Comp", comp.label)
        assertEquals(null, s.hardest(Discipline.BOULDER))
        val bad = s.copy(climbs = listOf(comp.copy(isComp = false)))
        assertFailsWith<RestoreError> { LogbookBackup(sessions = listOf(bad)).validate() }
    }

    @Test fun rejectsBadFiles() {
        val s = ClimbSession(gymID = "castle", start = Instant.parse("2026-10-01T09:00:00Z"))
        assertFailsWith<RestoreError> { LogbookBackup(version = 9, sessions = listOf(s)).validate() }
        assertFailsWith<RestoreError> {
            LogbookBackup(sessions = listOf(s.copy(end = Instant.parse("2026-10-01T08:00:00Z")))).validate()
        }
        assertFailsWith<RestoreError> { LogbookBackup(sessions = listOf(s.copy(price = -1.0))).validate() }
    }

    @Test fun durations() {
        assertEquals("45m", formatDuration(45 * 60))
        assertEquals("2h 05m", formatDuration(125 * 60))
    }

    // Shaped like a version 4 backup from the iPhone app, with colour climbs and a scale.
    private val iosV4 = """
    {
      "colourScales" : { "sen" : [ { "high" : 3, "low" : 2, "name" : "Blue" }, { "high" : 6, "low" : 4, "name" : "Red" } ] },
      "exported" : "2026-11-06T18:00:00Z",
      "sessions" : [
        {
          "climbs" : [
            { "colour" : "Blue", "colourHigh" : 3, "colourLow" : 2, "discipline" : "boulder", "grade" : 2,
              "id" : "9C4F5D21-3EA0-4F41-AD8E-405162738495", "isComp" : false, "loggedAt" : "2026-11-06T18:20:00Z", "result" : "flash" },
            { "colour" : "Red", "colourHigh" : 6, "colourLow" : 4, "discipline" : "boulder", "grade" : 5,
              "id" : "AD5061E2-4FB1-4052-BE9F-5162738495A6", "isComp" : false, "loggedAt" : "2026-11-06T18:40:00Z", "result" : "send" }
          ],
          "end" : "2026-11-06T20:00:00Z", "feeling" : 0, "gymID" : "sen",
          "id" : "22222222-3333-4444-5555-666666666666", "notes" : "", "start" : "2026-11-06T18:00:00Z"
        }
      ],
      "version" : 4
    }
    """.trimIndent()

    @Test fun readsIosColourBackup() {
        val b = LogbookBackup.decode(iosV4)
        val s = b.newSessions(emptyList()).single()
        val (blue, red) = s.climbs
        assertEquals("Blue", blue.label)
        assertEquals(ColourGrade("Blue", 2, 3), blue.colourGrade)
        assertEquals("V2–V3", blue.colourDetail)
        assertFalse(blue.hasFeltGrade)
        assertTrue(red.hasFeltGrade)
        assertEquals("V4–V6 · felt V5", red.colourDetail)
        assertEquals(5, s.hardest(Discipline.BOULDER), "A felt grade counts; otherwise the bottom of the range")
        assertEquals(listOf("Blue", "Red"), b.colourScales!!["sen"]!!.map { it.name })
        // Round trip keeps colours and scales.
        val again = LogbookBackup.decode(LogbookBackup(sessions = listOf(s), colourScales = b.colourScales).encode())
        assertEquals(listOf(s), again.sessions)
        assertEquals(b.colourScales, again.colourScales)
    }

    @Test fun colourClimbsAndScales() {
        val blue = ColourGrade("Blue", 2, 3)
        val climb = Climb.of(blue, ClimbResult.SEND, Instant.parse("2026-11-06T18:00:00Z"))
        assertEquals(2, climb.grade)
        assertEquals("V7", ColourGrade("Black", 7, 7).range)
        assertTrue(ColourGrade.isValidScale(listOf(blue, ColourGrade("Red", 4, 6))))
        assertFalse(ColourGrade.isValidScale(listOf(blue, blue)))
        assertFalse(ColourGrade.isValidScale(listOf(ColourGrade("Green", 3, 1))))
        assertEquals(0 to 1, ColourGrade.nextRange(emptyList()))
        assertEquals(7 to 8, ColourGrade.nextRange(listOf(blue, ColourGrade("Red", 4, 6))))
        assertEquals(17 to 17, ColourGrade.nextRange(listOf(ColourGrade("Black", 16, 17))))
        // Restoring keeps a scale you already have and adds the rest.
        val file = LogbookBackup(sessions = emptyList(), colourScales = mapOf("a" to listOf(blue), "b" to listOf(blue)))
        val mine = mapOf("a" to listOf(ColourGrade("Red", 4, 6)))
        assertEquals(mapOf("a" to mine["a"]!!, "b" to listOf(blue)), file.mergedColourScales(mine))

        val s = ClimbSession(gymID = "sen", start = Instant.parse("2026-11-06T17:00:00Z"), climbs = listOf(climb))
        LogbookBackup(sessions = listOf(s)).validate()
        for (bad in listOf(climb.copy(colourHigh = null), climb.copy(colourLow = 9), climb.copy(disciplineRaw = "rope"))) {
            assertFailsWith<RestoreError> { LogbookBackup(sessions = listOf(s.copy(climbs = listOf(bad)))).validate() }
        }
        assertFailsWith<RestoreError> {
            LogbookBackup(sessions = listOf(s), colourScales = mapOf("sen" to listOf(blue, blue))).validate()
        }
    }

    @Test fun rankedColoursAndChartRanges() {
        assertEquals(0 to 0, ColourGrade.parseRange("VB"), "VB counts as V0")
        assertEquals(3 to 3, ColourGrade.parseRange("V3"))
        assertEquals(2 to 5, ColourGrade.parseRange("V2-V5"))
        assertEquals(9 to 17, ColourGrade.parseRange("V9+"))
        for (bad in listOf("V5-V2", "6A", "V99")) assertEquals(null, ColourGrade.parseRange(bad))
        assertEquals("V9+", gradeRange(9, 17))
        assertEquals("V17", gradeRange(17, 17))

        val yellow = ColourGrade("Yellow")
        assertTrue(yellow.isValid && !yellow.hasRange && yellow.range == "")
        assertFalse(ColourGrade("Pink", 2, null).isValid, "A range has both ends or neither")
        val climb = Climb.of(yellow, ClimbResult.SEND, Instant.parse("2026-11-06T18:00:00Z"))
        assertEquals(Climb.UNGRADED, climb.grade)
        assertFalse(climb.hasFeltGrade)
        assertEquals(null, climb.colourDetail)
        val s = ClimbSession(gymID = "sen", start = Instant.parse("2026-11-06T17:00:00Z"), climbs = listOf(climb))
        assertEquals(1, s.sends.size)
        assertEquals(null, s.hardest(Discipline.BOULDER), "Counts as a send, not in grade stats")
        val felt = climb.copy(grade = 4)
        assertTrue(felt.hasFeltGrade)
        assertEquals("felt V4", felt.colourDetail)

        val file = LogbookBackup(sessions = listOf(s), colourScales = mapOf("sen" to listOf(yellow), "cleared" to emptyList()))
        file.validate()
        val again = LogbookBackup.decode(file.encode())
        assertEquals(listOf(s), again.sessions)
        assertEquals(emptyList(), again.colourScales!!["cleared"], "Removing a gym's chart survives a backup")
        // Ungraded is only allowed for comp climbs and colours without a range.
        for (bad in listOf(climb.copy(colourLow = 1, colourHigh = 2), climb.copy(colour = null))) {
            assertFailsWith<RestoreError> { LogbookBackup(sessions = listOf(s.copy(climbs = listOf(bad)))).validate() }
        }
    }

    @Test fun tallyFollowsTheGymsColourOrder() {
        val at = Instant.parse("2026-11-06T18:00:00Z")
        fun order(cs: List<Climb>, scale: List<ColourGrade>) = tallyGroups(cs, scale).map { it[0].colour ?: it[0].label }
        // A gym that only ranks its colours (easiest first): hardest colour first,
        // not alphabetical, and a colour that's left the scale goes last.
        val ranked = listOf("White", "Red", "Blue", "Teal").map { ColourGrade(it) }
        val mixed = listOf(ranked[2] to ClimbResult.SEND, ranked[0] to ClimbResult.FLASH, ColourGrade("Grey") to ClimbResult.SEND,
            ranked[3] to ClimbResult.ATTEMPT, ranked[1] to ClimbResult.SEND, ranked[2] to ClimbResult.SEND).map { Climb.of(it.first, it.second, at) }
        assertEquals(listOf("Teal", "Blue", "Red", "White", "Grey"), order(mixed, ranked))
        assertEquals(2, tallyGroups(mixed, ranked)[1].size, "Same colour is one row")
        // Ranges still come first; the scale only breaks ties (VB and V0–V1 both start at V0).
        val chart = listOf(ColourGrade("Black", 0, 0), ColourGrade("Pink", 0, 1), ColourGrade("Blue", 1, 3))
        val ranged = listOf(chart[0], chart[2], chart[1]).map { Climb.of(it, ClimbResult.SEND, at) } +
            Climb.of(Discipline.BOULDER, 2, ClimbResult.SEND, at, comp = false)
        assertEquals(listOf("V2", "Blue", "Pink", "Black"), order(ranged, chart))
    }

    @Test fun readsColourChartsFromGymData() {
        val gym = """{"id":"g","name":"G","brand":"B","type":"bouldering","zone":1,"lat":51.5,"lng":0.0,"payg":{},
            "colour_grades":[{"colour":"Black","grades":"VB"},{"colour":"Red","grades":"V9+"},{"colour":"White"},{"colour":"Odd","grades":"6A"}]}"""
        val data = ClimbingData.parse("""{"gyms":[$gym]}""")
        assertEquals(listOf(ColourGrade("Black", 0, 0), ColourGrade("Red", 9, 17), ColourGrade("White"), ColourGrade("Odd")),
            data.gyms.single().colourGrades)
    }

    @Test fun logOrderAndEditing() {
        val start = Instant.parse("2026-11-06T17:00:00Z")
        val end = start.plusSeconds(3600)
        // In a past session every tap used to land on the end time, so "last" was a coin toss.
        var past = ClimbSession(gymID = "g", start = start, end = end)
        for (g in listOf(3, 1, 2)) {
            past = past.copy(climbs = past.climbs + Climb.of(Discipline.BOULDER, g, ClimbResult.SEND, past.nextLogTime(end.plusSeconds(86_400)), false))
        }
        assertEquals(listOf(end, end.plusSeconds(1), end.plusSeconds(2)), past.climbs.map { it.loggedAt })
        assertEquals(2, past.lastClimb!!.grade, "Undo removes the last tap")
        // A live session: two taps in the same second still get an order.
        var live = ClimbSession(gymID = "g", start = start)
        live = live.copy(climbs = live.climbs + Climb.of(Discipline.BOULDER, 4, ClimbResult.SEND, live.nextLogTime(end), false))
        live = live.copy(climbs = live.climbs + Climb.of(Discipline.BOULDER, 5, ClimbResult.SEND, live.nextLogTime(end), false))
        assertEquals(5, live.lastClimb!!.grade)

        val blue = ColourGrade("Blue", 2, 3)
        var c = past.climbs[0].copy(isComp = true).withColour(blue)
        assertEquals(blue, c.colourGrade); assertEquals(2, c.grade); assertFalse(c.comp)
        c = c.copy(grade = 3).withColour(blue)
        assertEquals(3, c.grade, "Picking the same colour keeps the felt grade")
        c = c.withColour(null)
        assertEquals(null, c.colour); assertEquals(3, c.grade)
        c = c.withColour(ColourGrade("White")).withColour(null)
        assertEquals(0, c.grade, "A V-graded climb always has a grade")
        c = c.copy(grade = 16).withDiscipline(Discipline.ROPE)
        assertEquals("8a", c.gradeLabel)
        c = c.copy(grade = 20).withDiscipline(Discipline.BOULDER)
        assertEquals(17, c.grade, "Grades past the top clamp to it")
        c = c.withColour(blue).withDiscipline(Discipline.ROPE)
        assertEquals(null, c.colour, "Ropes have no colours")
        val comp = Climb.of(Discipline.BOULDER, Climb.UNGRADED, ClimbResult.SEND, start, true).withDiscipline(Discipline.ROPE)
        assertEquals(Climb.UNGRADED, comp.grade)
        LogbookBackup(sessions = listOf(past, live)).validate()
    }

    @Test fun feltGradeAtTheBottomOfTheRangeStillShows() {
        val start = Instant.parse("2026-11-06T17:00:00Z")
        val purple = ColourGrade("Purple", 3, 5)
        var c = Climb.of(purple, ClimbResult.SEND, start)
        assertEquals(null, c.feltGrade); assertEquals("V3–V5", c.colourDetail)
        c = c.withFeltGrade(3)
        assertEquals(3, c.feltGrade); assertEquals("V3–V5 · felt V3", c.colourDetail)
        assertEquals("V3", c.listGrade); assertTrue(c.listGradeIsFelt)
        assertEquals("V3–V5", c.withFeltGrade(null).listGrade)
        assertEquals("V4", Climb.of(Discipline.BOULDER, 4, ClimbResult.FLASH, start, false).listGrade)
        c = c.withFeltGrade(null)
        assertEquals(3, c.grade); assertFalse(c.hasFeltGrade)
        c = c.withFeltGrade(4).withColour(ColourGrade("White", 4, 6))
        assertFalse(c.hasFeltGrade, "A new colour starts from its own range")
        var ranked = Climb.of(ColourGrade("Yellow"), ClimbResult.SEND, start).withFeltGrade(2)
        assertEquals("felt V2", ranked.colourDetail)
        ranked = ranked.withFeltGrade(null)
        assertEquals(Climb.UNGRADED, ranked.grade); assertEquals(null, ranked.colourDetail)
        assertEquals(5, Climb.of(purple, ClimbResult.SEND, start).copy(grade = 5).feltGrade, "Old climbs: a moved grade is felt")

        val s = ClimbSession(gymID = "g", start = start, climbs = listOf(c.withFeltGrade(4), ranked))
        val text = LogbookBackup(sessions = listOf(s)).encode()
        assertEquals(1, Regex("feltGradeSet").findAll(text).count(), "Only written when set")
        assertEquals(listOf(s), LogbookBackup.decode(text).sessions)
        val bad = s.copy(climbs = listOf(Climb.of(Discipline.BOULDER, 2, ClimbResult.SEND, start, false).copy(feltGradeSet = true)))
        assertFailsWith<RestoreError> { LogbookBackup(sessions = listOf(bad)).validate() }
    }
}

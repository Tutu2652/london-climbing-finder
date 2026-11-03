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
        assertTrue("\"version\": 3" in text)
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
}

package io.github.tutu2652.climbingfinder.core

import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddedGymTest {
    private val tokyo = AddedGym(
        id = "added-1a2b3c4d", name = "B-Pump Ogikubo", area = "Tokyo", country = "Japan",
        lat = 35.7046, lng = 139.6201, timeZone = "Asia/Tokyo", type = "bouldering",
        adultPrice = 12.0, hours = mapOf("Mon" to listOf("13:00", "23:00")),
    )
    private val hackney = AddedGym(id = "added-99887766", name = "Pop-up wall", lat = 51.543, lng = -0.024)

    @Test fun becomesAGymThePricerCanUse() {
        val g = hackney.copy(adultPrice = 16.5, concessionPrice = 13.0).toGym()
        assertEquals("Added by you", g.brand)
        assertTrue(g.isAddedByYou)
        assertFalse(g.outsideLondon)
        assertNull(g.area)
        assertEquals(16.5, g.payg.adultPeak)
        assertEquals(13.0, g.payg.concessionPeak)
        assertEquals(16.5, Pricer().bestOnDay(g, Moment.now().at(0), false)?.price)
    }

    @Test fun abroad() {
        val g = tokyo.toGym()
        assertTrue(g.outsideLondon)
        assertEquals("Japan", g.zoneLabel)       // shown where a London gym shows its zone
        assertEquals("Japan", g.borough)         // so lists can search by country
        assertEquals(mapOf("Mon" to listOf("13:00", "23:00")), g.hours)
        assertEquals("Added by you", hackney.toGym().zoneLabel)
    }

    @Test fun openingHoursAreLocalTime() {
        // Monday 2026-11-23 05:00 in London is 14:00 in Tokyo: open there, though London would say not yet.
        val at = Instant.parse("2026-11-23T05:00:00Z")
        val local = tokyo.localMoment(at)
        assertEquals(Moment(2026, 11, 23, 14 * 60), local)
        assertEquals(true, tokyo.toGym().openState(local)?.open)
        assertEquals(false, tokyo.toGym().openState(Moment.of(at))?.open)
        // Without a time zone, London time.
        assertEquals(Moment.of(at), tokyo.copy(timeZone = null).localMoment(at))
    }

    @Test fun validation() {
        assertTrue(tokyo.isValid)
        assertTrue(hackney.isValid)              // a name and a pin are enough
        assertFalse(tokyo.copy(id = "castle").isValid)
        assertFalse(tokyo.copy(name = " ").isValid)
        assertFalse(tokyo.copy(lat = Double.NaN).isValid)
        assertFalse(tokyo.copy(lng = 200.0).isValid)
        assertFalse(tokyo.copy(timeZone = "Mars/Olympus").isValid)
        assertFalse(tokyo.copy(type = "gym").isValid)
        assertFalse(tokyo.copy(adultPrice = -1.0).isValid)
        assertFalse(tokyo.copy(hours = mapOf("Mon" to listOf("22:00", "10:00"))).isValid)
        assertFalse(tokyo.copy(hours = mapOf("Monday" to listOf("10:00", "22:00"))).isValid)
    }

    @Test fun mergeKeepsListedGymsAndSkipsClashes() {
        val listed = ClimbingData.parse(File(System.getProperty("repoRoot"), "gyms.json").readText()).gyms
        // One with a listed gym's id (not an added- id) is left out, not swapped in.
        val merged = AddedGym.merge(listed, listOf(tokyo, hackney, tokyo.copy(id = listed.first().id)))
        assertEquals(listed.size + 2, merged.size)
        assertEquals(listed, merged.take(listed.size))
        assertTrue(listed.none { it.id.startsWith(AddedGym.ID_PREFIX) })
        val id = AddedGym.newId(merged.map { it.id }.toSet())
        assertTrue(id.startsWith(AddedGym.ID_PREFIX) && merged.none { it.id == id })
    }

    @Test fun backupRoundTripAndRestore() {
        val backup = LogbookBackup(sessions = emptyList(), addedGyms = listOf(tokyo, hackney))
        val back = LogbookBackup.decode(backup.encode())
        assertEquals(listOf(tokyo, hackney), back.addedGyms)
        assertFalse("\"country\"" in LogbookBackup(sessions = emptyList(), addedGyms = listOf(hackney)).encode())

        val edited = tokyo.copy(name = "B-Pump")
        val merged = AddedGym.mergeFromBackup(listOf(edited), back.addedGyms)
        assertEquals(listOf(edited, hackney), merged)   // yours are kept as they are

        val bad = LogbookBackup(sessions = emptyList(), addedGyms = listOf(tokyo.copy(lat = 999.0)))
        assertFailsWith<RestoreError> { bad.validate() }
    }

    @Test fun readsTheIPhoneAppsJSON() {
        // As the iPhone app writes it: nil fields left out.
        val text = """{"exported":"2026-11-26T10:00:00Z","sessions":[],"version":4,"addedGyms":[
            {"area":"Paris","country":"France","hours":{},"id":"added-0f0f0f0f","lat":48.8566,"lng":2.3522,
             "name":"Arkose","postcode":"75011","timeZone":"Europe/Paris","type":"bouldering"}]}"""
        val g = assertNotNull(LogbookBackup.decode(text).addedGyms?.single())
        assertTrue(g.isValid)
        assertEquals("Europe/Paris", g.zone?.id)
        assertTrue(g.toGym().outsideLondon)
    }
    @Test fun customGymGradesSurviveBackupsWithoutBecomingVGrades() {
        val banana = tokyo.copy(name = "香蕉攀岩", boulderGrading = GymGrading.BANANA)
        assertEquals((0..20).map(Int::toString), banana.alternateGrades(Discipline.BOULDER))
        val edited = banana.copy(boulderGrading = GymGrading.CUSTOM,
            boulderGradeLabels = GymGrading.parseLabels("入门，G1, G2"))
        assertTrue(edited.isValid)
        assertEquals(listOf("入门", "G1", "G2"), edited.alternateGrades(Discipline.BOULDER))
        val at = Instant.parse("2026-11-26T10:00:00Z")
        val grades = listOf("G1", "G2").map { Climb.of(Discipline.BOULDER, 1, ClimbResult.SEND, at, false, it) }
        val session = ClimbSession(gymID = edited.id, start = at, end = at, climbs = grades)
        val backup = LogbookBackup.decode(LogbookBackup(sessions = listOf(session), addedGyms = listOf(edited)).encode())
        assertEquals(5, backup.version)
        val restored = backup.newSessions(emptyList()).single()
        assertEquals(listOf("G1", "G2"), restored.climbs.map { it.gradeLabel })
        assertEquals(2, restored.sends.size)
        assertNull(restored.hardest(Discipline.BOULDER))
        assertEquals(2, tallyGroups(restored.climbs, emptyList()).size)
        assertEquals(edited, backup.addedGyms?.single())
    }

}

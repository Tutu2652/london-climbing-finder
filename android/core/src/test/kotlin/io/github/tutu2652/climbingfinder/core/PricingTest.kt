package io.github.tutu2652.climbingfinder.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Golden prices, the same cases as tests/test_pricing.py. The full comparison
// with the Python, JS and Swift copies is in that file.
class PricingTest {
    private val data = ClimbingData.parse(File(System.getProperty("repoRoot"), "gyms.json").readText())
    private val pricer = Pricer(setOf("2026-05-25", "2026-08-31", "2026-12-25", "2026-12-28"))
    private fun gym(id: String) = data.gyms.first { it.id == id }
    private fun price(id: String, iso: String, time: String, conc: Boolean = false): PriceResult {
        val p = iso.split("-").map { it.toInt() }
        return pricer.applicable(gym(id), Moment(p[0], p[1], p[2], toMin(time)), conc)
    }

    @Test fun castleAdultHasNoOffpeak() {
        val r = price("castle", "2026-07-08", "10:00")
        assertEquals(18.0 to "peak", r.price to r.label)
    }

    @Test fun castleConcessionOffpeak() {
        val r = price("castle", "2026-07-08", "10:00", conc = true)
        assertEquals(10.5 to "off-peak", r.price to r.label)
    }

    @Test fun mileEndSuperOffpeakAndBankHoliday() {
        assertEquals(5.0, price("mileend", "2026-07-08", "07:30").price)
        assertTrue(price("mileend", "2026-08-31", "07:30").price!! > 5.0)
    }

    // Wednesday 8 July 2026 at Climbing District Bermondsey: £8 SE16 Local Day all day,
    // £10 women+ Sister Sessions from 19:00, otherwise £15 peak.
    private fun bermondseyWed(time: String, e: Eligibility): Double? {
        val p = Pricer(eligibility = e)
        return p.applicable(gym("arch_bermondsey"), Moment(2026, 7, 8, toMin(time)), false).price
    }

    @Test fun restrictedSlotsOnlyPriceForClimbersWhoQualify() {
        assertEquals(15.0, bermondseyWed("12:00", Eligibility()))
        assertEquals(15.0, bermondseyWed("19:30", Eligibility()))
        assertEquals(10.0, bermondseyWed("19:30", Eligibility(women = true)))
        assertEquals(15.0, bermondseyWed("12:00", Eligibility(women = true)))
        assertEquals(8.0, bermondseyWed("12:00", Eligibility(postcode = "se16 2aa")))
        assertEquals(8.0, bermondseyWed("19:30", Eligibility(women = true, postcode = "SE16")))
        assertEquals(15.0, bermondseyWed("12:00", Eligibility(postcode = "SE1 7PB")))
        assertEquals(15.0, bermondseyWed("12:00", Eligibility(postcode = "SE1")))
    }

    @Test fun postcodeDistrict() {
        assertEquals("SE16", Eligibility(postcode = "se16 4dg").district)
        assertEquals("SE16", Eligibility(postcode = "SE16").district)
        assertEquals("SE16", Eligibility(postcode = "SE165DJ").district)
        assertEquals("SE16", Eligibility(postcode = "SE16 5DJ").district)
        assertEquals("E14", Eligibility(postcode = "E149ZZ").district)
        assertEquals("SW1A", Eligibility(postcode = "SW1A 1AA").district)
        assertEquals(null, Eligibility(postcode = " ").district)
    }

    @Test fun restrictedSlotsAreListedWithTheirCondition() {
        val lines = gym("arch_bermondsey").dealLines(false)
        assertTrue(lines.any { it.label == "Local Day" && "SE16 residents only" in it.extra })
        assertTrue(lines.any { it.label == "Sister Sessions" && "women+ only" in it.extra })
        assertEquals(null, gym("arch_bermondsey").bestOffpeak(false))   // no adult deal for everyone
        assertEquals(10.0, gym("arch_bermondsey").bestOffpeak(false, Eligibility(women = true)))
        assertEquals(8.0, gym("arch_bermondsey").bestOffpeak(false, Eligibility(postcode = "SE16")))
    }

    @Test fun eventsHideAfterTheirDay() {
        val e = GymEvent(date = "2026-11-14", start = "18:00", title = "Watch party")
        assertTrue(e.isUpcoming("2026-11-14"))
        assertFalse(e.isUpcoming("2026-11-15"))
        assertTrue(e.copy(endDate = "2026-11-16").isUpcoming("2026-11-15"))
        assertEquals("Sat 14 Nov · from 18:00", e.whenText)
    }

    @Test fun boardOnlySlotNeverPricesEntry() {
        val r = price("rise", "2026-07-10", "12:00")
        assertEquals(12.5, r.price)
        assertFalse("Board" in r.label)
    }

    @Test fun studentSaverIsConcessionOnly() {
        assertEquals(5.0, price("arch_bermondsey", "2026-07-06", "12:00", conc = true).price)
        assertTrue(price("arch_bermondsey", "2026-07-06", "12:00").price!! > 5.0)
    }

    @Test fun bestOnDayReportsWindow() {
        val b = pricer.bestOnDay(gym("mileend"), Moment(2026, 7, 8), conc = false)
        assertNotNull(b)
        assertEquals(5.0, b.price)
        assertTrue("07:00" in b.`when`)
    }

    @Test fun everyGymHasAPrice() {
        for (g in data.gyms) assertNotNull(pricer.bestOnDay(g, Moment(2026, 7, 11), conc = false), g.id)
    }

    @Test fun displayHelpers() {
        assertEquals("Mon-Fri", formatDays(listOf("Mon", "Tue", "Wed", "Thu", "Fri")))
        assertEquals("Mon,Fri", formatDays(listOf("Fri", "Mon")))
        assertEquals("£12.50", formatPrice(12.5))
        assertEquals("n/a", formatPrice(null))
        assertEquals(1, Moment(2026, 7, 7).weekdayIndex)
    }
}

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

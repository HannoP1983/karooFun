package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SensorFieldsTest {
    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun sunsetInMunichMatchesTheAlmanac() {
        // Midsummer sunset in Munich is about 21:17 local time, 19:17 UTC; midwinter about 16:22, 15:22 UTC
        val summer = Astronomy.nextCrossing(48.137, 11.575, utc(2026, 6, 21, 12), Sky.SUNSET_ELEVATION, rising = false)!!
        assertTrue(summer in utc(2026, 6, 21, 19, 5)..utc(2026, 6, 21, 19, 30))
        val winter = Astronomy.nextCrossing(48.137, 11.575, utc(2026, 12, 21, 12), Sky.SUNSET_ELEVATION, rising = false)!!
        assertTrue(winter in utc(2026, 12, 21, 15, 10)..utc(2026, 12, 21, 15, 35))
    }

    @Test
    fun civilDuskComesAfterSunsetAndSunriseAfterNight() {
        val noon = utc(2026, 11, 19, 11)
        val sunset = Astronomy.nextCrossing(48.137, 11.575, noon, Sky.SUNSET_ELEVATION, rising = false)!!
        val dusk = Astronomy.nextCrossing(48.137, 11.575, noon, Sky.CIVIL_DUSK, rising = false)!!
        assertTrue(dusk - sunset in 25 * 60_000L..50 * 60_000L)
        val sunrise = Astronomy.nextCrossing(48.137, 11.575, dusk, Sky.SUNSET_ELEVATION, rising = true)!!
        assertTrue(sunrise > dusk)
    }

    @Test
    fun noSunsetInPolarSummer() {
        assertNull(Astronomy.nextCrossing(78.2, 15.6, utc(2026, 6, 21, 12), Sky.SUNSET_ELEVATION, rising = false))
    }

    @Test
    fun powerZonesFromProfileOrFtp() {
        val fromFtp = Rider(ftp = 250)
        assertNull(Rider().powerZone(200.0))
        assertEquals(1, fromFtp.powerZone(100.0))
        assertEquals(4, fromFtp.powerZone(240.0))
        assertEquals(7, fromFtp.powerZone(500.0))
        val fromProfile = Rider(powerZoneStarts = listOf(0, 150, 200, 250, 300))
        assertEquals(3, fromProfile.powerZone(220.0))
        assertEquals(5, fromProfile.powerZone(900.0))
    }

    @Test
    fun percentOfFtp() {
        assertEquals(104, percentFtpOf(260.0, 250))
        assertNull(percentFtpOf(260.0, 0))
        assertNull(percentFtpOf(null, 250))
    }

    @Test
    fun drivetrainFollowsShiftingOrSettings() {
        val gears = Gears(front = 2, frontMax = 2, frontTeeth = 52, rear = 7, rearMax = 12, rearTeeth = 18)
        val reported = Drivetrain.of(gears, FunSettings(ownGears = false))
        assertEquals(2, reported.rings.size)
        assertEquals(12, reported.cogs.size)
        assertEquals(52, reported.rings[1])
        assertEquals(18, reported.cogs[6])
        val own = Drivetrain.of(gears, FunSettings(ownGears = true, frontGears = 1, rearGears = 14))
        assertEquals(1, own.rings.size)
        assertEquals(14, own.cogs.size)
        assertEquals(1, own.frontGear)
    }

    @Test
    fun crossChainingOnlyAtTheExtremes() {
        fun cross(front: Int, rear: Int, rings: Int = 2) =
            Drivetrain.of(Gears(front = front, frontMax = rings, rear = rear, rearMax = 12), FunSettings()).crossChained
        assertTrue(cross(2, 1))
        assertTrue(cross(2, 2))
        assertFalse(cross(2, 3))
        assertTrue(cross(1, 12))
        assertTrue(cross(1, 11))
        assertFalse(cross(1, 1))
        assertFalse(cross(1, 12, rings = 1))
    }

    @Test
    fun pretzelsAndCountdown() {
        assertEquals(0, Food.PRETZEL.count(240.0))
        assertEquals(4, Food.PRETZEL.count(1180.0))
        assertEquals("1:12", SensorFields.countdown(72 * 60_000L))
        assertEquals("0:38", SensorFields.countdown(38 * 60_000L + 30_000))
        assertEquals("0:00", SensorFields.countdown(-5_000))
    }

    @Test
    fun everyNewFieldHasItsOwnTypeId() {
        assertEquals(ComicField.entries.size, ComicField.entries.map { it.typeId }.toSet().size)
    }
}

package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldTest {
    @Test
    fun percentOfMaxIsRounded() {
        assertEquals(86, percentOfMax(151.0, 175))
        assertEquals(100, percentOfMax(175.0, 175))
    }

    @Test
    fun pedalingStepsAt70And100() {
        assertEquals(Pedaling.GRINDING, pedaling(69.9))
        assertEquals(Pedaling.ROUND, pedaling(70.0))
        assertEquals(Pedaling.ROUND, pedaling(99.0))
        assertEquals(Pedaling.SPINNING, pedaling(100.0))
    }

    @Test
    fun maxHrFollowsTheZonesInUse() {
        val rider = Rider(maxHr = 190)
        assertEquals(175, rider.maxHr(FunSettings(ownZones = true, maxHr = 175)))
        assertEquals(190, rider.maxHr(FunSettings(ownZones = false)))
        assertEquals(DEFAULT_MAX_HR, Rider().maxHr(FunSettings(ownZones = false)))
    }

    @Test
    fun zoneAndPercentNeedAHeartRate() {
        val settings = FunSettings()
        assertNull(ComicField.HR_ZONE.value(RideSnapshot(), Rider(), settings))
        assertNull(ComicField.PERCENT_MAX_HR.value(RideSnapshot(), Rider(), settings))
        assertEquals(4.0, ComicField.HR_ZONE.value(RideSnapshot(heartRate = 151.0), Rider(), settings)!!, 0.0)
    }

    @Test
    fun pageCellsShowTheirRideValue() {
        val s = RideSnapshot(speed = 7.0, distance = 41050.0, elevation = 812.0, heartRate = 151.0, temperature = 14.6)
        val values = listOf(ComicField.SPEED, ComicField.DISTANCE, ComicField.ELEVATION, ComicField.HEART_RATE, ComicField.TEMPERATURE)
            .map { it.value(s, Rider(), FunSettings()) }
        assertEquals(listOf(7.0, 41050.0, 812.0, 151.0, 14.6), values)
    }

    @Test
    fun everyFieldHasItsOwnTypeId() {
        assertEquals(ComicField.entries.size, ComicField.entries.map { it.typeId }.toSet().size)
    }
}

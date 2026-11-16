package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventDetectorTest {
    private var settings = FunSettings()
    private val detector = EventDetector { settings }

    private fun sample(seconds: Long, speed: Double?, average: Double? = 25.0, grade: Double? = 0.0, zone: Int? = 2) =
        TriggerSample(seconds * 1_000, speed, average, grade, zone)

    @Test
    fun `pokal fires on a fast descent`() {
        assertEquals(Takeover.POKAL, detector.onSample(sample(0, 64.0, grade = -6.0), visible = true))
    }

    @Test
    fun `pokal needs a real descent, not fast flat riding`() {
        assertNull(detector.onSample(sample(0, 64.0, grade = -1.0), visible = true))
        assertNull(detector.onSample(sample(1, 58.0, grade = -8.0), visible = true))
    }

    @Test
    fun `fire needs speed above average and red zone for the minimum time`() {
        for (s in 0L..9L) assertNull(detector.onSample(sample(s, 30.0, zone = 4), visible = true))
        assertEquals(Takeover.FIRE, detector.onSample(sample(10, 30.0, zone = 4), visible = true))
    }

    @Test
    fun `fire resets when the heart rate drops out of the zone`() {
        for (s in 0L..8L) detector.onSample(sample(s, 30.0, zone = 5), visible = true)
        detector.onSample(sample(9, 30.0, zone = 3), visible = true)
        assertNull(detector.onSample(sample(12, 30.0, zone = 5), visible = true))
    }

    @Test
    fun `party needs steady zones for twenty minutes and average pace`() {
        for (s in 0L until 20 * 60) assertNull(detector.onSample(sample(s, 25.5, zone = 3), visible = true))
        assertEquals(Takeover.PARTY, detector.onSample(sample(20 * 60L, 25.5, zone = 2), visible = true))
    }

    @Test
    fun `party does not fire off pace`() {
        for (s in 0L..20 * 60) assertNull(detector.onSample(sample(s, 22.0, zone = 2), visible = true))
    }

    @Test
    fun `cooldown blocks all takeovers`() {
        assertEquals(Takeover.POKAL, detector.onSample(sample(0, 65.0, grade = -7.0), visible = true))
        assertNull(detector.onSample(sample(24 * 60L, 65.0, grade = -7.0), visible = true))
        assertEquals(Takeover.POKAL, detector.onSample(sample(25 * 60L, 65.0, grade = -7.0), visible = true))
    }

    @Test
    fun `nothing fires while the page is not visible`() {
        assertNull(detector.onSample(sample(0, 65.0, grade = -7.0), visible = false))
        assertEquals(Takeover.POKAL, detector.onSample(sample(1, 65.0, grade = -7.0), visible = true))
    }

    @Test
    fun `disabled actions stay quiet`() {
        settings = settings.copy(pokalEnabled = false)
        assertNull(detector.onSample(sample(0, 65.0, grade = -7.0), visible = true))
    }

    @Test
    fun `heart rate zones from profile or max heart rate`() {
        assertEquals(3, heartRateZone(150.0, listOf(0, 115, 134, 153, 172), 190))
        assertEquals(5, heartRateZone(180.0, listOf(0, 115, 134, 153, 172), 190))
        assertEquals(4, heartRateZone(160.0, emptyList(), 190))
        assertEquals(1, heartRateZone(90.0, emptyList(), 190))
    }

    @Test
    fun `own zones from max heart rate`() {
        val zones = FunSettings().withMaxHr(175)
        assertEquals(listOf(0, 105, 123, 140, 158), zones.zoneStarts)
        assertEquals(3, Rider().zone(130.0, zones))
        assertEquals(5, Rider().zone(160.0, zones))
    }

    @Test
    fun `karoo profile zones when own zones are off`() {
        val rider = Rider(zoneStarts = listOf(0, 120, 140, 160, 180), maxHr = 200)
        assertEquals(2, rider.zone(130.0, FunSettings(ownZones = false)))
        assertEquals(3, rider.zone(130.0, FunSettings()))
    }
}

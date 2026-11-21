package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RadarTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private val munich = RadarWindow(48.137, 11.575, RadarZoom.REGION, 478, 494)

    @Test
    fun bboxIsCenteredAndScaledForLatitude() {
        // 25 km on the ground at 48° are about 37.5 km in Web Mercator
        val (minX, minY, maxX, maxY) = munich.bbox().split(",").map { it.toDouble() }
        assertEquals(37_462.0, maxX - minX, 50.0)
        assertEquals((maxX - minX) * 494 / 478, maxY - minY, 50.0)
        assertEquals(1_288_523.0, (minX + maxX) / 2, 50.0)
    }

    @Test
    fun baseMapHalvesSplitTheBboxVertically() {
        val (_, top0, _, top1) = munich.bbox(0.0, 0.5).split(",").map { it.toDouble() }
        val (_, bottom0, _, bottom1) = munich.bbox(0.5, 1.0).split(",").map { it.toDouble() }
        assertEquals(top0, bottom1, 0.5)
        assertTrue(top1 > top0 && bottom1 > bottom0)
    }

    @Test
    fun recentersAfterAQuarterOfTheWidth() {
        assertFalse(munich.needsRecenter(48.137, 11.62))
        assertTrue(munich.needsRecenter(48.137, 11.70))
    }

    @Test
    fun radarUrlUsesUtcTime() {
        val url = munich.radarUrl(at("2026-11-16T19:05:00Z"))
        assertTrue(url, url.endsWith("&time=2026-11-16T19:05:00Z"))
    }

    @Test
    fun radarIsRequestedAtFourPixelsPerKilometer() {
        assertTrue(munich.radarUrl(0).contains("&width=100&height=103&format=image/png8"))
    }

    @Test
    fun nearbyPositionsShareTheCachedMap() {
        val a = RadarWindow.around(48.1370, 11.5750, RadarZoom.REGION, 478, 494)
        val b = RadarWindow.around(48.1372, 11.5755, RadarZoom.REGION, 478, 494)
        assertEquals(a.cacheKey, b.cacheKey)
        assertFalse(a.needsRecenter(48.137, 11.575))
    }

    @Test
    fun zoomStopsAtTheEnds() {
        assertEquals(RadarZoom.CITY, RadarZoom.CITY.zoomIn())
        assertEquals(RadarZoom.OVERVIEW, RadarZoom.REGION.zoomOut())
        assertEquals(RadarZoom.OVERVIEW, RadarZoom.OVERVIEW.zoomOut())
    }

    @Test
    fun timelineWaitsForPublishedScans() {
        // 19:07:30 → the 19:00 scan, since 19:05 may not be published yet
        assertEquals(at("2026-11-16T19:00:00Z"), RadarTimeline.now(at("2026-11-16T19:07:30Z")))
        assertFalse(RadarTimeline.isForecast(RadarTimeline.NOW_INDEX))
    }

    @Test
    fun timelineHasTheHourInFiveMinuteSteps() {
        assertEquals(13, RadarTimeline.OFFSETS_MIN.size)
        assertEquals(at("2026-11-16T19:05:00Z"), RadarTimeline.time(at("2026-11-16T19:00:00Z"), 1))
        assertEquals(at("2026-11-16T20:00:00Z"), RadarTimeline.time(at("2026-11-16T19:00:00Z"), 12))
        assertTrue(RadarTimeline.isLabeled(2))
        assertFalse(RadarTimeline.isLabeled(3))
    }

    @Test
    fun animationRunsThroughAllFramesAndRestsOnNow() {
        assertEquals((0..12).toList(), (0..12).map { RadarTimeline.frameAt(it) })
        assertEquals(RadarTimeline.NOW_INDEX, RadarTimeline.frameAt(15))
        assertEquals(0, RadarTimeline.frameAt(16))
    }

    @Test
    fun tappingStepsThroughTheHourAndWraps() {
        assertEquals(1, RadarTimeline.next(0))
        assertEquals(RadarTimeline.NOW_INDEX, RadarTimeline.next(12))
    }

    private fun minutes(m: Int) = m * 60_000L

    private fun hour(vararg wet: Boolean?) = wet.toList() + List(13 - wet.size) { wet.last() }

    @Test
    fun outlookFindsTheChange() {
        assertEquals(RainOutlook.RainFrom(minutes(15)), RainOutlook.of(0, hour(false, false, false, true)))
        assertEquals(RainOutlook.DryFrom(minutes(60)), RainOutlook.of(0, List(12) { true } + false))
        assertEquals(RainOutlook.StaysWet(), RainOutlook.of(0, hour(true)))
        assertEquals(RainOutlook.StaysDry(), RainOutlook.of(0, hour(false)))
        assertEquals(RainOutlook.Unknown, RainOutlook.of(0, hour(null, true)))
    }

    @Test
    fun outlookOnlyReachesAsFarAsItKnows() {
        // +25 unknown (not loaded, or the way ahead leaves the map): dry is only sure up to +20
        assertEquals(RainOutlook.StaysDry(minutes(20)), RainOutlook.of(0, hour(false, false, false, false, false, null)))
        assertEquals(RainOutlook.Unknown, RainOutlook.of(0, hour(true, null)))
    }

    @Test
    fun movingRiderIsLookedForAhead() {
        // Heading east at 20 km/h on average: 10 km in 30 minutes
        val rider = RideSnapshot(latitude = 48.0, longitude = 11.0, bearing = 90.0, speed = 7.0, averageSpeed = 20 / 3.6)
        val (lat, lon) = RainOutlook.ahead(rider, 30)!!
        assertEquals(48.0, lat, 1e-6)
        assertEquals(10_000.0, (lon - 11.0) * 111_195 * Math.cos(Math.toRadians(48.0)), 20.0)
        assertEquals(48.0 to 11.0, RainOutlook.ahead(rider, 0))
    }

    @Test
    fun radarWaitsForARealFix() {
        assertFalse(RideSnapshot().hasFix)
        // Coarse position before the fix
        assertFalse(RideSnapshot(latitude = 48.0, longitude = 11.0, accuracy = 800.0).hasFix)
        assertTrue(RideSnapshot(latitude = 48.0, longitude = 11.0, accuracy = 8.0).hasFix)
        // Without an accuracy the position is taken as it is
        assertTrue(RideSnapshot(latitude = 48.0, longitude = 11.0).hasFix)
        val coarse = RideSnapshot(latitude = 48.0, longitude = 11.0, accuracy = 800.0)
        assertEquals(RainOutlook.Unknown, RainOutlook.at(RadarState(window = munich), coarse))
    }

    @Test
    fun standingRiderStaysPut() {
        val rider = RideSnapshot(latitude = 48.0, longitude = 11.0, bearing = 90.0, speed = 0.5, averageSpeed = 6.0)
        assertEquals(48.0 to 11.0, RainOutlook.ahead(rider, 30))
        assertNull(RainOutlook.ahead(RideSnapshot(), 30))
    }
}

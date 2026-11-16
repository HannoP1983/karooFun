package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(at("2026-11-16T19:10:00Z"), RadarTimeline.time(at("2026-11-16T19:00:00Z"), 1))
        assertFalse(RadarTimeline.isForecast(RadarTimeline.NOW_INDEX))
        assertEquals(RadarTimeline.NOW_INDEX, RadarTimeline.frameAt(9))
    }

    @Test
    fun tappingStepsThroughTheHourAndWraps() {
        assertEquals(1, RadarTimeline.next(0))
        assertEquals(RadarTimeline.NOW_INDEX, RadarTimeline.next(6))
    }

    @Test
    fun outlookFindsTheChange() {
        val now = 0L
        assertEquals(RainOutlook.RainFrom(20 * 60_000L), RainOutlook.of(now, listOf(false, false, true, true, null, null, null)))
        assertEquals(RainOutlook.DryFrom(60 * 60_000L), RainOutlook.of(now, listOf(true, true, true, true, true, true, false)))
        assertEquals(RainOutlook.StaysWet, RainOutlook.of(now, listOf(true, true, true, true, true, null, true)))
        assertEquals(RainOutlook.StaysDry, RainOutlook.of(now, listOf(false, false, false, false, false, false, false)))
        assertEquals(RainOutlook.Unknown, RainOutlook.of(now, listOf(null, true, true, true, true, true, true)))
    }
}

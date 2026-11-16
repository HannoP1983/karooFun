package de.karoofun.karoo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.tan

/** Map width of the radar tile; the scale bar shows a round part of it. */
enum class RadarZoom(val widthKm: Double, val scaleKm: Int) {
    CITY(10.0, 2), REGION(25.0, 5), OVERVIEW(50.0, 10);

    fun zoomIn() = entries.getOrElse(ordinal - 1) { this }

    fun zoomOut() = entries.getOrElse(ordinal + 1) { this }
}

/**
 * The map window: centered on [latitude]/[longitude], [zoom] wide, with the aspect ratio of the map area
 * in pixels. Requests go out in Web Mercator (EPSG:3857) so map and radar line up exactly.
 */
data class RadarWindow(val latitude: Double, val longitude: Double, val zoom: RadarZoom, val widthPx: Int, val heightPx: Int) {
    private val centerX = Math.toRadians(longitude) * EARTH_RADIUS
    private val centerY = ln(tan(PI / 4 + Math.toRadians(latitude) / 2)) * EARTH_RADIUS

    /** Mercator meters per ground meter grow towards the poles. */
    private val halfWidth = zoom.widthKm * 500 / cos(Math.toRadians(latitude))
    private val halfHeight = halfWidth * heightPx / widthPx

    fun bbox(top: Double = 0.0, bottom: Double = 1.0) = String.format(
        Locale.US, "%.0f,%.0f,%.0f,%.0f",
        centerX - halfWidth, centerY + halfHeight - 2 * halfHeight * bottom,
        centerX + halfWidth, centerY + halfHeight - 2 * halfHeight * top,
    )

    /** Pixel offset of a position from the map center, for drawing the rider after the map was loaded. */
    fun offsetOf(lat: Double, lon: Double): Pair<Float, Float> {
        val x = Math.toRadians(lon) * EARTH_RADIUS
        val y = ln(tan(PI / 4 + Math.toRadians(lat) / 2)) * EARTH_RADIUS
        return ((x - centerX) / (2 * halfWidth) * widthPx).toFloat() to ((centerY - y) / (2 * halfHeight) * heightPx).toFloat()
    }

    /** Recenter once the rider got a quarter of the map width away from the middle. */
    fun needsRecenter(lat: Double, lon: Double): Boolean {
        val (dx, dy) = offsetOf(lat, lon)
        return abs(dx) > widthPx / 4f || abs(dy) > heightPx / 4f
    }

    /** Base map in two halves: one JPEG of the whole area can exceed the Karoo's 100 KB response limit. */
    fun baseMapUrl(half: Int) = "https://sgx.geodatenzentrum.de/wms_basemapde?service=WMS&version=1.3.0&request=GetMap" +
        "&layers=de_basemapde_web_raster_grau&styles=&crs=EPSG:3857&bbox=${bbox(half * 0.5, half * 0.5 + 0.5)}" +
        "&width=$widthPx&height=${heightPx / 2}&format=image/png8"

    /**
     * Radar at four pixels per kilometer: the DWD measures in 1 km cells anyway, and a frame shrinks from
     * 10-15 KB to well under 1 KB, which matters a lot over the companion app's slow Bluetooth link.
     */
    fun radarUrl(time: Long): String {
        val width = (zoom.widthKm * RADAR_PX_PER_KM).roundToInt()
        val height = (width.toDouble() * heightPx / widthPx).roundToInt()
        return "https://maps.dwd.de/geoserver/dwd/wms?service=WMS&version=1.3.0&request=GetMap" +
            "&layers=dwd:Niederschlagsradar&styles=&crs=EPSG:3857&bbox=${bbox()}&width=$width&height=$height" +
            "&format=image/png8&transparent=true&time=${isoUtc(time)}"
    }

    /** File name for the base map cache; [around] snaps centers to a grid so it hits again on later rides. */
    val cacheKey get() = "${zoom.name}_${widthPx}x${heightPx}_${centerX.roundToLong()}_${centerY.roundToLong()}"

    companion object {
        private const val EARTH_RADIUS = 6378137.0
        private const val RADAR_PX_PER_KM = 4
        private const val GRID_STEPS = 8
        private const val GRID_LATITUDE = 51.0

        /** A window near the rider, centered on a grid of an eighth of the map width. */
        fun around(lat: Double, lon: Double, zoom: RadarZoom, widthPx: Int, heightPx: Int): RadarWindow {
            // Grid size from a fixed latitude in the middle of Germany, so the grid is the same everywhere
            val step = zoom.widthKm * 1000 / cos(Math.toRadians(GRID_LATITUDE)) / GRID_STEPS
            val x = Math.round(Math.toRadians(lon) * EARTH_RADIUS / step) * step
            val y = Math.round(ln(tan(PI / 4 + Math.toRadians(lat) / 2)) * EARTH_RADIUS / step) * step
            val snappedLon = Math.toDegrees(x / EARTH_RADIUS)
            val snappedLat = Math.toDegrees(2 * atan(exp(y / EARTH_RADIUS)) - PI / 2)
            return RadarWindow(snappedLat, snappedLon, zoom, widthPx, heightPx)
        }

        /** Rough DWD radar and basemap.de coverage: Germany and the border regions. */
        fun covers(lat: Double, lon: Double) = lat in 47.0..55.2 && lon in 5.6..15.3

        private fun isoUtc(time: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:00'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(time))
    }
}

/**
 * The radar animation: "now" and the DWD forecast for the next hour in 10 minute steps. The layer holds
 * both, analysis up to now and forecast for about two hours, in 5 minute steps.
 */
object RadarTimeline {
    val OFFSETS_MIN = intArrayOf(0, 10, 20, 30, 40, 50, 60)
    const val NOW_INDEX = 0
    private const val STEP_MS = 5 * 60_000L

    /** One pass through all frames, then a few seconds on "now". */
    private val SEQUENCE = intArrayOf(0, 1, 2, 3, 4, 5, 6, 0, 0, 0)

    /** Latest 5 minute slot that is surely published: the DWD needs a few minutes per scan. */
    fun now(millis: Long) = (millis - STEP_MS) / STEP_MS * STEP_MS

    fun time(now: Long, index: Int) = now + OFFSETS_MIN[index] * 60_000L

    fun frameAt(tick: Int) = SEQUENCE[tick % SEQUENCE.size]

    fun isForecast(index: Int) = OFFSETS_MIN[index] > 0

    /** The frame after [index], back to "now" after the last one. */
    fun next(index: Int) = (index + 1) % OFFSETS_MIN.size
}

/** What the bottom line tells the rider, from rain at the map center now and in the forecast frames. */
sealed class RainOutlook {
    data object Unknown : RainOutlook()
    data object StaysDry : RainOutlook()
    data object StaysWet : RainOutlook()
    data class RainFrom(val time: Long) : RainOutlook()
    data class DryFrom(val time: Long) : RainOutlook()

    companion object {
        /** [rain] per timeline index, null where the frame is missing. */
        fun of(now: Long, rain: List<Boolean?>): RainOutlook {
            val current = rain.getOrNull(RadarTimeline.NOW_INDEX) ?: return Unknown
            for (i in RadarTimeline.NOW_INDEX + 1 until rain.size) {
                val later = rain[i] ?: continue
                if (later != current) {
                    val time = RadarTimeline.time(now, i)
                    return if (later) RainFrom(time) else DryFrom(time)
                }
            }
            return if (current) StaysWet else StaysDry
        }
    }
}

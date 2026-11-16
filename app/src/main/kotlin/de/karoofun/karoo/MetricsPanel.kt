package de.karoofun.karoo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Localized field names and texts drawn into the page. */
data class DashboardTexts(
    val waiting: String,
    val speed: String,
    val temperature: String,
    val freezing: String,
    val hot: String,
    val heartRate: String,
    val distance: String,
    val profile: String,
    val zone: String,
)

enum class TempState { FREEZE, NORMAL, HOT }

fun tempState(celsius: Double?) = when {
    celsius == null -> TempState.NORMAL
    celsius < 0 -> TempState.FREEZE
    celsius > 25 -> TempState.HOT
    else -> TempState.NORMAL
}

fun isAboveAverage(s: RideSnapshot): Boolean? {
    val speed = s.speed ?: return null
    val average = s.averageSpeed?.takeIf { it > 0 } ?: return null
    return speed >= average
}

/**
 * The four metric cells: speed vs. average, temperature with freeze/furnace effects, heart rate zone
 * and distance.
 *
 * Built for reading at a glance on rough roads: numbers as big as the cell allows, decimals smaller,
 * the field name always top left, and comic effects only at the edges so they never cover a digit.
 * Numbers never move; only the decoration around them animates once per frame (one frame per second).
 */
class MetricsPanel(private val comic: Comic, private val texts: DashboardTexts, private val units: Units) {
    private val critters = Critters(comic)
    private val weatherArt = WeatherArt(comic)

    fun drawSpeed(c: Canvas, cell: RectF, s: RideSnapshot, frame: Int) = inCell(c, cell) { w, h ->
        val above = isAboveAverage(s)
        if (above == true) critters.bunny(c, w - 133, 2f, frame) else if (above == false) critters.snail(c, w - 129, 2f, frame)
        comic.fieldTag(c, texts.speed)

        val value = s.speed?.let { units.speed(it) } ?: DASH
        val size = fitBig(value, SPEED_SIZE, w - 20)
        val baseline = h * 0.64f
        comic.bigNumber(c, value, w / 2, baseline, size)

        val average = s.averageSpeed?.let { units.speed(it) } ?: return@inCell
        drawAveragePill(c, w / 2, baseline + 8, average, above != false)
    }

    /** Average speed in a big white pill, with an arrow telling above or below. */
    private fun drawAveragePill(c: Canvas, cx: Float, top: Float, average: String, up: Boolean) {
        val size = AVERAGE_SIZE
        val i = average.indexOfLast { it == ',' || it == '.' }
        val whole = "Ø " + if (i > 0) average.substring(0, i) else average
        val decimals = if (i > 0) average.substring(i) else ""
        val arrow = size * 0.62f
        val width = arrow + 8 + comic.measure(whole, size) + comic.measure(decimals, size * 0.65f) + 26
        val box = RectF(cx - width / 2, top, cx + width / 2, top + size + 8)
        comic.sticker(c, Path().apply { addRoundRect(box, box.height() / 2, box.height() / 2, Path.Direction.CW) }, Color.WHITE, 3f, shadow = 3f)
        val baseline = box.bottom - 9
        val ax = box.left + 13 + arrow / 2
        val ay = box.centerY()
        val r = arrow * 0.42f
        comic.fill.color = INK
        c.drawPath(Path().apply {
            if (up) { moveTo(ax, ay - r); lineTo(ax + r, ay + r * 0.8f); lineTo(ax - r, ay + r * 0.8f) }
            else { moveTo(ax, ay + r); lineTo(ax + r, ay - r * 0.8f); lineTo(ax - r, ay - r * 0.8f) }
            close()
        }, comic.fill)
        var x = box.left + 13 + arrow + 8
        x += comic.plain(c, whole, x, baseline, size, typeface = comic.numbers)
        comic.plain(c, decimals, x, baseline, size * 0.65f, typeface = comic.numbers)
    }

    /**
     * [sky] is null until the first GPS fix and [weather] without a recent forecast; the cell then falls
     * back to the clear daytime look.
     */
    fun drawTemperature(c: Canvas, cell: RectF, s: RideSnapshot, sky: Sky?, weather: Weather?, frame: Int) = inCell(c, cell) { w, h ->
        val state = tempState(s.temperature)
        val condition = weather?.condition ?: Condition.CLEAR
        val wind = weather?.takeIf { it.windKmh >= Weather.MIN_WIND_KMH }
        val night = sky?.takeIf { it.dark && !condition.grey }
        when {
            state == TempState.FREEZE -> {
                if (night != null) drawNight(c, w, h, night, frame, moonY = 50f, moonRadius = 16f)
                if (condition.grey) bank(c, w, sky, condition)
                drawFrost(c, w, h, frame)
                when (condition) {
                    Condition.SNOW -> weatherArt.snow(c, w, h, frame)
                    Condition.RAIN, Condition.STORM -> weatherArt.rain(c, w, h, frame, rainColor(sky), heavy = condition == Condition.STORM)
                    else -> Unit
                }
            }
            // Rain puts the fire out
            state == TempState.HOT && !condition.wet -> {
                if (night != null) drawNight(c, w, h, night, frame, moonY = 32f, moonRadius = 18f)
                drawFurnace(c, w, h, frame, heatWaves = night == null && !condition.grey)
            }
            else -> drawWeather(c, w, h, sky, condition, frame, bottomCloud = wind == null)
        }
        if (wind != null) weatherArt.windsock(c, w, h, wind.windKmh, wind.windSide(s.bearing), frame)
        val tag = when (state) {
            TempState.FREEZE -> texts.freezing
            TempState.HOT -> texts.hot
            TempState.NORMAL -> texts.temperature
        }
        comic.fieldTag(c, tag, if (state == TempState.FREEZE) 0xFFE6F6FF.toInt() else 0xFFFFF6D6.toInt())

        val value = s.temperature?.let { units.temperature(it) } ?: DASH
        val unit = "°"
        val size = fitBig(value, TEMP_SIZE, w - 20, unit)
        val color = when (state) {
            TempState.FREEZE -> 0xFFE9F8FF.toInt()
            TempState.HOT -> 0xFFFFF3B0.toInt()
            TempState.NORMAL -> Color.WHITE
        }
        val baseline = when (state) {
            TempState.FREEZE -> h * 0.7f
            TempState.HOT -> h * 0.66f
            TempState.NORMAL -> h * 0.66f
        }
        comic.bigNumber(c, value, w / 2, baseline, size, color, unit)
    }

    private fun drawFrost(c: Canvas, w: Float, h: Float, frame: Int) {
        // Icicles along the top, each long one dripping on its own beat
        val heights = listOf(16f, 26f, 12f, 30f, 18f, 14f, 27f, 17f, 11f, 24f, 15f, 22f)
        val step = w / heights.size
        heights.forEachIndexed { i, len ->
            val x = i * step
            val icicle = Path().apply {
                moveTo(x, -2f)
                lineTo(x + step, -2f)
                quadTo(x + step * 0.7f, len * 0.6f, x + step / 2, len)
                quadTo(x + step * 0.3f, len * 0.6f, x, -2f)
                close()
            }
            comic.sticker(c, icicle, 0xFFF4FBFF.toInt(), 2.2f)
            if (len > 25) {
                val dropY = len + 4 + ((frame + i) % 3) * 6
                val cx = x + step / 2
                comic.sticker(c, Path().apply {
                    moveTo(cx, dropY); quadTo(cx + 4, dropY + 6, cx, dropY + 8); quadTo(cx - 4, dropY + 6, cx, dropY)
                }, ICE, 1.5f)
            }
        }
        // Snowflakes drifting down along the left and right edge only
        for ((i, flake) in listOf(Triple(18f, 0.4f, 8f), Triple(w - 14, 0.3f, 7f), Triple(22f, 0.68f, 6f), Triple(w - 10, 0.6f, 5f)).withIndex()) {
            val (x, y0, r) = flake
            val y = h * (y0 + ((frame + i * 2) % 4) * 0.04f)
            comic.snowflake(c, x, y, r, angle = frame * 20f)
        }
        // Snow drift along the bottom
        comic.sticker(c, Path().apply {
            moveTo(-2f, h + 2); lineTo(-2f, h - 18)
            cubicTo(w * 0.09f, h - 28, w * 0.19f, h - 24, w * 0.27f, h - 16)
            cubicTo(w * 0.35f, h - 8, w * 0.43f, h - 28, w * 0.55f, h - 26)
            cubicTo(w * 0.66f, h - 24, w * 0.7f, h - 12, w * 0.8f, h - 16)
            cubicTo(w * 0.88f, h - 20, w * 0.94f, h - 30, w + 2, h - 24)
            lineTo(w + 2, h + 2); close()
        }, Color.WHITE, 3f)
    }

    private fun drawFurnace(c: Canvas, w: Float, h: Float, frame: Int, heatWaves: Boolean) {
        // Two flame layers under the number, flipped and stretched per frame so the fire flickers
        val height = h * 0.38f
        c.save()
        if (frame % 3 == 1) c.scale(-1f, 1f, w / 2, 0f)
        c.scale(1f, listOf(1f, 0.85f, 1.1f)[frame % 3], 0f, h)
        comic.sticker(c, flames(w, h, height, 11, 0f), 0xFFD9301C.toInt(), 3f)
        comic.sticker(c, flames(w, h, height * 0.5f, 9, w / 36), 0xFFFFD166.toInt(), 2.5f)
        c.restore()
        if (!heatWaves) return
        // Heat waves rising in the top right corner
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        val lift = if (frame % 2 == 0) 0f else -4f
        for (k in 0 until 3) {
            val x = w - 44 + k * 11
            val y = 46f + lift
            c.drawPath(Path().apply {
                moveTo(x, y); cubicTo(x - 4, y - 6, x + 4, y - 10, x, y - 16); cubicTo(x - 4, y - 22, x + 4, y - 26, x, y - 32)
            }, comic.stroke)
        }
    }

    private fun flames(w: Float, h: Float, height: Float, tongues: Int, offset: Float): Path {
        val step = w / tongues
        return Path().apply {
            moveTo(-4f, h + 4)
            lineTo(-4f, h - height * 0.3f)
            for (i in 0 until tongues) {
                val x = i * step + offset
                val tip = height * (0.7f + 0.3f * (((i * 7) % 5) / 4f))
                quadTo(x + step * 0.15f, h - height * 0.2f, x + step * 0.5f, h - tip)
                quadTo(x + step * 0.7f, h - height * 0.35f, x + step, h - height * 0.3f)
            }
            lineTo(w + 4, h + 4)
            close()
        }
    }

    /** Sun sinking with its real elevation, hills at sunset, moon and stars after dark. */
    /** Clear or cloudy sky with sun, moon and the weather on top. */
    private fun drawWeather(c: Canvas, w: Float, h: Float, sky: Sky?, condition: Condition, frame: Int, bottomCloud: Boolean) {
        val daylight = sky.daylight
        val (cloud, shade) = CLOUDS.getValue(daylight)
        when (condition) {
            Condition.CLEAR -> drawSky(c, w, h, sky, frame, bottomCloud)
            Condition.PARTLY -> {
                drawSky(c, w, h, sky, frame, bottomCloud)
                weatherArt.cloud(c, w - 92, 52f, cloud, 1.3f)
                weatherArt.cloud(c, w * 0.46f, 26f, cloud, 0.9f)
            }
            Condition.OVERCAST -> {
                if (daylight == Daylight.EVENING) drawHills(c, w, h, 0xFF8A5E7E.toInt(), 0xFF6E4A66.toInt())
                weatherArt.cloudBank(c, w, cloud, shade)
                if (bottomCloud) weatherArt.cloud(c, 8 + (frame % 6) * 5f, h - 8, cloud)
            }
            Condition.RAIN, Condition.STORM -> {
                val storm = condition == Condition.STORM
                weatherArt.rain(c, w, h, frame, rainColor(sky), heavy = storm)
                bank(c, w, sky, condition)
                if (storm && frame % 2 == 0) weatherArt.bolt(c, w)
            }
            Condition.SNOW -> {
                weatherArt.cloudBank(c, w, cloud, shade)
                weatherArt.snow(c, w, h, frame)
            }
            Condition.FOG -> {
                // Sun or moon only faintly through the fog
                c.saveLayerAlpha(0f, 0f, w, h, FOG_ALPHA)
                drawSky(c, w, h, sky, frame, bottomCloud = false, hills = false)
                c.restore()
                weatherArt.fog(c, w, h, frame)
            }
        }
    }

    private fun bank(c: Canvas, w: Float, sky: Sky?, condition: Condition) {
        val daylight = sky.daylight
        val (fill, shade) = when {
            condition == Condition.STORM -> 0xFF6B7180.toInt() to 0xFF4E5360.toInt()
            condition.wet && daylight == Daylight.NIGHT -> 0xFF5E6A8F.toInt() to 0xFF46506F.toInt()
            condition.wet -> 0xFFC7CFD8.toInt() to 0xFF9AA6B4.toInt()
            else -> CLOUDS.getValue(daylight)
        }
        weatherArt.cloudBank(c, w, fill, shade)
    }

    private fun rainColor(sky: Sky?) = if (sky.daylight == Daylight.NIGHT) 0xFF9CC4F0.toInt() else 0xFFDDF3FF.toInt()

    /** Sun sinking with its real elevation, hills at sunset, moon and stars after dark. */
    private fun drawSky(c: Canvas, w: Float, h: Float, sky: Sky?, frame: Int, bottomCloud: Boolean, hills: Boolean = true) {
        val day = { sun: () -> Unit ->
            sun()
            if (bottomCloud) weatherArt.cloud(c, 8 + (frame % 6) * 5f, h - 8, Color.WHITE)
        }
        if (sky == null) return day { drawSun(c, w - 32, 30f, 25f, 46f, 0xFFFF9F1C.toInt(), frame) }
        when (sky.phase) {
            SkyPhase.DAY -> day { drawSun(c, w - 32, 30f, 25f, 46f, 0xFFFF9F1C.toInt(), frame) }
            SkyPhase.LOW_SUN -> day {
                // From the corner at 20° down towards the right edge at golden hour
                val t = ((Sky.HIGH_SUN - sky.sunElevation) / (Sky.HIGH_SUN - Sky.GOLDEN_HOUR)).toFloat().coerceIn(0f, 1f)
                drawSun(c, w - 32 + 6 * t, 30 + 28 * t, 25f + t, 46 - 4 * t, 0xFFFF8A1C.toInt(), frame)
            }
            SkyPhase.GOLDEN -> {
                drawSun(c, w - 34, h - 30, 30f, 38f, 0xFFFF4E1F.toInt(), frame)
                if (hills) drawHills(c, w, h, 0xFFB4507A.toInt(), 0xFF8A3E6B.toInt())
            }
            SkyPhase.DUSK -> {
                drawStars(c, w, h, frame, count = 3)
                if (sky.moonVisible) drawMoon(c, w - 34, 32f, 18f, sky)
                if (hills) drawHills(c, w, h, 0xFF4B336E.toInt(), 0xFF2E2350.toInt())
            }
            SkyPhase.NIGHT -> {
                drawNight(c, w, h, sky, frame, moonY = 32f, moonRadius = 21f)
                if (bottomCloud) weatherArt.cloud(c, 10 + (frame % 6) * 4f, h - 8, 0xFF8592BA.toInt())
            }
        }
    }

    private fun drawNight(c: Canvas, w: Float, h: Float, sky: Sky, frame: Int, moonY: Float, moonRadius: Float) {
        drawStars(c, w, h, frame)
        if (sky.moonVisible) drawMoon(c, w - 30 - (moonRadius - 16), moonY, moonRadius, sky)
    }

    private fun drawSun(c: Canvas, cx: Float, cy: Float, r: Float, rays: Float, color: Int, frame: Int) {
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3.5f
        for (k in 0 until 10) {
            val a = k * Math.PI / 5 + frame * Math.PI / 20
            c.drawLine(cx, cy, cx + rays * cos(a).toFloat(), cy + rays * sin(a).toFloat(), comic.stroke)
        }
        comic.sticker(c, Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }, color, 3.5f)
    }

    private fun drawHills(c: Canvas, w: Float, h: Float, back: Int, front: Int) {
        val k = w / DESIGN_WIDTH
        comic.sticker(c, Path().apply {
            moveTo(-2f, h + 2); lineTo(-2f, h - 26)
            cubicTo(30 * k, h - 40, 60 * k, h - 38, 95 * k, h - 24)
            cubicTo(130 * k, h - 12, 170 * k, h - 44, w + 2, h - 30)
            lineTo(w + 2, h + 2); close()
        }, back, 3f)
        comic.sticker(c, Path().apply {
            moveTo(-2f, h + 2); lineTo(-2f, h - 12)
            cubicTo(40 * k, h - 24, 80 * k, h - 6, 120 * k, h - 14)
            cubicTo(160 * k, h - 22, 200 * k, h - 10, w + 2, h - 16)
            lineTo(w + 2, h + 2); close()
        }, front, 3f)
    }

    /** Little four-pointed stars around the number; every third one twinkles bigger and warmer each frame. */
    private fun drawStars(c: Canvas, w: Float, h: Float, frame: Int, count: Int = STARS.size) {
        val k = w / DESIGN_WIDTH
        val kh = h / DESIGN_HEIGHT
        for (i in 0 until count) {
            val (x, y, size) = STARS[i]
            val on = (i + frame) % 3 == 0
            val r = if (on) size * 1.4f else size
            val star = Path()
            for (p in 0 until 8) {
                val a = p * Math.PI / 4 - Math.PI / 2
                val rr = if (p % 2 == 1) r * 0.4f else r
                val px = x * k + rr * cos(a).toFloat()
                val py = y * kh + rr * sin(a).toFloat()
                if (p == 0) star.moveTo(px, py) else star.lineTo(px, py)
            }
            star.close()
            comic.sticker(c, star, if (on) 0xFFFFF6C9.toInt() else 0xFFC9D3F2.toInt(), 1.8f)
        }
    }

    /**
     * Moon in its real phase: lit on the right while waxing in the northern hemisphere, mirrored when
     * waning or south of the equator. The terminator is a half ellipse whose width follows the phase.
     */
    private fun drawMoon(c: Canvas, cx: Float, cy: Float, r: Float, sky: Sky) {
        val disc = RectF(cx - r, cy - r, cx + r, cy + r)
        comic.fill.color = INK
        c.drawCircle(cx + 3, cy + 3, r, comic.fill)
        comic.fill.color = 0xFF55618A.toInt()
        c.drawCircle(cx, cy, r, comic.fill)

        val waxing = sky.moonPhase < 0.5
        val k = cos(2 * Math.PI * sky.moonPhase).toFloat()
        val terminator = RectF(cx - abs(k) * r, cy - r, cx + abs(k) * r, cy + r)
        val lit = Path().apply {
            arcTo(disc, -90f, 180f, true)
            arcTo(terminator, 90f, if (k > 0) -180f else 180f)
            close()
        }
        c.save()
        if (waxing == sky.southern) c.scale(-1f, 1f, cx, cy)
        comic.fill.color = 0xFFFFF4C2.toInt()
        c.drawPath(lit, comic.fill)
        comic.fill.color = 0x14000000
        c.drawCircle(cx + r * 0.3f, cy - r * 0.3f, r * 0.15f, comic.fill)
        c.drawCircle(cx + r * 0.5f, cy + r * 0.35f, r * 0.11f, comic.fill)
        c.restore()
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawCircle(cx, cy, r, comic.stroke)
    }

    fun drawHeartRate(c: Canvas, cell: RectF, s: RideSnapshot, zone: Int, frame: Int) = inCell(c, cell) { w, h ->
        val hasHr = s.heartRate != null
        val beat = frame % 2 == 0
        // The heart beats in the top right corner, harder in the high zones
        val scale = if (!hasHr) 1f else if (beat) (if (zone >= 4) 1.3f else 1.15f) else 0.95f
        val hx = w - 26
        val hy = 22f
        c.save()
        c.rotate(if (zone >= 5 && hasHr && beat) -14f else -8f, hx, hy)
        comic.sticker(c, comic.heart(hx, hy, 32f * scale), Color.WHITE, 3f)
        c.restore()
        if (hasHr && beat && zone >= 3) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 2.5f
            c.drawLine(hx - 26, hy - 8, hx - 33, hy - 13, comic.stroke)
            c.drawLine(hx - 26, hy + 6, hx - 34, hy + 7, comic.stroke)
        }
        comic.fieldTag(c, if (hasHr) "${texts.heartRate} · ${texts.zone}$zone" else texts.heartRate)

        val value = s.heartRate?.roundToInt()?.toString() ?: DASH
        comic.bigNumber(c, value, w / 2, h * 0.76f, fitBig(value, HR_SIZE, w - 20))
    }

    fun drawDistance(c: Canvas, cell: RectF, s: RideSnapshot, frame: Int) = inCell(c, cell) { w, h ->
        // A small bike rolling along the bottom edge towards the next 10 km mark
        val roadY = h - 7
        comic.stroke.color = 0x991F2328.toInt()
        comic.stroke.strokeWidth = 3f
        comic.stroke.strokeCap = Paint.Cap.BUTT
        var dash = -((frame * 10) % 24).toFloat()
        while (dash < w) {
            c.drawLine(dash, roadY, dash + 14, roadY, comic.stroke)
            dash += 24
        }
        comic.stroke.strokeCap = Paint.Cap.ROUND
        val progress = s.distance?.let { (units.distanceValue(it) % 10 / 10).toFloat() } ?: 0f
        c.save()
        c.translate(8 + progress * (w - 70), roadY - 25)
        c.scale(0.78f, 0.78f)
        comic.bike(c, frame)
        c.restore()

        comic.fieldTag(c, texts.distance)
        val value = s.distance?.let { units.distance(it) } ?: DASH
        comic.bigNumber(c, value, w / 2, h * 0.7f, fitBig(value, DISTANCE_SIZE, w - 20))
    }

    private inline fun inCell(c: Canvas, cell: RectF, draw: (Float, Float) -> Unit) {
        c.save()
        c.clipRect(cell)
        c.translate(cell.left, cell.top)
        draw(cell.width(), cell.height())
        c.restore()
    }

    private fun fitBig(value: String, maxSize: Float, available: Float, unit: String = ""): Float {
        var size = maxSize
        while (size > MIN_VALUE && comic.bigNumberWidth(value, size, unit) > available) size -= 2f
        return min(size, maxSize)
    }

    private companion object {
        const val DASH = "–"
        const val SPEED_SIZE = 90f
        const val AVERAGE_SIZE = 32f
        const val TEMP_SIZE = 96f
        const val HR_SIZE = 112f
        const val DISTANCE_SIZE = 98f
        const val MIN_VALUE = 40f

        const val FOG_ALPHA = 140

        /** Cloud fill and shade line by time of day. */
        val CLOUDS = mapOf(
            Daylight.DAY to (Color.WHITE to 0xFFDCE3EA.toInt()),
            Daylight.EVENING to (0xFFF4D3D9.toInt() to 0xFFD6A8B6.toInt()),
            Daylight.NIGHT to (0xFF8592BA.toInt() to 0xFF5E6A8F.toInt()),
        )

        const val DESIGN_WIDTH = 239f
        const val DESIGN_HEIGHT = 160f

        /** Stars as (x, y, size) in a 239 × 160 cell, scaled to the real cell. */
        val STARS = listOf(
            Triple(100f, 14f, 5f), Triple(138f, 30f, 4f), Triple(165f, 10f, 3.5f), Triple(20f, 128f, 4f),
            Triple(214f, 132f, 3.5f), Triple(120f, 150f, 3f), Triple(66f, 146f, 3.5f),
        )
    }
}

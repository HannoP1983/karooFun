package de.karoofun.karoo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The single comic fields, each its own data type. Sized for a 2 × 4 page, but they scale to any cell. */
enum class ComicField(val typeId: String, val nameRes: Int, val descriptionRes: Int, val labelRes: Int) {
    SPEED("fun-speed", R.string.field_now_speed_name, R.string.field_now_speed_description, R.string.field_speed),
    AVERAGE_SPEED("fun-avg-speed", R.string.field_avg_speed_name, R.string.field_avg_speed_description, R.string.field_avg_speed),
    MAX_SPEED("fun-max-speed", R.string.field_max_speed_name, R.string.field_max_speed_description, R.string.field_max_speed),
    DISTANCE("fun-distance", R.string.field_now_distance_name, R.string.field_now_distance_description, R.string.field_distance),
    ELEVATION("fun-elevation", R.string.field_now_profile_name, R.string.field_now_profile_description, R.string.field_profile_short),
    GRADE("fun-grade", R.string.field_grade_name, R.string.field_grade_description, R.string.field_grade),
    TO_SUMMIT("fun-to-summit", R.string.field_to_summit_name, R.string.field_to_summit_description, R.string.field_to_summit),
    ASCENT("fun-ascent", R.string.field_ascent_name, R.string.field_ascent_description, R.string.field_ascent),
    ALTITUDE("fun-altitude", R.string.field_altitude_name, R.string.field_altitude_description, R.string.field_altitude),
    UP_DOWN("fun-up-down", R.string.field_up_down_name, R.string.field_up_down_description, R.string.field_up_down),
    HEART_RATE("fun-heart-rate", R.string.field_now_hr_name, R.string.field_now_hr_description, R.string.field_heart_rate),
    AVERAGE_HR("fun-avg-hr", R.string.field_avg_hr_name, R.string.field_avg_hr_description, R.string.field_avg_hr),
    HR_ZONE("fun-hr-zone", R.string.field_hr_zone_name, R.string.field_hr_zone_description, R.string.field_hr_zone),
    MAX_HR("fun-max-hr", R.string.field_max_hr_name, R.string.field_max_hr_description, R.string.field_max_hr),
    PERCENT_MAX_HR("fun-percent-max-hr", R.string.field_percent_max_hr_name, R.string.field_percent_max_hr_description, R.string.field_percent_max_hr),
    CADENCE("fun-cadence", R.string.field_cadence_name, R.string.field_cadence_description, R.string.field_cadence),
    AVERAGE_CADENCE("fun-avg-cadence", R.string.field_avg_cadence_name, R.string.field_avg_cadence_description, R.string.field_avg_cadence),
    MAX_CADENCE("fun-max-cadence", R.string.field_max_cadence_name, R.string.field_max_cadence_description, R.string.field_max_cadence),
    POWER("fun-power", R.string.field_power_name, R.string.field_power_description, R.string.field_power),
    AVERAGE_POWER("fun-avg-power", R.string.field_avg_power_name, R.string.field_avg_power_description, R.string.field_avg_power),
    POWER_ZONE("fun-power-zone", R.string.field_power_zone_name, R.string.field_power_zone_description, R.string.field_power_zone),
    PERCENT_FTP("fun-percent-ftp", R.string.field_percent_ftp_name, R.string.field_percent_ftp_description, R.string.field_percent_ftp),
    GEAR("fun-gear", R.string.field_gear_name, R.string.field_gear_description, R.string.field_gear),
    CALORIES("fun-calories", R.string.field_calories_name, R.string.field_calories_description, R.string.field_calories),
    NEXT_TURN("fun-next-turn", R.string.field_next_turn_name, R.string.field_next_turn_description, R.string.field_next_turn),
    TEMPERATURE("fun-temperature", R.string.field_now_temperature_name, R.string.field_now_temperature_description, R.string.field_temperature),
    RAIN("fun-rain", R.string.field_rain_name, R.string.field_rain_description, R.string.field_rain),
    WIND("fun-wind", R.string.field_wind_name, R.string.field_wind_description, R.string.field_wind),
    LIGHTS_ON("fun-lights-on", R.string.field_lights_on_name, R.string.field_lights_on_description, R.string.field_lights_on),
    SUNSET("fun-sunset", R.string.field_sunset_name, R.string.field_sunset_description, R.string.field_sunset);

    /**
     * The field's number in base units (m/s, bpm, rpm, W, kcal, m, zone, percent, km/h wind, minutes for rain
     * and daylight countdowns), or null while there is none.
     */
    fun value(s: RideSnapshot, rider: Rider, settings: FunSettings, now: Long = System.currentTimeMillis()): Double? = when (this) {
        SPEED -> s.speed
        DISTANCE -> s.distance
        ELEVATION -> s.elevation
        HEART_RATE -> s.heartRate
        TEMPERATURE -> s.temperature
        AVERAGE_SPEED -> s.averageSpeed
        MAX_SPEED -> s.maxSpeed
        AVERAGE_HR -> s.averageHeartRate
        HR_ZONE -> rider.zone(s.heartRate, settings)?.toDouble()
        MAX_HR -> s.maxHeartRate
        PERCENT_MAX_HR -> s.heartRate?.let { percentOfMax(it, rider.maxHr(settings)).toDouble() }
        CADENCE -> s.cadence
        AVERAGE_CADENCE -> s.averageCadence
        MAX_CADENCE -> s.maxCadence
        GRADE -> s.grade
        TO_SUMMIT -> s.elevationToTop
        ASCENT -> s.ascent
        ALTITUDE -> s.elevation
        UP_DOWN -> s.ascent?.let { up -> s.descent?.let { up - it } }
        POWER -> s.power
        AVERAGE_POWER -> s.averagePower
        POWER_ZONE -> rider.powerZone(s.power)?.toDouble()
        PERCENT_FTP -> percentFtpOf(s.power, rider.ftp)?.toDouble()
        GEAR -> s.gears.rear?.toDouble()
        CALORIES -> s.calories
        NEXT_TURN -> s.distanceToNextTurn
        RAIN -> when (val outlook = SensorFields.rainOutlook(s)) {
            is RainOutlook.RainFrom -> SensorFields.minutesUntil(outlook.time, now).toDouble()
            is RainOutlook.DryFrom -> SensorFields.minutesUntil(outlook.time, now).toDouble()
            else -> null
        }
        WIND -> s.weather?.takeIf { it.freshAt(now) }?.windKmh
        LIGHTS_ON -> sunMinutes(s, now, Sky.CIVIL_DUSK)
        SUNSET -> sunMinutes(s, now, Sky.SUNSET_ELEVATION)
    }

    /** Minutes until the sun sinks below [elevation], null at night or without a GPS fix. */
    private fun sunMinutes(s: RideSnapshot, now: Long, elevation: Double): Double? {
        val lat = s.latitude ?: return null
        val lon = s.longitude ?: return null
        if (Astronomy.sunElevation(lat, lon, now) <= elevation) return null
        return Astronomy.nextCrossing(lat, lon, now, elevation, rising = false)?.let { SensorFields.minutesUntil(it, now).toDouble() }
    }
}

/** Heart rate as rounded percent of the max heart rate. */
fun percentOfMax(heartRate: Double, maxHr: Int) = (heartRate * 100 / max(1, maxHr)).roundToInt()

enum class Pedaling { GRINDING, ROUND, SPINNING }

/** Below 70 rpm the rider grinds, from 100 rpm on the legs spin. */
fun pedaling(rpm: Double) = when {
    rpm < GRINDING_BELOW -> Pedaling.GRINDING
    rpm >= SPINNING_FROM -> Pedaling.SPINNING
    else -> Pedaling.ROUND
}

private const val GRINDING_BELOW = 70
private const val SPINNING_FROM = 100

/**
 * Draws one comic field. The cells of the comic page (speed, temperature, heart rate, distance, profile) come
 * straight from [MetricsPanel] and [ProfilePanel]; the others are a colored panel with halftone, field name in a speech bubble, one big number and a
 * small animated sticker in the top right. Designed on a 239 × 160 cell (one eighth of a Karoo 3 page) and
 * scaled by height, so wider cells just get more room left and right.
 */
class FieldRenderer(private val fieldTexts: FieldTexts, texts: DashboardTexts, private val units: Units) {
    private val labels = fieldTexts.labels
    private val comic = Comic()
    private val sensors = SensorFields(comic, fieldTexts, units)
    private val critters = Critters(comic)
    private val metrics = MetricsPanel(comic, texts, units)
    private val profile = ProfilePanel(comic, texts, units)
    /** For single-width cells, where the full "elevation · ascent · grade" tag would be cut off. */
    private val narrowProfile = ProfilePanel(comic, texts.copy(profile = labels.getValue(ComicField.ELEVATION)), units)
    private val zoneShort = texts.zone

    /** [now] decides where sun and moon stand in the temperature field. */
    fun render(
        width: Int, height: Int, field: ComicField, s: RideSnapshot, rider: Rider, settings: FunSettings, frame: Int,
        now: Long = System.currentTimeMillis(), rain: RainOutlook? = if (field == ComicField.RAIN) SensorFields.rainOutlook(s) else null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        val k = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        c.scale(k, k)
        draw(c, width / k, height / k, field, s, rider, settings, frame, now, rain)
        return bitmap
    }

    private fun draw(c: Canvas, w: Float, h: Float, field: ComicField, s: RideSnapshot, rider: Rider, settings: FunSettings, frame: Int, now: Long, rain: RainOutlook?) {
        c.drawColor(INK)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), CORNER, CORNER, Path.Direction.CW) })
        val zone = rider.zone(s.heartRate, settings)
        val cell = RectF(0f, 0f, w, h)
        when (field) {
            ComicField.SPEED -> {
                background(c, w, h, DashboardRenderer.speedColor(s))
                metrics.drawSpeed(c, cell, s, frame)
            }
            ComicField.TEMPERATURE -> {
                val sky = if (s.latitude != null && s.longitude != null) Sky.at(s.latitude, s.longitude, now) else null
                val weather = s.weather?.takeIf { it.freshAt(now) }
                DashboardRenderer.skyPaint(comic.fill.apply { shader = null }, w, h, s, sky, weather)
                c.drawPaint(comic.fill)
                comic.fill.shader = null
                comic.halftone(c, w, h)
                metrics.drawTemperature(c, cell, s, sky, weather, frame)
            }
            ComicField.HEART_RATE -> {
                background(c, w, h, zoneColor(zone))
                metrics.drawHeartRate(c, cell, s, zone ?: 1, frame)
            }
            ComicField.DISTANCE -> {
                background(c, w, h, DashboardRenderer.DISTANCE_COLOR)
                metrics.drawDistance(c, cell, s, frame)
            }
            ComicField.ELEVATION -> {
                c.drawPaint(comic.fill.apply { shader = DashboardRenderer.profileSky(0f, h) })
                comic.fill.shader = null
                val grades = if (s.points.size >= 2) smoothedGrades(s.points) else DoubleArray(0)
                val panel = if (w < WIDE_PROFILE) narrowProfile else profile
                panel.drawFills(c, cell, s, grades, frame)
                comic.halftone(c, w, h)
                panel.drawOverlay(c, cell, s, grades, frame)
            }
            ComicField.AVERAGE_SPEED -> averageSpeed(c, w, h, s, frame)
            ComicField.MAX_SPEED -> maxSpeed(c, w, h, s, frame)
            ComicField.AVERAGE_HR -> averageHeartRate(c, w, h, s, rider, settings, frame)
            ComicField.HR_ZONE -> zone(c, w, h, zone, frame)
            ComicField.MAX_HR -> maxHeartRate(c, w, h, s, frame)
            ComicField.PERCENT_MAX_HR -> percentMaxHr(c, w, h, s, zone, field.value(s, rider, settings), frame)
            ComicField.CADENCE -> cadence(c, w, h, s.cadence, frame, record = false)
            ComicField.AVERAGE_CADENCE -> averageCadence(c, w, h, s.averageCadence, frame)
            ComicField.MAX_CADENCE -> cadence(c, w, h, s.maxCadence, frame, record = true)
            ComicField.GRADE -> sensors.grade(c, w, h, s, frame)
            ComicField.TO_SUMMIT -> sensors.toSummit(c, w, h, s, now, frame)
            ComicField.ASCENT -> sensors.ascent(c, w, h, s, frame)
            ComicField.ALTITUDE -> sensors.altitude(c, w, h, s, frame)
            ComicField.UP_DOWN -> sensors.upDown(c, w, h, s, frame)
            ComicField.POWER -> sensors.power(c, w, h, s, rider, frame)
            ComicField.AVERAGE_POWER -> sensors.averagePower(c, w, h, s, frame)
            ComicField.POWER_ZONE -> sensors.powerZone(c, w, h, s, rider, frame)
            ComicField.PERCENT_FTP -> sensors.percentFtp(c, w, h, s, rider, frame)
            ComicField.GEAR -> sensors.gear(c, w, h, s, settings, now, frame)
            ComicField.CALORIES -> sensors.calories(c, w, h, s, frame)
            ComicField.NEXT_TURN -> sensors.nextTurn(c, w, h, s, frame)
            ComicField.RAIN -> sensors.rain(c, w, h, rain, now, frame)
            ComicField.WIND -> sensors.wind(c, w, h, s, now, frame)
            ComicField.LIGHTS_ON -> sensors.lightsOn(c, w, h, s, now, frame)
            ComicField.SUNSET -> sensors.sunset(c, w, h, s, now, frame)
        }
        c.restore()
        comic.stroke.color = INK
        comic.stroke.strokeWidth = BORDER
        c.drawRoundRect(RectF(BORDER / 2, BORDER / 2, w - BORDER / 2, h - BORDER / 2), CORNER, CORNER, comic.stroke)
    }

    /** Bunny while the current speed is above the average, snail below it, like on the comic page. */
    private fun averageSpeed(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        val above = isAboveAverage(s)
        background(c, w, h, when (above) { true -> GREEN; false -> RED; null -> GREY })
        c.save()
        c.translate(w - 98, 4f)
        c.scale(0.7f, 0.7f)
        if (above == true) critters.bunny(c, 0f, 0f, frame) else if (above == false) critters.snail(c, 4f, 0f, frame)
        c.restore()
        label(c, ComicField.AVERAGE_SPEED)
        number(c, w, h, s.averageSpeed?.let { units.speed(it) })
    }

    /** Speed streaks rushing past and a trophy that sparkles while the rider is at the top speed right now. */
    private fun maxSpeed(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        c.drawPaint(comic.fill.apply { shader = LinearGradient(0f, 0f, w, h, 0xFFFF9F45.toInt(), 0xFFE9452E.toInt(), Shader.TileMode.CLAMP) })
        comic.fill.shader = null
        comic.halftone(c, w, h)
        comic.stroke.color = 0x661F2328
        comic.stroke.strokeWidth = 3f
        for ((i, y) in listOf(58f, 82f, 106f, 130f, 148f).withIndex()) {
            val length = 26f + (i * 13) % 30
            var x = w - ((frame * 37 + i * 53) % (w + 60).roundToInt())
            while (x > -length) {
                c.drawLine(x, y, x + length, y, comic.stroke)
                x -= w * 0.55f
            }
        }
        val record = s.speed != null && s.maxSpeed != null && s.speed >= s.maxSpeed - RECORD_MARGIN
        c.save()
        c.translate(w - 52, 4f)
        c.scale(0.38f, 0.38f)
        if (record) c.rotate(if (frame % 2 == 0) -8f else 8f, 60f, 116f)
        comic.trophy(c)
        c.restore()
        if (record) sparkles(c, w - 30, 26f, frame)
        label(c, ComicField.MAX_SPEED)
        number(c, w, h, s.maxSpeed?.let { units.speed(it) })
    }

    /** A calm heart in the colour of the average's zone, beating only every other second. */
    private fun averageHeartRate(c: Canvas, w: Float, h: Float, s: RideSnapshot, rider: Rider, settings: FunSettings, frame: Int) {
        val zone = rider.zone(s.averageHeartRate, settings)
        background(c, w, h, zoneColor(zone))
        heart(c, w, if (s.averageHeartRate != null && frame % 2 == 0) 1.08f else 0.95f, -8f)
        label(c, ComicField.AVERAGE_HR)
        number(c, w, h, s.averageHeartRate?.roundToInt()?.toString())
    }

    /** Big zone number over a five-step ladder in the zone colours; the current step stands out. */
    private fun zone(c: Canvas, w: Float, h: Float, zone: Int?, frame: Int) {
        background(c, w, h, zoneColor(zone))
        beatingHeart(c, w, zone, frame)
        label(c, ComicField.HR_ZONE)

        val left = 14f
        val gap = 5f
        val step = (w - 2 * left - 4 * gap) / 5
        val bottom = h - 12
        for (i in 0 until 5) {
            val x = left + i * (step + gap)
            val current = zone == i + 1
            val top = if (current) bottom - 22 else bottom - 12
            comic.sticker(c, Path().apply { addRoundRect(RectF(x, top, x + step, bottom), 4f, 4f, Path.Direction.CW) },
                DashboardRenderer.ZONE_COLORS[i], if (current) 3f else 2f, shadow = if (current) 2.5f else 0f)
        }
        val value = zone?.let { "$zoneShort$it" }
        comic.bigNumber(c, value ?: DASH, w / 2, h - 44, fit(value ?: DASH, ZONE_SIZE, w - 40))
    }

    /** The heart gets a crown: this is the best the rider has given today. */
    private fun maxHeartRate(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        background(c, w, h, 0xFFD93A5E.toInt())
        val beat = s.maxHeartRate != null && frame % 2 == 0
        heart(c, w, if (beat) 1.12f else 1f, 8f, fill = 0xFFFFD6DE.toInt())
        crown(c, w - 30, if (beat) 3f else 5f)
        label(c, ComicField.MAX_HR)
        number(c, w, h, s.maxHeartRate?.roundToInt()?.toString())
    }

    /** Percent of max with a thermometer on the right edge, filled from 50 to 100 % in the zone colours. */
    private fun percentMaxHr(c: Canvas, w: Float, h: Float, s: RideSnapshot, zone: Int?, percent: Double?, frame: Int) {
        background(c, w, h, zoneColor(zone))
        label(c, ComicField.PERCENT_MAX_HR)
        val tube = RectF(w - 36, 14f, w - 18, h - 30)
        val bulb = h - 24
        comic.sticker(c, Path().apply {
            addRoundRect(tube, 9f, 9f, Path.Direction.CW)
            addCircle(tube.centerX(), bulb, 15f, Path.Direction.CW)
        }, Color.WHITE, 3f, shadow = 2.5f)
        val level = percent?.let { ((it - 50) / 50).toFloat().coerceIn(0f, 1f) } ?: 0f
        // A little wobble on top of the mercury, so it looks alive at the same value
        val wobble = if (percent != null && frame % 2 == 0) 2f else 0f
        val top = tube.bottom - (tube.height() - 6) * level - wobble
        comic.fill.color = zoneColor(zone).takeIf { zone != null && zone > 1 } ?: 0xFFF2564B.toInt()
        c.drawRoundRect(RectF(tube.left + 4, top, tube.right - 4, tube.bottom + 4), 5f, 5f, comic.fill)
        c.drawCircle(tube.centerX(), bulb, 11f, comic.fill)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 2f
        for (p in listOf(60, 70, 80, 90)) {
            val y = tube.bottom - (tube.height() - 6) * (p - 50) / 50f
            c.drawLine(tube.left - 6, y, tube.left - 1, y, comic.stroke)
        }
        val value = percent?.roundToInt()?.toString() ?: DASH
        comic.bigNumber(c, value, (w - 44) / 2 + 2, h * BASELINE, fit(value, NUMBER_SIZE, w - 64, PERCENT), unit = PERCENT)
    }

    /** Chainring turning with the legs: slow and orange when grinding, purple with motion lines when spinning, grey while coasting. */
    private fun cadence(c: Canvas, w: Float, h: Float, rpm: Double?, frame: Int, record: Boolean) {
        // Zero while coasting: the chainring stands still
        val pace = rpm?.takeIf { it > 0 }?.let { pedaling(it) }
        val color = when {
            record -> 0xFF8E6BE8.toInt()
            pace == Pedaling.GRINDING -> 0xFFFF9F1C.toInt()
            pace == Pedaling.SPINNING -> 0xFFA884F3.toInt()
            pace == Pedaling.ROUND -> 0xFF22B8A6.toInt()
            else -> GREY
        }
        background(c, w, h, color)
        val step = when (pace) { null -> 0f; Pedaling.GRINDING -> 25f; Pedaling.ROUND -> 70f; Pedaling.SPINNING -> 130f }
        val fast = record || pace == Pedaling.SPINNING
        chainring(c, w - 40, 40f, frame * step, motion = fast)
        if (record) sparkles(c, w - 40, 40f, frame)
        label(c, if (record) ComicField.MAX_CADENCE else ComicField.CADENCE)
        number(c, w, h, rpm?.roundToInt()?.toString())
    }

    /** Average cadence: chainring at a steady, unhurried turn. */
    private fun averageCadence(c: Canvas, w: Float, h: Float, rpm: Double?, frame: Int) {
        background(c, w, h, 0xFF6FD3C4.toInt())
        chainring(c, w - 40, 40f, if (rpm == null) 0f else frame * 45f, motion = false)
        label(c, ComicField.AVERAGE_CADENCE)
        number(c, w, h, rpm?.roundToInt()?.toString())
    }

    private fun background(c: Canvas, w: Float, h: Float, color: Int) {
        c.drawColor(color)
        comic.halftone(c, w, h)
    }

    private fun label(c: Canvas, field: ComicField) = comic.fieldTag(c, labels.getValue(field))

    private fun number(c: Canvas, w: Float, h: Float, value: String?) {
        val text = value ?: DASH
        comic.bigNumber(c, text, w / 2, h * BASELINE, fit(text, NUMBER_SIZE, w - 24))
    }

    private fun fit(value: String, maxSize: Float, available: Float, unit: String = ""): Float {
        var size = maxSize
        while (size > MIN_SIZE && comic.bigNumberWidth(value, size, unit) > available) size -= 2f
        return size
    }

    private fun heart(c: Canvas, w: Float, scale: Float, rotation: Float, fill: Int = Color.WHITE) {
        val hx = w - 30
        val hy = 30f
        c.save()
        c.rotate(rotation, hx, hy)
        comic.sticker(c, comic.heart(hx, hy, 40f * scale), fill, 3f)
        c.restore()
    }

    /** The heart from the comic page: beats harder and tilts in the high zones. */
    private fun beatingHeart(c: Canvas, w: Float, zone: Int?, frame: Int) {
        val beat = zone != null && frame % 2 == 0
        val scale = if (zone == null) 1f else if (beat) (if (zone >= 4) 1.25f else 1.1f) else 0.95f
        heart(c, w, scale, if (zone != null && zone >= 5 && beat) -14f else -8f)
        if (beat && zone != null && zone >= 3) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 2.5f
            c.drawLine(w - 62, 22f, w - 70, 16f, comic.stroke)
            c.drawLine(w - 62, 38f, w - 71, 40f, comic.stroke)
        }
    }

    private fun crown(c: Canvas, cx: Float, bottom: Float) {
        val crown = Path().apply {
            moveTo(cx - 14, bottom + 12); lineTo(cx - 16, bottom); lineTo(cx - 8, bottom + 6); lineTo(cx, bottom - 4)
            lineTo(cx + 8, bottom + 6); lineTo(cx + 16, bottom); lineTo(cx + 14, bottom + 12); close()
        }
        c.save()
        c.rotate(12f, cx, bottom + 6)
        comic.sticker(c, crown, 0xFFFFC72C.toInt(), 2.5f)
        c.restore()
    }

    private fun sparkles(c: Canvas, cx: Float, cy: Float, frame: Int) {
        val spots = listOf(-26f to -14f, 22f to -18f, -20f to 22f, 26f to 16f)
        for ((i, spot) in spots.withIndex()) {
            if ((i + frame) % 2 != 0) continue
            comic.sticker(c, comic.star(cx + spot.first, cy + spot.second, 7f, 2.2f, points = 4), if (i % 2 == 0) Color.WHITE else 0xFFFFE135.toInt(), 1.8f)
        }
    }

    /** Chainring with teeth and one crank arm with pedal, turned by [angle] degrees. */
    private fun chainring(c: Canvas, cx: Float, cy: Float, angle: Float, motion: Boolean) {
        val r = 20f
        if (motion) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 2.5f
            for (offset in listOf(0f, 180f)) {
                c.drawArc(RectF(cx - r - 10, cy - r - 10, cx + r + 10, cy + r + 10), angle + offset + 200f, 60f, false, comic.stroke)
            }
        }
        val teeth = Path().apply {
            val n = 32
            for (i in 0 until n) {
                val a = (i * 2 * PI / n + Math.toRadians(angle.toDouble())).toFloat()
                val rr = if (i % 2 == 0) r else r - 3.5f
                val x = cx + rr * cos(a)
                val y = cy + rr * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        comic.sticker(c, teeth, 0xFFE3E7EC.toInt(), 2.5f, shadow = 2.5f)
        comic.sticker(c, Path().apply { addCircle(cx, cy, r * 0.5f, Path.Direction.CW) }, 0xFF9AA6B4.toInt(), 2f)
        val a = Math.toRadians(angle.toDouble())
        val px = cx + 24f * cos(a).toFloat()
        val py = cy + 24f * sin(a).toFloat()
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 7f
        c.drawLine(cx, cy, px, py, comic.stroke)
        comic.stroke.color = 0xFF5A6472.toInt()
        comic.stroke.strokeWidth = 3.5f
        c.drawLine(cx, cy, px, py, comic.stroke)
        comic.sticker(c, Path().apply { addRoundRect(RectF(px - 7, py - 3, px + 7, py + 3), 2f, 2f, Path.Direction.CW) }, INK, 1.5f)
        comic.sticker(c, Path().apply { addCircle(cx, cy, 3.5f, Path.Direction.CW) }, Color.WHITE, 1.5f)
    }

    private fun zoneColor(zone: Int?) = DashboardRenderer.ZONE_COLORS[(zone ?: 1) - 1]

    private companion object {
        const val DESIGN_WIDTH = 239f
        const val DESIGN_HEIGHT = 160f
        const val CORNER = 12f
        const val WIDE_PROFILE = 360f
        const val BORDER = 3.5f
        const val NUMBER_SIZE = 100f
        const val ZONE_SIZE = 84f
        const val MIN_SIZE = 40f
        const val BASELINE = 0.84f
        const val DASH = "–"
        const val PERCENT = "%"
        /** m/s below the max that still count as riding the top speed right now (about 1 km/h). */
        const val RECORD_MARGIN = 0.3
        val GREEN = 0xFF3FBF66.toInt()
        val RED = 0xFFF2564B.toInt()
        val GREY = 0xFF8FA3B8.toInt()
    }
}

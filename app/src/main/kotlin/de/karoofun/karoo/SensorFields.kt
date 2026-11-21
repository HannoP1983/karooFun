package de.karoofun.karoo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Texts of the single fields: the field names and everything the sensor fields write besides numbers. */
data class FieldTexts(
    val labels: Map<ComicField, String>,
    val front: String,
    val rear: String,
    val crossChain: String,
    val kcal: String,
    val food: (Food, Int) -> String,
    val noRoute: String,
    val noClimb: String,
    val stillToGo: String,
    val turnNow: String,
    val rain: String,
    val dryFrom: String,
    val dry: String,
    val wet: String,
    val nextHour: String,
    val nextMinutes: String,
    val jacketIn: String,
    val wetFor: String,
    val radarLoading: String,
    val radarOutside: String,
    val tailwind: String,
    val headwind: String,
    val crosswind: String,
    val noWeather: String,
    val lightsOnNow: String,
    val on: String,
    val dusk: String,
    val brightFrom: String,
    val waitingForGps: String,
    val sunriseIn: String,
    val sunsetAt: String,
    val sunriseAt: String,
    val noFtp: String,
    val appliance: (Appliance, Int) -> String,
    val espressos: (String, Int) -> String,
    val zoneCharacters: List<String>,
    val pot: List<String>,
    val krrk: String,
    val coffeeGrinder: String,
    val tractor: String,
    val lazyShifting: String,
    val wall: String,
    val push: String,
    val wheee: String,
    val summitBeer: String,
    val summitMadeIt: String,
    val offRoute: String,
    val backToRoute: String,
    val blowDry: String,
    val photoStop: String,
)

/**
 * The fields for power, shifting, navigation, rain, wind and daylight. Same look as the other single fields:
 * colored panel, field name in a bubble, a sticker top right, one big number and, where it helps, a small
 * caption along the bottom. Drawn on the 239 × 160 design cell.
 */
class SensorFields(private val comic: Comic, private val texts: FieldTexts, private val units: Units) {
    private val weatherArt = WeatherArt(comic)
    private val art = FunArt(comic)
    private val clock = SimpleDateFormat("H:mm", units.locale)

    /** Watts with the household appliance they would run: light bulbs, blender, toaster, hair dryer, kettle. */
    fun power(c: Canvas, w: Float, h: Float, s: RideSnapshot, rider: Rider, frame: Int) {
        val watts = s.power
        val zone = rider.powerZone(watts)
        background(c, w, h, zone?.let { powerColor(it) } ?: GREY)
        tag(c, ComicField.POWER)
        if (watts == null) {
            bolt(c, w - 46, 4f, 1f)
            number(c, w, h, null)
            return
        }
        val appliance = Appliance.of(watts)
        art.appliance(c, w - 40, 36f, appliance, watts, frame)
        if (zone != null && zone >= 5 && frame % 2 == 0) zap(c, w - 36, 34f)
        number(c, w, h, watts.roundToInt().toString(), captioned = true)
        caption(c, w, h, texts.appliance(appliance, Appliance.bulbs(watts)))
    }

    /** Average watts; the ride's work so far goes into the caption as espressos. */
    fun averagePower(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        background(c, w, h, TEAL)
        art.espresso(c, w - 38, 38f, frame)
        tag(c, ComicField.AVERAGE_POWER)
        val cups = s.energy?.let { espressos(it) } ?: 0
        number(c, w, h, s.averagePower?.roundToInt()?.toString(), captioned = cups > 0)
        if (cups > 0) caption(c, w, h, texts.espressos(s.energy!!.roundToInt().toString(), cups))
    }

    /** Big zone over a ladder with one step per zone, like the heart rate zone field. */
    fun powerZone(c: Canvas, w: Float, h: Float, s: RideSnapshot, rider: Rider, frame: Int) {
        val zone = rider.powerZone(s.power)
        background(c, w, h, zone?.let { powerColor(it) } ?: GREY)
        val count = zoneCount(rider)
        val character = zone?.let { ZoneCharacter.of(it, count) }
        if (character != null) {
            art.character(c, w - 34, 34f, character, frame)
        } else {
            bolt(c, w - 40, 6f, 0.7f)
        }
        tagOr(c, ComicField.POWER_ZONE, character?.let { texts.zoneCharacters[it.ordinal] }, frame)
        val left = 12f
        val gap = 4f
        val step = (w - 2 * left - (count - 1) * gap) / count
        val bottom = h - 12
        for (i in 0 until count) {
            val x = left + i * (step + gap)
            val current = zone == i + 1
            val top = if (current) bottom - 22 else bottom - 12
            comic.sticker(c, Path().apply { addRoundRect(RectF(x, top, x + step, bottom), 4f, 4f, Path.Direction.CW) },
                powerColor(i + 1), if (current) 3f else 2f, shadow = if (current) 2.5f else 0f)
        }
        val value = zone?.let { "Z$it" } ?: DASH
        comic.bigNumber(c, value, w / 2, h - 44, fit(value, ZONE_SIZE, w - 40))
        if (zone == null && rider.ftp <= 0 && rider.powerZoneStarts.size < 2) caption(c, w, h, texts.noFtp, top = h - 70)
    }

    /** Percent of FTP as a pot on the stove: lukewarm, simmering, bubbling, boiling over from 110 %. */
    fun percentFtp(c: Canvas, w: Float, h: Float, s: RideSnapshot, rider: Rider, frame: Int) {
        val percent = percentFtpOf(s.power, rider.ftp)
        background(c, w, h, rider.powerZone(s.power)?.let { powerColor(it) } ?: GREY)
        val pot = Pot.of(percent ?: 0)
        art.pot(c, w - 44, 40f, pot, frame)
        tagOr(c, ComicField.PERCENT_FTP, percent?.let { texts.pot[pot.ordinal] }, frame)
        if (rider.ftp <= 0) {
            number(c, w, h, null, captioned = true)
            caption(c, w, h, texts.noFtp)
        } else {
            number(c, w, h, percent?.toString(), unit = PERCENT)
        }
    }

    /**
     * Front and rear gear as two numbers with the drivetrain seen from above below them: chainrings left,
     * cassette right, the chain from the ring in use to the cog in use. Cross-chaining turns the field orange.
     */
    fun gear(c: Canvas, w: Float, h: Float, s: RideSnapshot, settings: FunSettings, now: Long, frame: Int) {
        val drive = Drivetrain.of(s.gears, settings)
        val cross = drive.crossChained
        background(c, w, h, if (cross) ORANGE else TEAL)
        val remark = when {
            cross -> null
            lazyShifting(s.gearSince, now, s.speed) -> texts.lazyShifting
            else -> when (pedalStyle(s.cadence, s.speed)) {
                PedalStyle.COFFEE_GRINDER -> texts.coffeeGrinder
                PedalStyle.TRACTOR -> texts.tractor
                null -> null
            }
        }
        tagOr(c, ComicField.GEAR, remark, frame)
        val front = drive.frontGear?.toString() ?: DASH
        val rear = drive.rearGear?.toString() ?: DASH
        for ((value, label, x) in listOf(Triple(front, texts.front, w / 2 - 36), Triple(rear, texts.rear, w / 2 + 36))) {
            comic.bigNumber(c, value, x, 84f, GEAR_SIZE)
            pill(c, label, x, 102f)
        }
        topView(c, RectF(10f, h - 50, w - 10, h - 4), drive, frame)
        if (cross) comic.bubble(c, if (frame % 4 < 2) texts.crossChain else texts.krrk, w - 8, 8f, size = 15f, background = 0xFFFFE135.toInt(), rotation = 4f, anchorRight = true)
    }

    private fun topView(c: Canvas, area: RectF, drive: Drivetrain, frame: Int) {
        val k = min(area.width() * 0.52f / (drive.rings.max() + drive.cogs.max()), 2.4f)
        val fx = area.left + area.width() * 0.27f
        val rx = area.left + area.width() * 0.74f
        val pitch = min(7f, (area.height() - 8) / drive.cogs.size)
        val cassette = pitch * (drive.cogs.size - 1)
        val top = area.top + (area.height() - cassette) / 2
        fun cogY(i: Int) = top + i * pitch
        val ringGap = if (drive.rings.size > 1) min(14f, cassette * 0.35f) else 0f
        fun ringY(i: Int) = area.centerY() + (i - (drive.rings.size - 1) / 2f) * ringGap
        fun bar(x: Float, y: Float, length: Float, on: Boolean, outline: Float, inner: Float) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = outline
            c.drawLine(x - length / 2, y, x + length / 2, y, comic.stroke)
            comic.stroke.color = if (on) 0xFFFFE135.toInt() else 0xFFD5DBE2.toInt()
            comic.stroke.strokeWidth = inner
            c.drawLine(x - length / 2, y, x + length / 2, y, comic.stroke)
        }
        drive.cogs.forEachIndexed { i, teeth ->
            val on = drive.rearGear == i + 1
            bar(rx, cogY(i), teeth * k, on, if (on) 6.5f else 5f, if (on) 3.5f else 2.2f)
        }
        drive.rings.forEachIndexed { i, teeth ->
            val on = drive.frontGear == i + 1
            bar(fx, ringY(i), teeth * k, on, if (on) 8f else 6f, if (on) 4.5f else 3f)
        }
        val f = drive.frontGear ?: return
        val r = drive.rearGear ?: return
        val x1 = fx + drive.rings[f - 1] * k / 2
        val x2 = rx - drive.cogs[r - 1] * k / 2
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 6f
        c.drawLine(x1, ringY(f - 1), x2, cogY(r - 1), comic.stroke)
        // Chain links running along, one step per frame
        comic.stroke.color = 0xFF9AA6B4.toInt()
        comic.stroke.strokeWidth = 2.5f
        comic.stroke.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), -frame * 3f)
        c.drawLine(x1, ringY(f - 1), x2, cogY(r - 1), comic.stroke)
        comic.stroke.pathEffect = null
    }

    /** Calories in food, a different dish every few seconds: pretzel, bratwurst, pizza, döner, wheat beer. */
    fun calories(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        background(c, w, h, 0xFF8BD13F.toInt())
        val kcal = s.calories
        val food = Food.at(frame, kcal)
        art.food(c, w - 38, 34f, food, frame)
        tag(c, ComicField.CALORIES)
        number(c, w, h, kcal?.roundToInt()?.toString(), captioned = true)
        val count = kcal?.let { food.count(it) } ?: 0
        caption(c, w, h, if (count > 0) "${texts.kcal} = ${texts.food(food, count)}" else texts.kcal)
    }

    /** Signpost that gets more urgent the closer the turn is; off the route the way back instead. */
    fun nextTurn(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        s.offRoute?.let { back ->
            background(c, w, h, GREY)
            art.recalculating(c, w - 36, 36f, frame)
            tagOr(c, ComicField.NEXT_TURN, texts.offRoute, frame, always = true)
            val (value, unit) = shortDistance(back)
            number(c, w, h, value, unit = unit, captioned = true)
            caption(c, w, h, texts.backToRoute)
            return
        }
        val meters = s.distanceToNextTurn
        val color = when {
            meters == null -> GREY
            meters > 500 -> 0xFF3FBF66.toInt()
            meters > 100 -> 0xFFFFD23F.toInt()
            meters > NOW_METERS -> ORANGE
            else -> 0xFFF2564B.toInt()
        }
        background(c, w, h, color)
        val bounce = if (meters != null && meters <= 100 && frame % 2 == 0) 6f else 0f
        val px = w - 52
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 5f
        c.drawLine(px, 66f, px, 18f, comic.stroke)
        comic.sticker(c, Path().apply {
            moveTo(px - 6 + bounce, 10f); lineTo(px + 30 + bounce, 10f); lineTo(px + 44 + bounce, 22f)
            lineTo(px + 30 + bounce, 34f); lineTo(px - 6 + bounce, 34f); close()
        }, Color.WHITE, 3f, shadow = 2.5f)
        if (meters != null && meters <= NOW_METERS) {
            comic.bubble(c, texts.turnNow, 10f, 8f, size = REMARK_SIZE_SMALL, background = 0xFFFFE135.toInt())
        } else {
            tag(c, ComicField.NEXT_TURN)
        }
        if (meters == null) {
            number(c, w, h, null, captioned = true)
            caption(c, w, h, texts.noRoute)
            return
        }
        val (value, unit) = shortDistance(meters)
        number(c, w, h, value, unit = unit)
    }

    /**
     * Meters to climb with the mountain and its flag, a climber on its flank as far up as the rider is; the distance
     * left goes into the caption. On top: summit cross, confetti and summit beer.
     */
    fun toSummit(c: Canvas, w: Float, h: Float, s: RideSnapshot, now: Long, frame: Int) {
        val base = 78f
        val up = s.elevationToTop
        if (celebratingSummit(s.summitAt, now) && (up == null || up <= SUMMIT_LEFT)) {
            gradient(c, w, h, 0xFFFFD23F.toInt(), 0xFFFFF1A8.toInt())
            comic.sticker(c, Path().apply { moveTo(w - 128, base); lineTo(w - 64, 18f); lineTo(w + 4, base); close() }, 0xFFFF9A1F.toInt(), 3f)
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 4f
            c.drawLine(w - 64, 18f, w - 64, 0f, comic.stroke)
            c.drawLine(w - 72, 6f, w - 56, 6f, comic.stroke)
            art.confetti(c, w, h, frame)
            tagOr(c, ComicField.TO_SUMMIT, texts.summitBeer, frame)
            number(c, w, h, units.elevation(0.0), unit = units.elevationUnit, captioned = true)
            caption(c, w, h, texts.summitMadeIt)
            return
        }
        gradient(c, w, h, 0xFF6EC3FF.toInt(), 0xFFCFEBFF.toInt())
        comic.sticker(c, Path().apply { moveTo(w - 128, base); lineTo(w - 64, 18f); lineTo(w + 4, base); close() }, 0xFFFF9A1F.toInt(), 3f)
        comic.sticker(c, Path().apply { moveTo(w - 128, base); lineTo(w - 92, 44f); lineTo(w - 76, base); close() }, 0xFF3DDC6A.toInt(), 2f)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(w - 64, 18f, w - 64, 2f, comic.stroke)
        comic.sticker(c, Path().apply { moveTo(w - 64, 3f); lineTo(w - 48, 8f); lineTo(w - 64, 13f); close() }, 0xFFF2564B.toInt(), 2f)
        climbProgress(s.climbTotal, up)?.let { p ->
            // Up the right flank to just below the flag, starting where the flank enters the cell; the left one is behind the field name
            art.climber(c, w - 10 - 52 * p, base - 18 - 46 * p, frame)
        }
        tag(c, ComicField.TO_SUMMIT)
        if (up == null) {
            number(c, w, h, null, captioned = true)
            caption(c, w, h, texts.noClimb)
            return
        }
        number(c, w, h, units.elevation(up), unit = units.elevationUnit, captioned = s.distanceToTop != null)
        s.distanceToTop?.let { caption(c, w, h, String.format(texts.stillToGo, units.shortDistance(it))) }
    }

    /** The slope tilts with the grade, a bike rides on it, colored like the profile's grade bands. */
    fun grade(c: Canvas, w: Float, h: Float, s: RideSnapshot, frame: Int) {
        val grade = s.grade
        background(c, w, h, grade?.let { GradeBand.of(it).color } ?: GREY)
        if (grade != null) {
            val rise = (abs(grade) * 6).toFloat().coerceIn(6f, 84f)
            val up = grade >= 0
            val slope = Path().apply {
                moveTo(0f, h + 2)
                lineTo(0f, if (up) h - 4 else h - 4 - rise)
                lineTo(w, if (up) h - 4 - rise else h - 4)
                lineTo(w, h + 2)
                close()
            }
            comic.sticker(c, slope, 0xD9FFFFFF.toInt(), 3f)
            // The bike rides at the right edge, where the slope is highest uphill and lowest downhill
            val x = w - 34
            val y = h - 4 - rise * (if (up) (w - 34) / w else 34 / w)
            c.save()
            c.translate(x, y)
            c.rotate(Math.toDegrees(atan((if (up) -rise else rise) / w.toDouble())).toFloat())
            c.scale(0.7f, 0.7f)
            c.translate(-29f, -32f)
            comic.bike(c, frame)
            c.restore()
            repeat(GradeMood.sweat(grade)) { i ->
                art.sweat(c, x - 22 - i * 9f, y - 44 + ((frame + i) % 2) * 5f, 0.8f)
            }
            if (GradeMood.of(grade) == GradeMood.WHEEE) {
                comic.stroke.color = Color.WHITE
                comic.stroke.strokeWidth = 3f
                for (dy in listOf(-24f, -14f)) c.drawLine(x - 40 - (frame % 2) * 6, y + dy, x - 26, y + dy + 4, comic.stroke)
            }
        }
        tagOr(c, ComicField.GRADE, grade?.let { GradeMood.of(it) }?.let {
            when (it) { GradeMood.WALL -> texts.wall; GradeMood.PUSH -> texts.push; GradeMood.WHEEE -> texts.wheee }
        }, frame)
        // Number left of the bike, so the bike stays visible at the right edge
        val text = grade?.let { units.grade(it) } ?: DASH
        val unit = if (grade == null) "" else PERCENT
        comic.bigNumber(c, text, (w - BIKE_LANE) / 2 + 6, h * CAPTIONED_BASELINE, fit(text, NUMBER_SIZE, w - BIKE_LANE - 12, unit), unit = unit)
    }

    fun rain(c: Canvas, w: Float, h: Float, outlook: RainOutlook?, now: Long, frame: Int) {
        when (outlook) {
            null, RainOutlook.Unknown -> {
                background(c, w, h, GREY)
                weatherArt.cloud(c, w - 92, 50f, 0xFFDCE3EA.toInt(), 1.2f)
                comic.fieldTag(c, texts.rain)
                number(c, w, h, null, captioned = true)
                caption(c, w, h, if (outlook == null) texts.radarOutside else texts.radarLoading)
            }
            is RainOutlook.StaysDry -> {
                background(c, w, h, 0xFFFFD23F.toInt())
                sun(c, w - 34, 30f, frame)
                comic.fieldTag(c, texts.rain)
                word(c, w, h, texts.dry)
                caption(c, w, h, horizon(outlook.until, now))
            }
            is RainOutlook.StaysWet -> {
                background(c, w, h, 0xFF6E7E92.toInt())
                rainCloud(c, w, frame)
                art.ducks(c, w, h, frame)
                comic.fieldTag(c, texts.rain)
                word(c, w, h, texts.wet)
                caption(c, w, h, horizon(outlook.until, now))
            }
            is RainOutlook.RainFrom -> {
                gradient(c, w, h, 0xFF8C9AAB.toInt(), 0xFF6E7E92.toInt())
                rainCloud(c, w, frame)
                tag(c, ComicField.RAIN)
                number(c, w, h, clock.format(Date(outlook.time)), captioned = true)
                caption(c, w, h, String.format(texts.jacketIn, minutesUntil(outlook.time, now)))
            }
            is RainOutlook.DryFrom -> {
                background(c, w, h, 0xFF4FA3F0.toInt())
                sun(c, w - 30, 26f, frame)
                weatherArt.cloud(c, w - 96, 56f, Color.WHITE, 1.1f)
                art.ducks(c, w, h, frame)
                comic.fieldTag(c, texts.dryFrom)
                number(c, w, h, clock.format(Date(outlook.time)), captioned = true)
                caption(c, w, h, String.format(texts.wetFor, minutesUntil(outlook.time, now)))
            }
        }
    }

    /** "the next hour", or how far the outlook reaches when frames are missing or the way ahead leaves the map. */
    private fun horizon(until: Long?, now: Long) = until?.let { String.format(texts.nextMinutes, minutesUntil(it, now)) } ?: texts.nextHour

    /** Wind speed with the windsock from the temperature cell; the color says whether it helps. */
    fun wind(c: Canvas, w: Float, h: Float, s: RideSnapshot, now: Long, frame: Int) {
        val weather = s.weather?.takeIf { it.freshAt(now) }
        val side = weather?.windSide(s.bearing)
        background(c, w, h, when (side) {
            WindSide.TAIL -> 0xFF3FBF66.toInt()
            WindSide.HEAD -> 0xFFF2564B.toInt()
            WindSide.CROSS -> 0xFF4FA3F0.toInt()
            null -> GREY
        })
        val blowDry = weather != null && side == WindSide.HEAD && weather.windKmh >= BLOW_DRY_KMH
        if (blowDry) {
            art.blowDry(c, w - 70, 38f, frame)
        } else if (weather != null && side != null) {
            c.save()
            c.translate(w - 108, 0f)
            weatherArt.windsock(c, 100f, 64f, weather.windKmh, side, frame)
            c.restore()
        }
        tag(c, ComicField.WIND)
        if (weather == null || side == null) {
            number(c, w, h, null, captioned = true)
            caption(c, w, h, texts.noWeather)
            return
        }
        val speed = if (units.imperialDistance) weather.windKmh * MILES_PER_KM else weather.windKmh
        number(c, w, h, speed.roundToInt().toString(), unit = units.speedUnit, captioned = true)
        caption(c, w, h, when {
            blowDry -> texts.blowDry
            side == WindSide.TAIL -> texts.tailwind
            side == WindSide.HEAD -> texts.headwind
            else -> texts.crosswind
        })
    }

    /** Countdown to civil dusk, when bike lights are needed; after it the lamp is on until it gets light. */
    fun lightsOn(c: Canvas, w: Float, h: Float, s: RideSnapshot, now: Long, frame: Int) {
        val lat = s.latitude
        val lon = s.longitude
        if (lat == null || lon == null) return waiting(c, w, h, ComicField.LIGHTS_ON)
        val light = Astronomy.sunElevation(lat, lon, now) > Sky.CIVIL_DUSK
        if (light) {
            gradient(c, w, h, 0xFF3D3A8C.toInt(), 0xFFFF8A65.toInt())
            lamp(c, w - 34, 34f, lit = false, frame)
            tag(c, ComicField.LIGHTS_ON)
            val dusk = Astronomy.nextCrossing(lat, lon, now, Sky.CIVIL_DUSK, rising = false)
            number(c, w, h, dusk?.let { countdown(it - now) }, captioned = dusk != null)
            dusk?.let { caption(c, w, h, String.format(texts.dusk, clock.format(Date(it)))) }
        } else {
            gradient(c, w, h, 0xFF14203F.toInt(), 0xFF2D3F72.toInt())
            art.bats(c, w - 70, frame)
            lamp(c, w - 34, 34f, lit = true, frame)
            comic.fieldTag(c, texts.lightsOnNow, 0xFFFFE135.toInt())
            word(c, w, h, texts.on)
            Astronomy.nextCrossing(lat, lon, now, Sky.CIVIL_DUSK, rising = true)?.let {
                caption(c, w, h, String.format(texts.brightFrom, clock.format(Date(it))))
            }
        }
    }

    /** Countdown to sunset with the sun over the hills; after sunset it counts down to sunrise. */
    fun sunset(c: Canvas, w: Float, h: Float, s: RideSnapshot, now: Long, frame: Int) {
        val lat = s.latitude
        val lon = s.longitude
        if (lat == null || lon == null) return waiting(c, w, h, ComicField.SUNSET)
        val up = Astronomy.sunElevation(lat, lon, now) > Sky.SUNSET_ELEVATION
        val next = Astronomy.nextCrossing(lat, lon, now, Sky.SUNSET_ELEVATION, rising = !up)
        if (up) {
            gradient(c, w, h, 0xFFFF9F45.toInt(), 0xFFF2566B.toInt())
            comic.sticker(c, Path().apply { addCircle(w - 46, 46f + (frame % 3) * 2, 20f, Path.Direction.CW) }, 0xFFFF4E1F.toInt(), 3f)
            hills(c, w, 0xFF8A3E6B.toInt())
            val golden = next != null && next - now <= PHOTO_STOP_MS
            tagOr(c, ComicField.SUNSET, if (golden) texts.photoStop else null, frame)
        } else {
            gradient(c, w, h, 0xFF14203F.toInt(), 0xFF3D3A8C.toInt())
            comic.sticker(c, Path().apply { addCircle(w - 46, 50f - (frame % 3) * 2, 20f, Path.Direction.CW) }, 0xFFFFC23D.toInt(), 3f)
            hills(c, w, 0xFF2E2350.toInt())
            comic.fieldTag(c, texts.sunriseIn)
        }
        number(c, w, h, next?.let { countdown(it - now) }, captioned = next != null)
        next?.let { caption(c, w, h, String.format(if (up) texts.sunsetAt else texts.sunriseAt, clock.format(Date(it)))) }
    }

    private fun waiting(c: Canvas, w: Float, h: Float, field: ComicField) {
        background(c, w, h, GREY)
        tag(c, field)
        number(c, w, h, null, captioned = true)
        caption(c, w, h, texts.waitingForGps)
    }

    private fun background(c: Canvas, w: Float, h: Float, color: Int) {
        c.drawColor(color)
        comic.halftone(c, w, h)
    }

    private fun gradient(c: Canvas, w: Float, h: Float, top: Int, bottom: Int) {
        comic.fill.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawPaint(comic.fill)
        comic.fill.shader = null
        comic.halftone(c, w, h)
    }

    private fun tag(c: Canvas, field: ComicField) = comic.fieldTag(c, texts.labels.getValue(field))

    /**
     * The field name, taking turns with a comic [remark] every few seconds, so the big number stays free.
     * With [always] the remark stays.
     */
    private fun tagOr(c: Canvas, field: ComicField, remark: String?, frame: Int, always: Boolean = false) {
        if (remark != null && (always || frame % REMARK_CYCLE >= REMARK_CYCLE / 2)) {
            comic.bubble(c, remark, 10f, 8f, size = Comic.TAG_SIZE, background = 0xFFFFE135.toInt())
        } else {
            tag(c, field)
        }
    }

    /** Big number; with a caption below it sits higher and a bit smaller. */
    private fun number(c: Canvas, w: Float, h: Float, value: String?, unit: String = "", captioned: Boolean = false, baseline: Float = if (captioned) CAPTIONED_BASELINE else BASELINE) {
        val text = value ?: DASH
        val size = fit(text, if (captioned) CAPTIONED_SIZE else NUMBER_SIZE, w - 24, unit)
        comic.bigNumber(c, text, w / 2, h * baseline, size, unit = unit)
    }

    /** A word instead of a number, e.g. "DRY", as big as it fits. */
    private fun word(c: Canvas, w: Float, h: Float, text: String) {
        comic.bigNumber(c, text, w / 2, h * CAPTIONED_BASELINE, fit(text, WORD_SIZE, w - 24))
    }

    private fun caption(c: Canvas, w: Float, h: Float, text: String, top: Float = h - 27) = pill(c, text, w / 2, top + 16, size = 14f)

    /** Small hand-lettered label on a soft white pill, centered on [cx] with its baseline at [baseline]. */
    private fun pill(c: Canvas, text: String, cx: Float, baseline: Float, size: Float = 13f) {
        val width = comic.measure(text, size, comic.hand) + 14
        comic.fill.color = 0xDDFFFFFF.toInt()
        c.drawRoundRect(RectF(cx - width / 2, baseline - size, cx + width / 2, baseline + size * 0.38f), size, size, comic.fill)
        comic.plain(c, text, cx, baseline, size, align = android.graphics.Paint.Align.CENTER)
    }

    private fun fit(value: String, maxSize: Float, available: Float, unit: String = ""): Float {
        var size = maxSize
        while (size > MIN_SIZE && comic.bigNumberWidth(value, size, unit) > available) size -= 2f
        return size
    }

    private fun bolt(c: Canvas, x: Float, y: Float, k: Float) {
        comic.sticker(c, Path().apply {
            moveTo(x + 22 * k, y); lineTo(x + 4 * k, y + 30 * k); lineTo(x + 16 * k, y + 30 * k); lineTo(x + 10 * k, y + 54 * k)
            lineTo(x + 34 * k, y + 20 * k); lineTo(x + 21 * k, y + 20 * k); lineTo(x + 28 * k, y); close()
        }, 0xFFFFE135.toInt(), 3f)
    }

    private fun zap(c: Canvas, cx: Float, cy: Float) {
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 2.5f
        c.drawLine(cx - 30, cy - 14, cx - 38, cy - 18, comic.stroke)
        c.drawLine(cx - 30, cy + 6, cx - 39, cy + 8, comic.stroke)
        c.drawLine(cx + 26, cy - 16, cx + 32, cy - 22, comic.stroke)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, frame: Int) {
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        for (k in 0 until 8) {
            val a = k * Math.PI / 4 + frame * Math.PI / 16
            c.drawLine(cx, cy, cx + 30 * cos(a).toFloat(), cy + 30 * sin(a).toFloat(), comic.stroke)
        }
        comic.sticker(c, Path().apply { addCircle(cx, cy, 17f, Path.Direction.CW) }, 0xFFFF9F1C.toInt(), 3f)
    }

    private fun rainCloud(c: Canvas, w: Float, frame: Int) {
        comic.stroke.color = 0xFFDDF3FF.toInt()
        comic.stroke.strokeWidth = 3f
        for (i in 0 until 4) {
            val x = w - 82 + i * 16f
            val y = 50f + ((frame + i) % 3) * 6
            c.drawLine(x, y, x - 4, y + 10, comic.stroke)
        }
        weatherArt.cloud(c, w - 96, 48f, 0xFFC7CFD8.toInt(), 1.2f)
    }

    private fun lamp(c: Canvas, cx: Float, cy: Float, lit: Boolean, frame: Int) {
        if (lit) {
            comic.stroke.color = 0xFFFFE135.toInt()
            comic.stroke.strokeWidth = 3.5f
            val reach = if (frame % 2 == 0) 32f else 28f
            for (a in listOf(-150.0, -120.0, -90.0, -60.0, -30.0)) {
                val r = Math.toRadians(a)
                c.drawLine(cx + 20 * cos(r).toFloat(), cy - 4 + 20 * sin(r).toFloat(), cx + reach * cos(r).toFloat(), cy - 4 + reach * sin(r).toFloat(), comic.stroke)
            }
        }
        comic.sticker(c, Path().apply {
            moveTo(cx - 10, cy + 14); cubicTo(cx - 12, cy + 6, cx - 20, cy, cx - 20, cy - 8)
            cubicTo(cx - 20, cy - 20, cx - 11, cy - 26, cx, cy - 26); cubicTo(cx + 11, cy - 26, cx + 20, cy - 20, cx + 20, cy - 8)
            cubicTo(cx + 20, cy, cx + 12, cy + 6, cx + 10, cy + 14); close()
        }, if (lit) 0xFFFFE135.toInt() else 0xFFFFF6C9.toInt(), 3f, shadow = 2.5f)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 10, cy + 14, cx + 10, cy + 24), 3f, 3f, Path.Direction.CW) }, 0xFF9AA6B4.toInt(), 2.5f)
    }

    private fun hills(c: Canvas, w: Float, color: Int) {
        comic.sticker(c, Path().apply {
            moveTo(w - 120, 82f); lineTo(w - 120, 64f); cubicTo(w - 96, 52f, w - 70, 58f, w - 52, 66f)
            cubicTo(w - 34, 72f, w - 16, 58f, w + 2, 60f); lineTo(w + 2, 82f); close()
        }, color, 3f)
    }

    private fun shortDistance(meters: Double): Pair<String, String> =
        if (meters >= 1000) String.format(units.locale, "%.1f", units.distanceValue(meters)) to units.distanceUnit
        else units.elevation(meters) to units.elevationUnit

    private fun zoneCount(rider: Rider) = rider.powerZoneStarts.size.takeIf { it >= 2 } ?: Rider.COGGAN_PERCENT.size

    companion object {
        private const val DASH = "–"
        private const val PERCENT = "%"
        private const val NUMBER_SIZE = 100f
        private const val CAPTIONED_SIZE = 84f
        private const val WORD_SIZE = 64f
        private const val ZONE_SIZE = 84f
        private const val GEAR_SIZE = 62f
        private const val MIN_SIZE = 36f
        private const val BASELINE = 0.84f
        private const val CAPTIONED_BASELINE = 0.74f
        private const val NOW_METERS = 30.0
        private const val REMARK_CYCLE = 6
        private const val REMARK_SIZE_SMALL = 16f
        private const val SUMMIT_LEFT = 15.0
        private const val BLOW_DRY_KMH = 30.0
        private const val PHOTO_STOP_MS = 30 * 60_000L
        private const val BIKE_LANE = 52f
        private const val MILES_PER_KM = 0.621371
        private val GREY = 0xFF8FA3B8.toInt()
        private val TEAL = 0xFF22B8A6.toInt()
        private val ORANGE = 0xFFFF9F1C.toInt()

        /** Colors of the classic 7 power zones, from recovery grey to sprint purple. */
        private val POWER_COLORS = intArrayOf(
            0xFFA9B3BF.toInt(), 0xFF4FA3F0.toInt(), 0xFF8BD13F.toInt(), 0xFFFFD23F.toInt(),
            0xFFFF9F1C.toInt(), 0xFFF2564B.toInt(), 0xFFA884F3.toInt(),
        )

        fun powerColor(zone: Int) = POWER_COLORS[(zone - 1).coerceIn(0, POWER_COLORS.size - 1)]

        /** "1:12" for one hour and twelve minutes, "0:38" below an hour. */
        fun countdown(millis: Long): String {
            val minutes = max(0L, millis / 60_000)
            return String.format("%d:%02d", minutes / 60, minutes % 60)
        }

        fun minutesUntil(time: Long, now: Long) = max(0L, (time - now) / 60_000).toInt()

        /** Rain outlook at the rider from the background radar, Unknown while it loads, null outside the radar. */
        fun rainOutlook(s: RideSnapshot): RainOutlook? = RadarStore.state?.value?.let { RainOutlook.at(it, s) } ?: RainOutlook.Unknown
    }
}

/** Power as rounded percent of FTP, null without power or FTP. */
fun percentFtpOf(watts: Double?, ftp: Int): Int? = if (watts == null || ftp <= 0) null else (watts * 100 / ftp).roundToInt()

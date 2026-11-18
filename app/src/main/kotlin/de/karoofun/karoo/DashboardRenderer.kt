package de.karoofun.karoo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws the whole comic page: four metric cells on top, the elevation profile below, separated by
 * hand-drawn wobbly lines instead of a strict grid. Pages too low for the profile show the cells only.
 *
 * All sizes are in view pixels of the Karoo 3 (480 px wide screen).
 */
class DashboardRenderer(texts: DashboardTexts, units: Units) {
    private val comic = Comic()
    private val metrics = MetricsPanel(comic, texts, units)
    private val profile = ProfilePanel(comic, texts, units)

    /** [now] decides where sun and moon stand; previews pass other times of day. */
    fun render(width: Int, height: Int, data: RideSnapshot, zone: Int, frame: Int, now: Long = System.currentTimeMillis()): Bitmap {
        // Bitmaps travel to the Karoo app inside RemoteViews, which must stay well below the 1 MB binder limit.
        // RGB_565 halves the size, so a full page fits without scaling down.
        val scale = min(1f, sqrt(MAX_PIXELS / (width.toFloat() * height)))
        val bitmap = Bitmap.createBitmap(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()), Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val sky = if (data.latitude != null && data.longitude != null) Sky.at(data.latitude, data.longitude, now) else null
        val weather = data.weather?.takeIf { it.freshAt(now) }
        draw(canvas, width.toFloat(), height.toFloat(), data, sky, weather, zone, frame)
        return bitmap
    }

    private fun draw(c: Canvas, w: Float, h: Float, data: RideSnapshot, sky: Sky?, weather: Weather?, zone: Int, frame: Int) {
        val withProfile = h >= MIN_PROFILE_PAGE
        val mh = if (withProfile) h / 2 else h
        val cx = w / 2
        val my = mh / 2

        val top = WobblyEdge(cx - 4, 0f, cx, my, 6f)
        val left = WobblyEdge(0f, my - 3, cx, my, 5f)
        val right = WobblyEdge(cx, my, w, my + 3, 5f)
        val bottom = WobblyEdge(cx, my, cx + 4, mh, 6f)
        val profileLeft = WobblyEdge(0f, mh + 3, cx + 4, mh, 6f)
        val profileRight = WobblyEdge(cx + 4, mh, w, mh - 3, 6f)

        c.drawColor(INK)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), CORNER, CORNER, Path.Direction.CW) })

        // Region fills
        fill(c, Path().apply { moveTo(0f, 0f); lineTo(top.x0, 0f); top.forward(this); left.backward(this); close() }) { color = speedColor(data) }
        fill(c, Path().apply { moveTo(top.x0, 0f); lineTo(w, 0f); lineTo(w, right.y1); right.backward(this); top.backward(this); close() }) {
            skyPaint(this, w - cx, my, data, sky, weather)
            shader?.setLocalMatrix(Matrix().apply { setTranslate(cx, 0f) })
        }
        val lowerEdge: (Path) -> Unit = { p -> if (withProfile) profileLeft.backward(p) else { p.lineTo(bottom.x1, h); p.lineTo(0f, h) } }
        fill(c, Path().apply { moveTo(0f, left.y0); left.forward(this); bottom.forward(this); lowerEdge(this); close() }) {
            color = if (data.heartRate == null) ZONE_COLORS[0] else ZONE_COLORS[zone - 1]
        }
        fill(c, Path().apply {
            moveTo(cx, my); right.forward(this)
            if (withProfile) { lineTo(w, profileRight.y1); profileRight.backward(this) } else { lineTo(w, h); lineTo(bottom.x1, h) }
            bottom.backward(this); close()
        }) { color = DISTANCE_COLOR }

        val profileArea = RectF(0f, mh, w, h)
        val grades = if (data.points.size >= 2) smoothedGrades(data.points) else DoubleArray(0)
        if (withProfile) {
            fill(c, Path().apply { moveTo(0f, profileLeft.y0); profileLeft.forward(this); profileRight.forward(this); lineTo(w, h); lineTo(0f, h); close() }) {
                shader = profileSky(mh, h)
            }
            profile.drawFills(c, profileArea, data, grades, frame)
        }

        comic.halftone(c, w, h)

        // Hand-drawn separators, fading out before the edges and the crossing
        comic.partialStroke(c, top.path(), 0.12f, 0.85f)
        comic.partialStroke(c, bottom.path(), 0.15f, 0.85f)
        comic.partialStroke(c, left.path(), 0.1f, 0.85f)
        comic.partialStroke(c, right.path(), 0.1f, 0.88f)
        if (withProfile) {
            comic.partialStroke(c, Path().apply { moveTo(0f, profileLeft.y0); profileLeft.forward(this); profileRight.forward(this) }, 0.05f, 0.95f)
        }

        metrics.drawSpeed(c, RectF(0f, 0f, cx, my), data, frame)
        metrics.drawTemperature(c, RectF(cx, 0f, w, my), data, sky, weather, frame)
        metrics.drawHeartRate(c, RectF(0f, my, cx, mh), data, zone, frame)
        metrics.drawDistance(c, RectF(cx, my, w, mh), data, frame)
        if (withProfile) profile.drawOverlay(c, profileArea, data, grades, frame)
        c.restore()

        comic.stroke.color = INK
        comic.stroke.strokeWidth = BORDER
        c.drawRoundRect(RectF(BORDER / 2, BORDER / 2, w - BORDER / 2, h - BORDER / 2), CORNER, CORNER, comic.stroke)
    }

    private inline fun fill(c: Canvas, path: Path, setup: Paint.() -> Unit) {
        comic.fill.shader = null
        comic.fill.setup()
        c.drawPath(path, comic.fill)
        comic.fill.shader = null
    }

    companion object {
        /**
         * Background of the temperature cell in cell coordinates ([w] × [h]): sky by time of day, grey when the
         * weather is, ice blue in frost and a glowing furnace in the heat.
         */
        fun skyPaint(paint: Paint, w: Float, h: Float, data: RideSnapshot, sky: Sky?, weather: Weather?) {
            val dark = sky?.dark == true
            val condition = weather?.condition ?: Condition.CLEAR
            val grey = GREY_SKIES[condition]?.getValue(sky.daylight)
            val state = tempState(data.temperature)
            if (grey != null && (state == TempState.NORMAL || state == TempState.HOT && condition.wet)) {
                paint.shader = vertical(h, grey.first, grey.second)
                return
            }
            when (state) {
                TempState.FREEZE -> paint.shader = if (dark) vertical(h, 0xFF14203F.toInt(), 0xFF3A5A8C.toInt())
                    else vertical(h, 0xFFE3F6FF.toInt(), 0xFF7FCBF0.toInt())
                TempState.HOT -> paint.shader = if (dark) vertical(h, 0xFF2A1E4A.toInt(), 0xFF7A2E3A.toInt())
                    else RadialGradient(w * 0.55f, h, w,
                        intArrayOf(0xFFFFC23D.toInt(), 0xFFFF7A2E.toInt(), 0xFFE9452E.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                TempState.NORMAL -> when (sky?.phase ?: SkyPhase.DAY) {
                    SkyPhase.DAY -> paint.color = 0xFFFFD23F.toInt()
                    SkyPhase.LOW_SUN -> paint.shader = vertical(h, 0xFFFFD866.toInt(), 0xFFFFB347.toInt())
                    SkyPhase.GOLDEN -> paint.shader = vertical(h, 0xFFFF9F45.toInt(), 0xFFFF7A59.toInt(), 0xFFF2566B.toInt(), middle = 0.6f)
                    SkyPhase.DUSK -> paint.shader = vertical(h, 0xFF3D3A8C.toInt(), 0xFF8E4B9C.toInt(), 0xFFFF8A65.toInt(), middle = 0.55f)
                    SkyPhase.NIGHT -> paint.shader = vertical(h, 0xFF14203F.toInt(), 0xFF2D3F72.toInt())
                }
            }
        }

        /** Background of the speed cell: green above the average, red below, grey without one. */
        fun speedColor(data: RideSnapshot) = when (isAboveAverage(data)) {
            true -> 0xFF3FBF66.toInt()
            false -> 0xFFF2564B.toInt()
            null -> 0xFF8FA3B8.toInt()
        }

        const val DISTANCE_COLOR = 0xFFA884F3.toInt()

        /** Sky behind the elevation profile. */
        fun profileSky(top: Float, bottom: Float): Shader = LinearGradient(0f, top, 0f, bottom, 0xFF6EC3FF.toInt(), 0xFFCFEBFF.toInt(), Shader.TileMode.CLAMP)

        /** Top-to-bottom sky gradient, optionally with a third color at [middle]. */
        private fun vertical(bottom: Float, top: Int, vararg below: Int, middle: Float = 0.5f): Shader {
            val colors = intArrayOf(top, *below)
            val positions = if (colors.size == 3) floatArrayOf(0f, middle, 1f) else null
            return LinearGradient(0f, 0f, 0f, bottom, colors, positions, Shader.TileMode.CLAMP)
        }

        val ZONE_COLORS = intArrayOf(0xFFA9B3BF.toInt(), 0xFF4FA3F0.toInt(), 0xFF8BD13F.toInt(), 0xFFFF9F1C.toInt(), 0xFFF2564B.toInt())
        private const val MAX_PIXELS = 320_000f

        /** Top and bottom sky color for grey weather by time of day. */
        private val GREY_SKIES = run {
            val cloudy = mapOf(
                Daylight.DAY to (0xFFBFD2E0.toInt() to 0xFFA3B6C6.toInt()),
                Daylight.EVENING to (0xFFB88C9E.toInt() to 0xFFD98A7A.toInt()),
                Daylight.NIGHT to (0xFF1E2638.toInt() to 0xFF33405A.toInt()),
            )
            mapOf(
                Condition.OVERCAST to cloudy,
                Condition.SNOW to cloudy,
                Condition.FOG to cloudy,
                Condition.RAIN to mapOf(
                    Daylight.DAY to (0xFF8C9AAB.toInt() to 0xFF6E7E92.toInt()),
                    Daylight.EVENING to (0xFF7A6A85.toInt() to 0xFF9A6E78.toInt()),
                    Daylight.NIGHT to (0xFF1A202E.toInt() to 0xFF2B3445.toInt()),
                ),
                Condition.STORM to mapOf(
                    Daylight.DAY to (0xFF4F5566.toInt() to 0xFF353A48.toInt()),
                    Daylight.EVENING to (0xFF3F3550.toInt() to 0xFF5A3A4E.toInt()),
                    Daylight.NIGHT to (0xFF14161F.toInt() to 0xFF262A38.toInt()),
                ),
            )
        }
        private const val MIN_PROFILE_PAGE = 480f
        private const val CORNER = 18f
        private const val BORDER = 3.5f
    }
}

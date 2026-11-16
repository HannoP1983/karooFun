package de.karoofun.karoo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Weather on top of the temperature cell sky: cloud bank, rain, snow, lightning, fog and an airport
 * windsock. Positions are designed for a 239 × 160 cell and scale with the cell width; everything stays
 * clear of the number or lies under it.
 */
class WeatherArt(private val comic: Comic) {
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 10f
    }

    /** Big cloud across the top right, hiding sun or moon. */
    fun cloudBank(c: Canvas, w: Float, fill: Int, shade: Int) {
        val k = w / DESIGN_WIDTH
        comic.sticker(c, Path().apply {
            moveTo(96 * k, 40f)
            cubicTo(86 * k, 40f, 84 * k, 26f, 96 * k, 24f)
            cubicTo(94 * k, 10f, 112 * k, 4f, 122 * k, 12f)
            cubicTo(128 * k, -2f, 152 * k, -4f, 160 * k, 8f)
            cubicTo(170 * k, -4f, 196 * k, -2f, 200 * k, 12f)
            cubicTo(214 * k, 6f, 236 * k, 12f, 234 * k, 28f)
            cubicTo(246 * k, 30f, 246 * k, 46f, 232 * k, 46f)
            lineTo(104 * k, 46f)
            cubicTo(96 * k, 46f, 92 * k, 42f, 96 * k, 40f)
            close()
        }, fill, 3f)
        comic.stroke.color = shade
        comic.stroke.strokeWidth = 4f
        c.drawPath(Path().apply {
            moveTo(110 * k, 44f)
            cubicTo(130 * k, 38f, 150 * k, 46f, 170 * k, 40f)
            cubicTo(190 * k, 46f, 212 * k, 38f, 230 * k, 44f)
        }, comic.stroke)
    }

    /** Drops falling a bit further every frame, three per column; [heavy] adds columns. */
    fun rain(c: Canvas, w: Float, h: Float, frame: Int, color: Int, heavy: Boolean) {
        val k = w / DESIGN_WIDTH
        val columns = if (heavy) HEAVY_RAIN else RAIN
        columns.forEachIndexed { i, column ->
            for (j in 0 until 3) {
                val y = 52f + j * 36 + (frame * 14 + i * 13) % 36
                if (y > h - 10) continue
                val x = column * k - j * 5
                comic.sticker(c, Path().apply {
                    moveTo(x, y)
                    quadTo(x + 6, y + 10, x, y + 14)
                    quadTo(x - 6, y + 10, x, y)
                    close()
                }, color, 2f)
            }
        }
    }

    fun snow(c: Canvas, w: Float, h: Float, frame: Int) {
        val k = w / DESIGN_WIDTH
        FLAKES.forEachIndexed { i, (x, y, r) ->
            comic.snowflake(c, x * k - (frame % 2) * 3, y + ((frame + i) % 3) * 6, r)
        }
        comic.sticker(c, Path().apply {
            moveTo(-2f, h + 2); lineTo(-2f, h - 14)
            cubicTo(60 * k, h - 24, 140 * k, h - 6, w + 2, h - 18)
            lineTo(w + 2, h + 2); close()
        }, Color.WHITE, 3f)
    }

    /** Yellow lightning bolt hanging from the cloud bank right of the number. */
    fun bolt(c: Canvas, w: Float) {
        val x = w - 17
        val y = 40f
        comic.sticker(c, Path().apply {
            moveTo(x, y); rLineTo(-16f, 34f); rLineTo(13f, 0f); rLineTo(-11f, 34f)
            rLineTo(30f, -44f); rLineTo(-14f, 0f); rLineTo(12f, -24f); close()
        }, 0xFFFFE14D.toInt(), 3f)
    }

    /** Dashed white fog bands drifting sideways. */
    fun fog(c: Canvas, w: Float, h: Float, frame: Int) {
        val k = h / DESIGN_HEIGHT
        FOG.forEachIndexed { i, (y, alpha) ->
            band.color = Color.argb((alpha * 255).toInt(), 255, 255, 255)
            band.pathEffect = DashPathEffect(floatArrayOf(60f + i * 10, 18f), 0f)
            val x = -10f + ((frame + i) % 2) * 8
            c.drawLine(x, y * k, w + 10, y * k, band)
        }
    }

    /**
     * Red and white airport windsock under the number. Like the real one it lifts with the wind and stands
     * straight from about 28 km/h. Tailwind points it along the riding direction (right), headwind moves the
     * pole to the right edge and points it left, crosswind shows it foreshortened.
     */
    fun windsock(c: Canvas, w: Float, h: Float, windKmh: Double, side: WindSide, frame: Int) {
        val head = side == WindSide.HEAD
        val px = if (head) w - 26 else 26f
        val top = h - 34
        val sx = if (head) -1f else 1f
        val lift = min(1.0, windKmh / FULL_WIND_KMH).toFloat()
        val angle = 45 * (1 - lift) + floatArrayOf(0f, 4f, -2f, 3f)[frame % 4]
        val length = if (side == WindSide.CROSS) 32f else 50f

        comic.stroke.color = INK
        comic.stroke.strokeWidth = 6f
        c.drawLine(px, h + 2, px, top - 6, comic.stroke)
        comic.stroke.color = 0xFFC9CED6.toInt()
        comic.stroke.strokeWidth = 2.5f
        c.drawLine(px, h + 2, px, top - 6, comic.stroke)
        comic.sticker(c, Path().apply { addCircle(px, top - 7, 3.5f, Path.Direction.CW) }, RED, 2f)

        c.save()
        c.translate(px, top)
        c.scale(sx, 1f)
        c.rotate(angle)
        fun wave(t: Float) = sin((t * 3 + frame) * 2) * 1.5f * lift
        for (i in 0 until 5) {
            val a = i / 5f
            val b = (i + 1) / 5f
            val ra = SOCK_START + (SOCK_END - SOCK_START) * a
            val rb = SOCK_START + (SOCK_END - SOCK_START) * b
            comic.sticker(c, Path().apply {
                moveTo(a * length, -ra + wave(a)); lineTo(b * length, -rb + wave(b))
                lineTo(b * length, rb + wave(b)); lineTo(a * length, ra + wave(a)); close()
            }, if (i % 2 == 0) RED else Color.WHITE, 2.4f)
        }
        comic.sticker(c, Path().apply { addOval(RectF(length - 2.2f, -SOCK_END, length + 2.2f, SOCK_END), Path.Direction.CW) }, 0xFF8A1F1C.toInt(), 2f)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3.5f
        c.drawLine(0f, -SOCK_START, 0f, SOCK_START, comic.stroke)
        c.restore()

        if (windKmh >= GUSTY_KMH) {
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 3f
            for ((dx, dy, l) in GUSTS) {
                val x = px + sx * dx
                c.drawPath(Path().apply { moveTo(x, top + dy); rQuadTo(sx * l / 2, -4f, sx * l, 0f) }, comic.stroke)
            }
        }
    }

    /** The plain fluffy cloud from the sunny sky, scaled by [k]; (x, y) is its bottom left. */
    fun cloud(c: Canvas, x: Float, y: Float, color: Int, k: Float = 1f) {
        c.save()
        c.translate(x, y)
        c.scale(k, k)
        comic.sticker(c, Path().apply {
            moveTo(8f, 0f); cubicTo(0f, 0f, 0f, -10f, 8f, -10f); cubicTo(8f, -20f, 23f, -22f, 26f, -13f)
            cubicTo(31f, -21f, 46f, -18f, 44f, -8f); cubicTo(54f, -8f, 54f, 0f, 46f, 0f); close()
        }, color, max(1.5f, 3f / k))
        c.restore()
    }

    companion object {
        const val DESIGN_WIDTH = 239f
        const val DESIGN_HEIGHT = 160f
        private const val FULL_WIND_KMH = 28.0
        private const val GUSTY_KMH = 20.0
        private const val SOCK_START = 9f
        private const val SOCK_END = 5f
        private const val RED = 0xFFE53935.toInt()
        private val RAIN = floatArrayOf(108f, 134f, 160f, 186f, 212f, 232f, 20f)
        private val HEAVY_RAIN = floatArrayOf(104f, 124f, 144f, 164f, 184f, 204f, 224f, 18f)
        private val FLAKES = listOf(
            Triple(112f, 62f, 6f), Triple(140f, 80f, 5f), Triple(170f, 60f, 7f), Triple(200f, 78f, 5f), Triple(226f, 64f, 6f),
            Triple(130f, 120f, 5f), Triple(214f, 118f, 6f), Triple(20f, 70f, 5f), Triple(26f, 132f, 6f),
        )
        private val FOG = listOf(54f to 0.5f, 70f to 0.4f, 120f to 0.55f, 136f to 0.45f)
        private val GUSTS = listOf(Triple(60f, -8f, 22f), Triple(66f, 4f, 16f), Triple(58f, 14f, 12f))
    }
}

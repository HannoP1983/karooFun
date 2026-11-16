package de.karoofun.karoo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The speed cell mascots in the free strip right of the field tag: a bunny galloping while faster than the
 * average, a big snail creeping while slower. Both are drawn in a 124 × 38 box with the ground at y = 38,
 * and switch pose once per frame since the page only updates once per second.
 */
class Critters(private val comic: Comic) {

    /** Snail facing right, crawling 3 px per frame with a stretching head and wobbling eye stalks. */
    fun snail(c: Canvas, x: Float, y: Float, frame: Int) {
        val stretch = floatArrayOf(0f, 3f, 5f, 2f)[frame % 4]
        val wobble = floatArrayOf(0f, 2f, 0f, -2f)[frame % 4]
        val hx = 88 + stretch
        c.save()
        c.translate(x + (frame % 4) * 3, y)
        for (i in 0 until 3) {
            blob(c, -4f - i * 11, 37f, 5f - i, 2f, Color.argb(((0.75f - i * 0.2f) * 255).toInt(), 255, 255, 255))
        }
        comic.sticker(c, Path().apply {
            moveTo(0f, 38f)
            quadTo(40f, 34f, hx - 6, 36f)
            quadTo(hx + 10, 38f, hx + 12, 28f)
            quadTo(hx + 13, 16f, hx + 3, 15f)
            quadTo(hx - 6, 15f, hx - 9, 25f)
            quadTo(50f, 28f, 12f, 31f)
            close()
        }, 0xFFF7E3A1.toInt(), 3f, shadow = 3f)
        for ((from, to) in listOf(hx to (hx - 4 + wobble to 4f), hx + 6 to (hx + 12 + wobble to 5f))) {
            line(c, from, 17f, to.first, to.second, 2.4f)
            oval(c, to.first, to.second, 4f, 4f, 0f, Color.WHITE, 2f)
            blob(c, to.first + 1.2f, to.second, 1.8f, 1.8f, INK)
        }
        stroke(c, 2f, Path().apply { moveTo(hx + 3, 27f); quadTo(hx + 7, 30f, hx + 11, 27f) })
        blob(c, hx - 1, 24f, 2.5f, 2.5f, PINK)
        oval(c, 40f, 19f, 17f, 17f, 0f, SHELL, 3f)
        stroke(c, 2.2f, Path().apply {
            moveTo(41f, 19f)
            var a = 0.0
            while (a <= SPIRAL_TURNS) {
                val r = 15 * 0.85 * a / SPIRAL_TURNS
                lineTo((41 + r * cos(a)).toFloat(), (19 + r * sin(a)).toFloat())
                a += 0.25
            }
        })
        comic.stroke.color = 0xB3FFFFFF.toInt()
        comic.stroke.strokeWidth = 3f
        c.drawPath(Path().apply { moveTo(29f, 8f); quadTo(35f, 3f, 42f, 4f) }, comic.stroke)
        c.restore()
    }

    /** Caramel bunny running right: flight pose (legs stretched, ears flat) and gathered pose alternate. */
    fun bunny(c: Canvas, x: Float, y: Float, frame: Int) {
        c.save()
        c.translate(x, y)
        if (frame % 2 == 0) {
            oval(c, 4f, 33f, 3.5f, 3.5f, 0f, Color.WHITE, 2f)
            oval(c, -4f, 30f, 2.5f, 2.5f, 0f, Color.WHITE, 2f)
            foot(c, 20f, 30f, 14f, 15f)
            oval(c, 92f, 34f, 7f, 3.5f, 25f, FUR)
            oval(c, 60f, 11f, 14f, 4.5f, -2f, FUR_DARK)
            oval(c, 62f, 7f, 16f, 5f, -10f, FUR)
            oval(c, 63f, 7f, 10f, 2f, -10f, PINK, 0f)
            oval(c, 48f, 24f, 21f, 10f, -4f, FUR, 3f, shadow = 3f)
            oval(c, 68f, 28f, 6f, 5f, 0f, CREAM, 0f)
            oval(c, 28f, 19f, 6f, 6f, 0f, Color.WHITE)
            head(c, 82f, 19f)
        } else {
            oval(c, 24f, 34f, 4.5f, 4.5f, 0f, Color.WHITE, 2f)
            oval(c, 15f, 32f, 3f, 3f, 0f, Color.WHITE, 2f)
            foot(c, 56f, 33f, 4f, 14f)
            oval(c, 46f, 34f, 7f, 3.2f, -25f, FUR)
            oval(c, 60f, 10f, 13f, 4.5f, -8f, FUR_DARK)
            oval(c, 63f, 6f, 15f, 5f, -22f, FUR)
            oval(c, 64f, 6f, 9f, 2f, -22f, PINK, 0f)
            oval(c, 46f, 22f, 18f, 11f, -14f, FUR, 3f, shadow = 3f)
            oval(c, 62f, 27f, 6f, 5f, 0f, CREAM, 0f)
            oval(c, 30f, 17f, 6f, 6f, 0f, Color.WHITE)
            head(c, 78f, 19f)
        }
        c.restore()
    }

    private fun head(c: Canvas, hx: Float, hy: Float) {
        oval(c, hx, hy, 16f, 14.5f, 0f, FUR, 3f)
        // Floppy ear tip falling over the forehead
        comic.sticker(c, Path().apply {
            moveTo(hx - 8, hy - 12)
            quadTo(hx - 2, hy - 18, hx + 4, hy - 15)
            quadTo(hx + 7, hy - 13, hx + 5, hy - 9)
            quadTo(hx, hy - 12, hx - 6, hy - 9)
            close()
        }, FUR, 2f)
        oval(c, hx + 5, hy + 6, 8.5f, 6.5f, 0f, CREAM, 1.6f)
        blob(c, hx - 8, hy + 4, 3.2f, 3.2f, CHEEK)
        blob(c, hx + 13, hy + 3, 2.6f, 2.6f, CHEEK)
        for (ex in floatArrayOf(hx - 2, hx + 9)) {
            val ey = hy - 2
            blob(c, ex, ey, 3.6f, 4.6f, INK)
            blob(c, ex + 1.2f, ey - 1.6f, 1.5f, 1.5f, Color.WHITE)
            blob(c, ex - 1, ey + 1.8f, 0.7f, 0.7f, Color.WHITE)
        }
        comic.sticker(c, Path().apply { moveTo(hx + 3, hy + 3); lineTo(hx + 7, hy + 3); lineTo(hx + 5, hy + 5.4f); close() }, NOSE, 1.2f)
        stroke(c, 1.5f, Path().apply {
            moveTo(hx + 1.5f, hy + 7)
            quadTo(hx + 3.25f, hy + 9.4f, hx + 5, hy + 7)
            quadTo(hx + 6.75f, hy + 9.4f, hx + 8.5f, hy + 7)
        })
    }

    private fun foot(c: Canvas, cx: Float, cy: Float, rotation: Float, length: Float) {
        c.save()
        c.rotate(rotation, cx, cy)
        oval(c, cx, cy, length, 5f, 0f, SOLE)
        oval(c, cx + length * 0.55f, cy, 3f, 3.4f, 0f, PINK, 1.2f)
        oval(c, cx + length * 0.05f, cy, 3.6f, 3.6f, 0f, PINK, 1.2f)
        c.restore()
    }

    /** Ink-outlined ellipse, rotated by [rotation] degrees; a [width] of 0 draws the fill only. */
    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, rotation: Float, color: Int, width: Float = 2.5f, shadow: Float = 0f) {
        c.save()
        c.rotate(rotation, cx, cy)
        if (width > 0) {
            comic.sticker(c, Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }, color, width, shadow)
        } else {
            blob(c, cx, cy, rx, ry, color)
        }
        c.restore()
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        comic.fill.color = color
        c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, comic.fill)
    }

    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float) {
        comic.stroke.color = INK
        comic.stroke.strokeWidth = width
        c.drawLine(x0, y0, x1, y1, comic.stroke)
    }

    private fun stroke(c: Canvas, width: Float, path: Path) {
        comic.stroke.color = INK
        comic.stroke.strokeWidth = width
        c.drawPath(path, comic.stroke)
    }

    private companion object {
        const val SPIRAL_TURNS = PI * 4.2
        const val SHELL = 0xFFE9A86B.toInt()
        const val PINK = 0xFFF4A3B4.toInt()
        const val CHEEK = 0xD9F4A3B4.toInt()
        const val NOSE = 0xFFE86F8A.toInt()
        const val FUR = 0xFFE3A564.toInt()
        const val FUR_DARK = 0xFFC98945.toInt()
        const val CREAM = 0xFFFFF3E0.toInt()
        const val SOLE = 0xFFE7DCD6.toInt()
    }
}

package de.karoofun.karoo

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

const val INK = 0xFF1F2328.toInt()
const val PAPER = 0xFFFFF4D6.toInt()
const val ICE = 0xFFBFE6FA.toInt()

/**
 * Drawing toolkit for the comic look: outlined numbers with a hard offset shadow, speech bubbles,
 * halftone dots and hand-drawn wobbly lines. All sizes are in Karoo view pixels.
 */
class Comic {
    val numbers: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val hand: Typeface = Typeface.create("casual", Typeface.BOLD)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val halftone = Paint().apply {
        val tile = Bitmap.createBitmap(HALFTONE_STEP, HALFTONE_STEP, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawCircle(HALFTONE_STEP / 2f, HALFTONE_STEP / 2f, 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A000000 })
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    fun halftone(c: Canvas, w: Float, h: Float) = c.drawRect(0f, 0f, w, h, halftone)

    fun measure(value: String, size: Float, typeface: Typeface = numbers): Float {
        text.typeface = typeface
        text.textSize = size
        return text.measureText(value)
    }

    /** White comic number with ink outline and hard shadow. Returns the drawn width. */
    fun outlined(
        c: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int = Color.WHITE,
        align: Paint.Align = Paint.Align.LEFT,
        typeface: Typeface = numbers,
    ): Float {
        val outline = (size * 0.042f).coerceIn(2f, 4.5f)
        val shadow = (size * 0.07f).coerceIn(2f, 7f)
        text.typeface = typeface
        text.textSize = size
        text.textAlign = align
        text.color = INK
        text.style = Paint.Style.FILL_AND_STROKE
        text.strokeJoin = Paint.Join.ROUND
        text.strokeWidth = outline * 2
        c.drawText(value, x + shadow, baseline + shadow, text)
        c.drawText(value, x, baseline, text)
        text.style = Paint.Style.FILL
        text.color = color
        c.drawText(value, x, baseline, text)
        return text.measureText(value)
    }

    /**
     * A number for reading at a glance: the whole part big, decimals at 60 %, an optional tiny unit.
     * Centered on [cx]. Returns the drawn width.
     */
    fun bigNumber(c: Canvas, value: String, cx: Float, baseline: Float, size: Float, color: Int = Color.WHITE, unit: String = ""): Float {
        val (whole, decimals) = splitDecimals(value)
        val width = bigNumberWidth(value, size, unit)
        var x = cx - width / 2
        x += outlined(c, whole, x, baseline, size, color)
        if (decimals.isNotEmpty()) x += outlined(c, decimals, x, baseline, size * DECIMAL_RATIO, color)
        if (unit.isNotEmpty()) outlined(c, unit, x + size * 0.04f, baseline, size * 0.3f, color)
        return width
    }

    fun bigNumberWidth(value: String, size: Float, unit: String = ""): Float {
        val (whole, decimals) = splitDecimals(value)
        return measure(whole, size) +
            (if (decimals.isEmpty()) 0f else measure(decimals, size * DECIMAL_RATIO)) +
            (if (unit.isEmpty()) 0f else size * 0.04f + measure(unit, size * 0.3f))
    }

    /** "26,3" → ("26", ",3"); works for both decimal separators. */
    private fun splitDecimals(value: String): Pair<String, String> {
        val i = value.indexOfLast { it == ',' || it == '.' }
        return if (i <= 0) value to "" else value.substring(0, i) to value.substring(i)
    }

    /** Field name in a speech bubble, always at the top left of its cell. */
    fun fieldTag(c: Canvas, text: String, background: Int = 0xFFFFF6D6.toInt()) =
        bubble(c, text, 10f, 8f, size = TAG_SIZE, background = background)

    /** Value followed by a small unit, both outlined. Returns the total width. */
    fun valueWithUnit(c: Canvas, value: String, unit: String, x: Float, baseline: Float, size: Float, color: Int = Color.WHITE): Float {
        val valueWidth = outlined(c, value, x, baseline, size, color)
        val unitWidth = outlined(c, unit, x + valueWidth + size * 0.06f, baseline, size * UNIT_RATIO, color)
        return valueWidth + size * 0.06f + unitWidth
    }

    fun valueWithUnitWidth(value: String, unit: String, size: Float) =
        measure(value, size) + size * 0.06f + measure(unit, size * UNIT_RATIO)

    /** Plain text, no outline. Returns the drawn width. */
    fun plain(c: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int = INK, typeface: Typeface = hand, align: Paint.Align = Paint.Align.LEFT): Float {
        text.typeface = typeface
        text.textSize = size
        text.textAlign = align
        text.style = Paint.Style.FILL
        text.color = color
        c.drawText(value, x, baseline, text)
        return text.measureText(value)
    }

    /**
     * Speech bubble with hand lettering, slightly rotated. [x] is the left edge, or the right edge when
     * [anchorRight] is set. The tail points down-left, or down-right with [tailRight].
     */
    fun bubble(
        c: Canvas,
        value: String,
        x: Float,
        top: Float,
        size: Float = 15f,
        background: Int = 0xFFFFF6D6.toInt(),
        rotation: Float = -3f,
        anchorRight: Boolean = false,
        tailRight: Boolean = anchorRight,
    ): RectF {
        val width = measure(value, size, hand) + size * 1.2f
        val height = size * 1.55f
        val left = if (anchorRight) x - width else x
        val box = RectF(left, top, left + width, top + height)
        val radius = height / 2
        c.save()
        c.rotate(rotation, box.centerX(), box.centerY())
        val tailX = if (tailRight) box.right - radius - 4 else box.left + radius + 4
        val tail = Path().apply {
            moveTo(tailX - 6, box.bottom - 1)
            lineTo(tailX + 6, box.bottom - 1)
            lineTo(tailX + (if (tailRight) 4 else -4), box.bottom + 9)
            close()
        }
        fill.color = INK
        c.drawRoundRect(RectF(box).apply { offset(2.5f, 2.5f) }, radius, radius, fill)
        c.drawPath(tail, fill)
        fill.color = background
        c.drawRoundRect(box, radius, radius, fill)
        stroke.color = INK
        stroke.strokeWidth = 2.5f
        c.drawRoundRect(box, radius, radius, stroke)
        plain(c, value, box.centerX(), box.centerY() + size * 0.36f, size, align = Paint.Align.CENTER)
        c.restore()
        return box
    }

    /** Ink-outlined shape with a hard offset shadow. */
    fun sticker(c: Canvas, path: Path, color: Int, width: Float = 2.5f, shadow: Float = 0f) {
        if (shadow > 0) {
            c.save()
            c.translate(shadow, shadow)
            fill.color = INK
            c.drawPath(path, fill)
            c.restore()
        }
        fill.color = color
        c.drawPath(path, fill)
        stroke.color = INK
        stroke.strokeWidth = width
        c.drawPath(path, stroke)
    }

    fun heart(cx: Float, cy: Float, size: Float): Path {
        val s = size / 100f
        return Path().apply {
            moveTo(cx, cy + 38 * s)
            cubicTo(cx - 30 * s, cy + 16 * s, cx - 44 * s, cy, cx - 44 * s, cy - 18 * s)
            cubicTo(cx - 44 * s, cy - 32 * s, cx - 33 * s, cy - 42 * s, cx - 20 * s, cy - 42 * s)
            cubicTo(cx - 10 * s, cy - 42 * s, cx - 4 * s, cy - 36 * s, cx, cy - 28 * s)
            cubicTo(cx + 4 * s, cy - 36 * s, cx + 10 * s, cy - 42 * s, cx + 20 * s, cy - 42 * s)
            cubicTo(cx + 33 * s, cy - 42 * s, cx + 44 * s, cy - 32 * s, cx + 44 * s, cy - 18 * s)
            cubicTo(cx + 44 * s, cy, cx + 30 * s, cy + 16 * s, cx, cy + 38 * s)
            close()
        }
    }

    fun star(cx: Float, cy: Float, rOut: Float, rIn: Float, points: Int = 5) = Path().apply {
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) rOut else rIn
            val a = i / (points * 2f) * 2 * PI.toFloat() - PI.toFloat() / 2
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** Golden trophy in a 120 × 116 box. Translate and scale the canvas to place it. */
    fun trophy(c: Canvas) {
        val gold = 0xFFFFC72C.toInt()
        stroke.color = INK
        stroke.strokeWidth = 4f
        c.drawPath(Path().apply { moveTo(30f, 24f); cubicTo(10f, 22f, 8f, 52f, 34f, 58f); moveTo(90f, 24f); cubicTo(110f, 22f, 112f, 52f, 86f, 58f) }, stroke)
        sticker(c, Path().apply { moveTo(30f, 14f); lineTo(90f, 14f); lineTo(90f, 44f); cubicTo(90f, 70f, 74f, 84f, 60f, 86f); cubicTo(46f, 84f, 30f, 70f, 30f, 44f); close() }, gold, 4f)
        sticker(c, Path().apply { addRect(52f, 86f, 68f, 100f, Path.Direction.CW) }, gold, 4f)
        sticker(c, Path().apply { addRect(36f, 100f, 84f, 116f, Path.Direction.CW) }, 0xFFB8742A.toInt(), 4f)
        sticker(c, star(60f, 42f, 12f, 5f), Color.WHITE, 2.5f)
        stroke.color = 0xFFFFF1B0.toInt()
        stroke.strokeWidth = 5f
        c.drawLine(40f, 22f, 40f, 44f, stroke)
    }

    /** Brezn with salt, centered on (x, y); it hops on odd [t]. */
    fun pretzel(c: Canvas, x: Float, y: Float, scale: Float, t: Int) {
        c.save()
        c.translate(x, y + if (t % 2 == 1) -4f else 0f)
        c.rotate(if (t % 2 == 1) -8f else 4f)
        c.scale(scale, scale)
        val loops = Path().apply {
            moveTo(-26f, 16f); cubicTo(-48f, -4f, -32f, -38f, -4f, -24f); cubicTo(10f, -16f, 8f, 6f, -16f, 20f)
            moveTo(26f, 16f); cubicTo(48f, -4f, 32f, -38f, 4f, -24f); cubicTo(-10f, -16f, -8f, 6f, 16f, 20f)
            moveTo(-30f, 24f); quadTo(0f, 2f, 30f, 24f)
        }
        stroke.color = INK
        stroke.strokeWidth = 16f
        c.drawPath(loops, stroke)
        stroke.color = 0xFFB8742A.toInt()
        stroke.strokeWidth = 10f
        c.drawPath(loops, stroke)
        fill.color = Color.WHITE
        for ((sx, sy) in listOf(-30f to -6f, -14f to -28f, 16f to -27f, 32f to -4f, 0f to 12f)) c.drawCircle(sx, sy, 2f, fill)
        c.restore()
    }

    /** Six-armed snowflake: ink first, white on top, so it reads on any background. */
    fun snowflake(c: Canvas, cx: Float, cy: Float, r: Float, angle: Float = 0f, color: Int = Color.WHITE) {
        val arms = Path()
        for (k in 0 until 6) {
            val a = Math.toRadians((angle + k * 60).toDouble())
            val ex = cx + r * cos(a).toFloat()
            val ey = cy + r * sin(a).toFloat()
            arms.moveTo(cx, cy)
            arms.lineTo(ex, ey)
            val bx = cx + r * 0.6f * cos(a).toFloat()
            val by = cy + r * 0.6f * sin(a).toFloat()
            for (side in listOf(-1, 1)) {
                val b = a + side * Math.PI / 4
                arms.moveTo(bx, by)
                arms.lineTo(bx + r * 0.3f * cos(b).toFloat(), by + r * 0.3f * sin(b).toFloat())
            }
        }
        stroke.color = INK
        stroke.strokeWidth = max(3.5f, r * 0.34f)
        c.drawPath(arms, stroke)
        stroke.color = color
        stroke.strokeWidth = max(1.5f, r * 0.16f)
        c.drawPath(arms, stroke)
    }

    /**
     * Comic bike in a 58 × 34 box with the wheels touching y = 32. Translate and scale the canvas to place it.
     * Wheels spin a bit every [frame]; [frameColor] other than ink gets an ink outline.
     */
    fun bike(c: Canvas, frame: Int, frameColor: Int = INK) {
        for (wx in listOf(11f, 47f)) {
            sticker(c, Path().apply { addCircle(wx, 23f, 9f, Path.Direction.CW) }, Color.WHITE, 3f)
            c.save()
            c.rotate(frame * 45f, wx, 23f)
            stroke.color = INK
            stroke.strokeWidth = 1.8f
            c.drawLine(wx - 9, 23f, wx + 9, 23f, stroke)
            c.drawLine(wx, 14f, wx, 32f, stroke)
            c.restore()
        }
        val tubes = Path().apply {
            moveTo(11f, 23f); lineTo(22f, 8f); lineTo(40f, 8f); lineTo(47f, 23f)
            moveTo(22f, 8f); lineTo(30f, 23f); lineTo(40f, 8f)
            moveTo(30f, 23f); lineTo(11f, 23f)
            moveTo(20f, 4f); lineTo(26f, 4f)
            moveTo(40f, 8f); lineTo(38f, 2f); lineTo(44f, 2f)
        }
        stroke.color = INK
        stroke.strokeWidth = if (frameColor == INK) 3f else 5f
        c.drawPath(tubes, stroke)
        if (frameColor != INK) {
            stroke.color = frameColor
            stroke.strokeWidth = 2.2f
            c.drawPath(tubes, stroke)
        }
    }

    /** Draws only the middle part of [path], so hand-drawn separators fade in and out instead of boxing cells in. */
    fun partialStroke(c: Canvas, path: Path, from: Float, to: Float, width: Float = 3f) {
        val measure = PathMeasure(path, false)
        val part = Path()
        measure.getSegment(measure.length * from, measure.length * to, part, true)
        stroke.color = INK
        stroke.strokeWidth = width
        c.drawPath(part, stroke)
    }

    companion object {
        const val UNIT_RATIO = 0.38f
        const val DECIMAL_RATIO = 0.6f
        const val TAG_SIZE = 20f
        private const val HALFTONE_STEP = 7
    }
}

/** A slightly bent line between two points that can be appended to a path in both directions. */
class WobblyEdge(val x0: Float, val y0: Float, val x1: Float, val y1: Float, private val bend: Float) {
    private val nx = -(y1 - y0)
    private val ny = x1 - x0
    private val norm = max(1f, kotlin.math.sqrt(nx * nx + ny * ny))
    private val ax = x0 + (x1 - x0) / 3 + nx / norm * bend
    private val ay = y0 + (y1 - y0) / 3 + ny / norm * bend
    private val bx = x0 + (x1 - x0) * 2 / 3 - nx / norm * bend
    private val by = y0 + (y1 - y0) * 2 / 3 - ny / norm * bend

    fun forward(p: Path) = p.cubicTo(ax, ay, bx, by, x1, y1)

    fun backward(p: Path) = p.cubicTo(bx, by, ax, ay, x0, y0)

    fun path() = Path().apply { moveTo(x0, y0); forward(this) }
}

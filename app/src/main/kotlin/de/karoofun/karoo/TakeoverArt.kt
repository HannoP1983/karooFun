package de.karoofun.karoo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Localized texts of the takeovers. */
data class TakeoverTexts(
    val pokalTop: String,
    val pokalOr: String,
    val pokalBottom: String,
    val pokalSayings: List<String>,
    val fireTop: String,
    val fireBottom: String,
    val partyTop: String,
    val partyBottom: String,
    val prost: String,
)

/** The fixed, readable strip at the bottom of a takeover. */
data class PanelInfo(val caption: String, val speed: String, val unit: String, val chips: List<Chip>)

data class Chip(val icon: ChipIcon, val text: String)

enum class ChipIcon { NONE, UP, DOWN, HEART }

/**
 * Draws the full-page comic takeovers frame by frame, at 5 frames per second. The frames do not contain
 * live values; those sit in the separately rendered [renderPanel] strip on top.
 *
 * Coordinates are designed for a 480 px wide page and scaled to the real width.
 */
class TakeoverArt(private val texts: TakeoverTexts) {
    private val comic = Comic()
    private val lightDots = Paint().apply {
        val tile = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawCircle(3.5f, 3.5f, 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x16FFFFFF })
        shader = android.graphics.BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    /** One frame of [count] for a page of [width] × [height]. [variant] picks the saying where there is one. */
    fun renderFrame(type: Takeover, width: Int, height: Int, index: Int, count: Int, variant: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        val s = width / DESIGN_WIDTH
        c.scale(s, s)
        val h = height / s
        c.drawColor(INK)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, DESIGN_WIDTH, h), 18f, 18f, Path.Direction.CW) })
        when (type) {
            Takeover.POKAL -> pokal(c, h, index, variant)
            Takeover.FIRE -> fire(c, h, index, count)
            Takeover.PARTY -> party(c, h, index, count)
        }
        c.restore()
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3.5f
        c.drawRoundRect(RectF(1.75f, 1.75f, DESIGN_WIDTH - 1.75f, h - 1.75f), 18f, 18f, comic.stroke)
        return bitmap
    }

    /** The bottom strip with live values, transparent around the sticker so the art shows through. */
    fun renderPanel(type: Takeover, width: Int, info: PanelInfo): Bitmap {
        val s = width / DESIGN_WIDTH
        val bitmap = Bitmap.createBitmap(width, (PANEL_STRIP * s).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(s, s)
        val color = when (type) {
            Takeover.POKAL -> 0xFF3B8CFF.toInt()
            Takeover.FIRE -> 0xFFF2564B.toInt()
            Takeover.PARTY -> 0xFF22B8A6.toInt()
        }
        val box = RectF(18f, 2f, DESIGN_WIDTH - 18, 2f + PANEL_HEIGHT)
        comic.sticker(c, Path().apply { addRoundRect(box, 22f, 22f, Path.Direction.CW) }, color, 3.5f, shadow = 5f)
        comic.plain(c, info.caption, box.left + 18, box.top + 30, 15f)
        comic.valueWithUnit(c, info.speed, info.unit, box.left + 18, box.top + 86, 58f)

        val chipSize = if (info.chips.size == 1) 30f else 21f
        val chipHeight = chipSize + 14
        var top = box.centerY() - (info.chips.size * chipHeight + (info.chips.size - 1) * 8) / 2
        for (chip in info.chips) {
            drawChip(c, chip, box.right - 18, top, chipSize)
            top += chipHeight + 8
        }
        return bitmap
    }

    private fun drawChip(c: Canvas, chip: Chip, right: Float, top: Float, size: Float) {
        val icon = if (chip.icon == ChipIcon.NONE) 0f else size * 0.8f
        val width = icon + comic.measure(chip.text, size) + 22
        val box = RectF(right - width, top, right, top + size + 14)
        comic.sticker(c, Path().apply { addRoundRect(box, 16f, 16f, Path.Direction.CW) }, Color.WHITE, 3f, shadow = 3f)
        val cy = box.centerY()
        val ix = box.left + 10 + icon / 2
        val r = size * 0.32f
        comic.fill.color = INK
        when (chip.icon) {
            ChipIcon.UP -> c.drawPath(Path().apply { moveTo(ix, cy - r); lineTo(ix + r, cy + r * 0.8f); lineTo(ix - r, cy + r * 0.8f); close() }, comic.fill)
            ChipIcon.DOWN -> c.drawPath(Path().apply { moveTo(ix, cy + r); lineTo(ix + r, cy - r * 0.8f); lineTo(ix - r, cy - r * 0.8f); close() }, comic.fill)
            ChipIcon.HEART -> c.drawPath(comic.heart(ix, cy + 1, size * 0.8f), comic.fill)
            ChipIcon.NONE -> Unit
        }
        comic.plain(c, chip.text, box.left + 12 + icon, cy + size * 0.36f, size, typeface = comic.numbers)
    }

    /* ---------- Pokal oder Spital?! ---------- */

    private fun pokal(c: Canvas, h: Float, t: Int, variant: Int) {
        val cx = DESIGN_WIDTH / 2
        val cy = h / 2
        val offset = (t % 2) * 7.5f
        for (k in 0 until 48) {
            comic.fill.color = if (k % 2 == 0) 0xFFFF5A3C.toInt() else 0xFFFFB627.toInt()
            c.drawPath(wedge(cx, cy, 900f, offset + k * 7.5f, 7.5f), comic.fill)
        }
        c.drawRect(0f, 0f, DESIGN_WIDTH, h, darkDots)

        val shake = SHAKE[t % 4]
        c.save()
        c.translate(shake.first, shake.second)
        val pulse = 1 + (t % 2) * 0.04f
        c.save()
        c.scale(pulse, pulse, 240f, 290f)
        val rot = t * 0.06f
        c.save(); c.translate(6f, 6f); comic.fill.color = INK; c.drawPath(burst(240f, 290f, 228f, 168f, 18, rot, 0.82f), comic.fill); c.restore()
        comic.sticker(c, burst(240f, 290f, 228f, 168f, 18, rot, 0.82f), 0xFFFFE135.toInt(), 5f)
        c.restore()

        val trophyBig = t % 2 == 0
        c.save()
        c.translate(if (trophyBig) 14f else 24f, if (trophyBig) 14f else 24f)
        c.scale(if (trophyBig) 1.15f else 0.95f, if (trophyBig) 1.15f else 0.95f)
        c.rotate(if (trophyBig) -8f else -2f, 60f, 60f)
        comic.trophy(c)
        c.restore()
        c.save()
        c.translate(if (trophyBig) 318f else 304f, if (trophyBig) 52f else 40f)
        c.scale(if (trophyBig) 1f else 1.18f, if (trophyBig) 1f else 1.18f)
        c.rotate(if (trophyBig) 4f else 8f, 70f, 40f)
        ambulance(c, t)
        c.restore()

        // Rider shooting down a slope
        val shift = (t % 3) * 8f
        c.save()
        c.translate(300 + shift, 410 + shift * 0.5f)
        c.rotate(24f)
        comic.stroke.color = Color.WHITE
        comic.stroke.strokeWidth = 5f
        c.drawLine(-90f, -6f, -40f, -6f, comic.stroke)
        c.drawLine(-80f, 8f, -44f, 8f, comic.stroke)
        c.drawLine(-100f, 22f, -56f, 22f, comic.stroke)
        c.scale(1.5f, 1.5f)
        c.translate(-29f, -17f)
        comic.bike(c, t * 4 / 3)
        c.restore()

        c.save()
        c.rotate(-8f, 240f, 100f)
        comic.outlined(c, "VS", 240f, 112f, 34f, align = Paint.Align.CENTER)
        c.restore()
        comic.outlined(c, texts.pokalTop, 240f, 258f, fitSize(texts.pokalTop, 70f), align = Paint.Align.CENTER)
        comic.outlined(c, texts.pokalOr, 240f, 296f, 30f, align = Paint.Align.CENTER)
        val bottomSize = fitSize(texts.pokalBottom + "?!", 70f)
        val bottomWidth = comic.measure(texts.pokalBottom, bottomSize) + comic.measure("?!", bottomSize)
        val x = 240f - bottomWidth / 2
        val wordWidth = comic.outlined(c, texts.pokalBottom, x, 362f, bottomSize)
        comic.outlined(c, "?!", x + wordWidth, 362f, bottomSize, color = 0xFFFF4D3D.toInt())
        val saying = texts.pokalSayings[variant % texts.pokalSayings.size]
        comic.bubble(c, saying, 40f, 420f, size = 18f, rotation = if (t % 2 == 1) -5f else -2f)
        c.restore()
    }

    private fun ambulance(c: Canvas, t: Int) {
        if (t % 2 == 1) {
            comic.stroke.color = 0xFF3B8CFF.toInt()
            comic.stroke.strokeWidth = 4f
            c.drawLine(56f, 2f, 50f, -6f, comic.stroke)
            c.drawLine(70f, 0f, 70f, -10f, comic.stroke)
            c.drawLine(84f, 2f, 90f, -6f, comic.stroke)
        }
        comic.sticker(c, Path().apply { addRect(60f, 6f, 80f, 16f, Path.Direction.CW) }, if (t % 2 == 1) 0xFF3B8CFF.toInt() else Color.WHITE, 3.5f)
        comic.sticker(c, Path().apply { addRect(6f, 16f, 106f, 70f, Path.Direction.CW) }, Color.WHITE, 4f)
        comic.sticker(c, Path().apply { moveTo(106f, 32f); lineTo(124f, 32f); lineTo(138f, 48f); lineTo(138f, 70f); lineTo(106f, 70f); close() }, Color.WHITE, 4f)
        comic.sticker(c, Path().apply { moveTo(112f, 38f); lineTo(122f, 38f); lineTo(130f, 48f); lineTo(112f, 48f); close() }, 0xFF9FD6F2.toInt(), 3f)
        comic.sticker(c, Path().apply {
            moveTo(48f, 26f); lineTo(62f, 26f); lineTo(62f, 38f); lineTo(74f, 38f); lineTo(74f, 52f); lineTo(62f, 52f)
            lineTo(62f, 64f); lineTo(48f, 64f); lineTo(48f, 52f); lineTo(36f, 52f); lineTo(36f, 38f); lineTo(48f, 38f); close()
        }, 0xFFFF4D3D.toInt(), 3f)
        for (x in listOf(32f, 116f)) comic.sticker(c, Path().apply { addCircle(x, 72f, 11f, Path.Direction.CW) }, Color.WHITE, 4f)
    }

    /* ---------- You're on fire! ---------- */

    private fun fire(c: Canvas, h: Float, t: Int, count: Int) {
        val cx = 240f
        val cy = 270f
        comic.fill.shader = RadialGradient(240f, h * 0.42f, h * 0.7f, intArrayOf(0xFF7A1E3A.toInt(), 0xFF3B1240.toInt(), 0xFF1E0B2A.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, DESIGN_WIDTH, h, comic.fill)
        comic.fill.shader = null
        c.drawRect(0f, 0f, DESIGN_WIDTH, h, lightDots)

        val shake = if (t < 3) listOf(0f to 0f, -6f to 4f, 6f to -4f)[t] else SHAKE[t % 4]
        c.save()
        c.translate(shake.first, shake.second)

        // Smoke and debris drift outwards while the explosion throbs
        val smoke = min(1f, t / 5f) * (0.9f + (t % 2) * 0.06f)
        if (smoke > 0) for ((x, y, k) in listOf(Triple(70f, 120f, 1f), Triple(410f, 150f, 0.9f), Triple(60f, 420f, 0.8f), Triple(420f, 400f, 1f))) {
            puff(c, x, y, smoke * k)
        }
        val reach = min(1f, t / 8f)
        for (i in 0 until 8) {
            val a = i / 8.0 * 2 * PI + 0.6
            val r = 150 + reach * 140
            c.save()
            c.translate(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat() * 0.9f)
            c.rotate(t * 55f + i * 45)
            comic.sticker(c, Path().apply { moveTo(-7f, -4f); lineTo(6f, -6f); lineTo(8f, 5f); lineTo(-4f, 7f); close() }, 0xFF3B1240.toInt(), 2.5f)
            c.restore()
        }

        val k = when {
            t < 3 -> listOf(0.25f, 0.65f, 1.12f)[t]
            t >= count - 2 -> listOf(0.8f, 0.5f)[t - (count - 2)]
            else -> 1 + (t % 2) * 0.05f
        }
        val rot = t * 0.05f
        c.save()
        c.scale(k, k, cx, cy)
        c.save(); c.translate(7f, 7f); comic.fill.color = INK; c.drawPath(burst(cx, cy, 250f, 185f, 16, rot, 0.86f), comic.fill); c.restore()
        comic.sticker(c, burst(cx, cy, 250f, 185f, 16, rot, 0.86f), 0xFFE9452E.toInt(), 5f)
        comic.sticker(c, burst(cx, cy, 200f, 150f, 14, -rot + 0.2f, 0.86f), 0xFFFF7A2E.toInt(), 4f)
        comic.sticker(c, burst(cx, cy, 158f, 118f, 12, rot + 0.4f, 0.86f), 0xFFFFC23D.toInt(), 4f)
        comic.sticker(c, burst(cx, cy, 108f, 84f, 10, -rot, 0.86f), 0xFFFFF3B0.toInt(), 3f)
        c.restore()

        if (t >= 1) {
            val sparkReach = min(1f, (t + 1) / 6f)
            for (i in 0 until 14) {
                val a = i / 14.0 * 2 * PI + 0.2
                val r = 120 + sparkReach * (90 + (i % 3) * 30)
                val size = 6f + (i % 3) * 3
                c.save()
                c.translate(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat() * 0.9f)
                c.rotate(t * 40f + i * 30)
                comic.sticker(c, comic.star(0f, 0f, size, size * 0.3f, points = 4), if (i % 2 == 0) Color.WHITE else 0xFFFFE135.toInt(), 2f)
                c.restore()
            }
        }
        if (t >= 2 && t < count - 1) {
            c.save()
            if (t == 2) c.scale(1.15f, 1.15f, 240f, 270f)
            c.rotate(-4f, 240f, 270f)
            comic.outlined(c, texts.fireTop, 240f, 238f, fitSize(texts.fireTop, 38f), align = Paint.Align.CENTER)
            comic.outlined(c, texts.fireBottom, 240f, 310f, fitSize(texts.fireBottom, 72f), color = 0xFFFFE135.toInt(), align = Paint.Align.CENTER)
            c.restore()
        }
        c.restore()
        if (t == 1) {
            comic.fill.color = 0xD9FFF8D0.toInt()
            c.drawRect(0f, 0f, DESIGN_WIDTH, h, comic.fill)
        }
    }

    private fun puff(c: Canvas, x: Float, y: Float, k: Float) {
        c.save()
        c.translate(x, y)
        c.scale(k, k)
        comic.sticker(c, Path().apply {
            moveTo(-30f, 10f); cubicTo(-44f, 10f, -44f, -8f, -30f, -8f); cubicTo(-30f, -26f, -6f, -30f, 0f, -16f)
            cubicTo(8f, -30f, 32f, -26f, 30f, -8f); cubicTo(46f, -8f, 46f, 10f, 30f, 10f); close()
        }, 0xFFE8E1EE.toInt(), 3f)
        c.restore()
    }

    /* ---------- Party-Pace wins the race! ---------- */

    private fun party(c: Canvas, h: Float, t: Int, count: Int) {
        c.drawColor(0xFF2A1B5C.toInt())
        c.drawRect(0f, 0f, DESIGN_WIDTH, h, lightDots)

        // Spotlights sweeping from the disco ball, swapping colors every frame
        for (i in 0 until 6) {
            val a = Math.toDegrees(i / 6.0 * PI + 0.25 + sin(t * 0.9 + i) * 0.18).toFloat()
            comic.fill.color = (PARTY_COLORS[(i + t) % PARTY_COLORS.size] and 0x00FFFFFF) or 0x47000000
            c.drawPath(wedge(240f, 92f, 900f, a, 9f), comic.fill)
        }
        for (i in 0 until 28) {
            val x = (i * 67) % 470 + 5f + if ((t + i) % 2 == 1) 6 else -6
            val y = (((i * 41) % 520 - 60 + t * (14 + (i % 4) * 5)) % 560 + 560) % 560f
            val color = PARTY_COLORS[i % PARTY_COLORS.size]
            if (i % 3 == 0) {
                comic.sticker(c, Path().apply { addCircle(x, y, 5f, Path.Direction.CW) }, color, 2f)
            } else {
                c.save()
                c.rotate(t * 50f + i * 37, x, y)
                comic.sticker(c, Path().apply { addRoundRect(RectF(x - 4, y - 7, x + 4, y + 7), 2f, 2f, Path.Direction.CW) }, color, 2f)
                c.restore()
            }
        }

        // Two mugs clinking every other beat
        val clink = t % 4 == 1 || t % 4 == 2
        beer(c, if (clink) 74f else 62f, 128f, 1f, if (clink) 18f else 6f, t, 0, mirrored = false)
        beer(c, if (clink) 126f else 138f, 128f, 1f, if (clink) 18f else 6f, t, 1, mirrored = true)
        if (clink) {
            for ((x, y, r) in listOf(Triple(82f, 72f, 4f), Triple(120f, 70f, 3.5f), Triple(100f, 60f, 3f))) {
                comic.sticker(c, Path().apply { addCircle(x, y, r, Path.Direction.CW) }, Color.WHITE, 2f)
            }
            for ((x, y) in listOf(100f to 86f, 88f to 92f, 112f to 92f)) {
                comic.sticker(c, Path().apply { moveTo(x, y); quadTo(x - 6, y - 10, x, y - 16); quadTo(x + 6, y - 10, x, y); close() }, 0xFFFFB627.toInt(), 2f)
            }
            comic.bubble(c, texts.prost, 40f, 34f, size = 16f, rotation = -6f, tailRight = true)
        }
        pretzel(c, 404f, 140f, 1.1f, t)
        beer(c, 62f, 436f, 0.95f, if (t % 2 == 1) -8f else 6f, t, 2, mirrored = false)
        beer(c, 418f, 436f, 0.95f, if (t % 2 == 1) 8f else -6f, t, 3, mirrored = true)
        discoBall(c, t)
        partyBike(c, t)

        if (t >= 2 && t < count - 1) {
            val tilt = if (t == 2) -9f else if (t % 2 == 1) -3f else -1f
            val scale = if (t == 2) 1.12f else if (t >= count - 2) 0.9f else 1f
            val banner = RectF(22f, 176f, DESIGN_WIDTH - 22, 310f)
            c.save()
            c.rotate(tilt, banner.centerX(), banner.centerY())
            c.scale(scale, scale, banner.centerX(), banner.centerY())
            comic.sticker(c, Path().apply { addRoundRect(banner, 26f, 26f, Path.Direction.CW) }, 0xFFFF4FA3.toInt(), 4f, shadow = 6f)
            comic.outlined(c, texts.partyTop, 240f, 246f, fitSize(texts.partyTop, 52f, 400f), color = 0xFFFFE135.toInt(), align = Paint.Align.CENTER)
            comic.outlined(c, texts.partyBottom, 240f, 290f, fitSize(texts.partyBottom, 34f, 400f), align = Paint.Align.CENTER)
            c.restore()
        }
    }

    private fun discoBall(c: Canvas, t: Int) {
        val drop = if (t < 3) listOf(-110f, -30f, 8f)[t] else if (t % 2 == 1) 4f else 0f
        val cy = 92 + drop
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(240f, 0f, 240f, cy - 44, comic.stroke)
        c.save()
        c.clipPath(Path().apply { addCircle(240f, cy, 44f, Path.Direction.CW) })
        val tiles = intArrayOf(Color.WHITE, 0xFFC9D3E0.toInt(), 0xFF8A97AA.toInt())
        for (row in -4..4) for (col in -4..4) {
            val x = 240f + col * 13 + (t % 2) * 6
            val y = cy + row * 13
            if (hypot(x - 240f, y - cy) > 52) continue
            comic.fill.color = tiles[((row + col + t) % 3 + 3) % 3]
            c.drawRect(x - 6, y - 6, x + 6, y + 6, comic.fill)
        }
        c.restore()
        comic.stroke.strokeWidth = 4f
        c.drawCircle(240f, cy, 44f, comic.stroke)
        if (t % 2 == 1) {
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 3f
            c.drawLine(200f, cy - 30, 190f, cy - 40, comic.stroke)
            c.drawLine(282f, cy - 26, 292f, cy - 36, comic.stroke)
            c.drawLine(290f, cy + 20, 302f, cy + 24, comic.stroke)
        }
    }

    private fun beer(c: Canvas, x: Float, y: Float, scale: Float, tilt: Float, t: Int, i: Int, mirrored: Boolean) {
        c.save()
        c.translate(x, y)
        if (mirrored) c.scale(-1f, 1f)
        c.rotate(tilt)
        c.scale(scale, scale)
        val handle = Path().apply { moveTo(22f, -12f); cubicTo(42f, -12f, 42f, 22f, 22f, 22f) }
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 10f
        c.drawPath(handle, comic.stroke)
        comic.stroke.color = 0xFFFFF7DA.toInt()
        comic.stroke.strokeWidth = 4f
        c.drawPath(handle, comic.stroke)
        comic.sticker(c, Path().apply { addRoundRect(RectF(-24f, -22f, 24f, 36f), 7f, 7f, Path.Direction.CW) }, 0xFFFFF7DA.toInt(), 3.5f)
        comic.fill.color = 0xFFFFB627.toInt()
        c.drawRoundRect(RectF(-19f, -16f, 19f, 31f), 4f, 4f, comic.fill)
        comic.stroke.color = 0xFFFFD46B.toInt()
        comic.stroke.strokeWidth = 3f
        c.drawLine(-9f, -12f, -9f, 26f, comic.stroke)
        c.drawLine(9f, -12f, 9f, 26f, comic.stroke)
        comic.fill.color = 0xFFFFF3B0.toInt()
        for ((bx, off) in listOf(-10f to 0, 2f to 13, 11f to 26)) {
            c.drawCircle(bx, 24f - ((t * 7 + off + i * 9) % 40), 2.5f, comic.fill)
        }
        comic.sticker(c, Path().apply {
            moveTo(-28f, -16f); cubicTo(-36f, -30f, -18f, -42f, -8f, -32f); cubicTo(-2f, -46f, 22f, -42f, 18f, -30f)
            cubicTo(34f, -34f, 36f, -14f, 25f, -14f); lineTo(25f, -4f); quadTo(20f, 4f, 15f, -4f); lineTo(15f, -12f)
            quadTo(8f, -8f, -2f, -12f); quadTo(-14f, -8f, -28f, -16f); close()
        }, Color.WHITE, 3f)
        c.restore()
    }

    private fun pretzel(c: Canvas, x: Float, y: Float, scale: Float, t: Int) {
        c.save()
        c.translate(x, y + if (t % 2 == 1) -4f else 0f)
        c.rotate(if (t % 2 == 1) -8f else 4f)
        c.scale(scale, scale)
        val loops = Path().apply {
            moveTo(-26f, 16f); cubicTo(-48f, -4f, -32f, -38f, -4f, -24f); cubicTo(10f, -16f, 8f, 6f, -16f, 20f)
            moveTo(26f, 16f); cubicTo(48f, -4f, 32f, -38f, 4f, -24f); cubicTo(-10f, -16f, -8f, 6f, 16f, 20f)
            moveTo(-30f, 24f); quadTo(0f, 2f, 30f, 24f)
        }
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 16f
        c.drawPath(loops, comic.stroke)
        comic.stroke.color = 0xFFB8742A.toInt()
        comic.stroke.strokeWidth = 10f
        c.drawPath(loops, comic.stroke)
        comic.fill.color = Color.WHITE
        for ((sx, sy) in listOf(-30f to -6f, -14f to -28f, 16f to -27f, 32f to -4f, 0f to 12f)) c.drawCircle(sx, sy, 2f, comic.fill)
        c.restore()
    }

    private fun partyBike(c: Canvas, t: Int) {
        comic.sticker(c, Path().apply { addOval(RectF(112f, 462f, 368f, 502f), Path.Direction.CW) }, 0xFFFFE135.toInt(), 3.5f)
        comic.fill.color = 0xFFFFF3B0.toInt()
        val rx = if (t % 2 == 1) 70f else 90f
        val ry = if (t % 2 == 1) 9f else 12f
        c.drawOval(RectF(240 - rx, 482 - ry, 240 + rx, 482 + ry), comic.fill)

        val hop = if (t % 2 == 1) -10f else 0f
        c.save()
        c.translate(173f, 404 + hop)
        c.scale(2.3f, 2.3f)
        comic.bike(c, t, frameColor = 0xFFFF4FA3.toInt())
        comic.sticker(c, Path().apply { moveTo(17f, 4f); lineTo(23f, -14f); lineTo(29f, 4f); close() }, 0xFFFFE135.toInt(), 2f)
        comic.sticker(c, Path().apply { addCircle(23f, -15f, 3f, Path.Direction.CW) }, 0xFF22B8A6.toInt(), 1.5f)
        c.restore()
        if (t % 2 == 1) {
            comic.stroke.color = 0xFFFFE135.toInt()
            comic.stroke.strokeWidth = 3.5f
            c.drawLine(156f, 420f, 142f, 412f, comic.stroke)
            c.drawLine(152f, 444f, 136f, 444f, comic.stroke)
            c.drawLine(324f, 420f, 338f, 412f, comic.stroke)
            c.drawLine(328f, 444f, 344f, 444f, comic.stroke)
        }
    }

    /* ---------- Shapes ---------- */

    private val darkDots = Paint().apply {
        val tile = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawCircle(3.5f, 3.5f, 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F000000 })
        shader = android.graphics.BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun wedge(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) = Path().apply {
        moveTo(cx, cy)
        arcTo(RectF(cx - r, cy - r, cx + r, cy + r), startDeg, sweepDeg)
        close()
    }

    private fun burst(cx: Float, cy: Float, rOut: Float, rIn: Float, spikes: Int, rot: Float, squash: Float) = Path().apply {
        for (i in 0 until spikes * 2) {
            val r = if (i % 2 == 1) rIn else rOut * (0.82f + 0.18f * ((i * 7) % 4) / 3f)
            val a = i / (spikes * 2f) * 2 * PI.toFloat() + rot
            val x = cx + r * cos(a)
            val y = cy + r * sin(a) * squash
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun fitSize(text: String, size: Float, width: Float = 440f): Float {
        var s = size
        while (s > 20 && comic.measure(text, s) > width) s -= 1f
        return max(20f, s)
    }

    companion object {
        const val FPS = 5
        const val DESIGN_WIDTH = 480f
        /** Height of the bottom strip bitmap in design pixels: sticker, shadow and margin. */
        const val PANEL_STRIP = 150f
        private const val PANEL_HEIGHT = 118f
        private val SHAKE = listOf(0f to 0f, -4f to 3f, 4f to -2f, -2f to -3f)
        private val PARTY_COLORS = intArrayOf(0xFFFF4FA3.toInt(), 0xFFFFE135.toInt(), 0xFF22B8A6.toInt(), 0xFF4FA3F0.toInt(), 0xFFFF9F1C.toInt(), 0xFFA884F3.toInt())
    }
}

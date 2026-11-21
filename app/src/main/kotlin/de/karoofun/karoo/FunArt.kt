package de.karoofun.karoo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * The small animated stickers of the sensor fields: household appliances, faces, a pot, food, a climber,
 * rubber ducks, bats. Each sits around a center point and is about 60 px across on the 239 × 160 cell;
 * [frame] counts seconds.
 */
class FunArt(private val comic: Comic) {

    /* ---------- Watts at home ---------- */

    fun appliance(c: Canvas, cx: Float, cy: Float, appliance: Appliance, watts: Double, frame: Int) = when (appliance) {
        Appliance.BULB -> bulb(c, cx, cy, (watts / 200).toFloat().coerceIn(0.3f, 1f), frame)
        Appliance.BLENDER -> blender(c, cx, cy, frame)
        Appliance.TOASTER -> toaster(c, cx, cy, frame)
        Appliance.HAIR_DRYER -> hairDryer(c, cx, cy, frame)
        Appliance.KETTLE -> kettle(c, cx, cy, frame)
    }

    /** Light bulb glowing brighter the more watts go in. */
    private fun bulb(c: Canvas, cx: Float, cy: Float, glow: Float, frame: Int) {
        comic.fill.color = (((0x99 * glow).toInt() + if (frame % 2 == 0) 0x20 else 0) shl 24) or 0xFFF6A0
        c.drawCircle(cx, cy - 6, 24f + 8 * glow, comic.fill)
        comic.sticker(c, Path().apply {
            moveTo(cx - 9, cy + 12); cubicTo(cx - 11, cy + 4, cx - 19, cy - 2, cx - 19, cy - 10)
            cubicTo(cx - 19, cy - 22, cx - 10, cy - 28, cx, cy - 28); cubicTo(cx + 10, cy - 28, cx + 19, cy - 22, cx + 19, cy - 10)
            cubicTo(cx + 19, cy - 2, cx + 11, cy + 4, cx + 9, cy + 12); close()
        }, 0xFFFFF3B0.toInt(), 3f, shadow = 2.5f)
        comic.stroke.color = 0xFFFF9F1C.toInt()
        comic.stroke.strokeWidth = 2f
        c.drawPath(Path().apply { moveTo(cx - 5, cy + 10); lineTo(cx - 5, cy - 4); lineTo(cx, cy - 10); lineTo(cx + 5, cy - 4); lineTo(cx + 5, cy + 10) }, comic.stroke)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 9, cy + 12, cx + 9, cy + 22), 3f, 3f, Path.Direction.CW) }, 0xFF9AA6B4.toInt(), 2.5f)
    }

    private fun blender(c: Canvas, cx: Float, cy: Float, frame: Int) {
        val shake = if (frame % 2 == 0) -2f else 2f
        comic.sticker(c, Path().apply {
            moveTo(cx - 15 + shake, cy - 26); lineTo(cx + 15 + shake, cy - 26); lineTo(cx + 10 + shake, cy + 6); lineTo(cx - 10 + shake, cy + 6); close()
        }, 0xFFBFE6FA.toInt(), 3f)
        comic.fill.color = 0xFFFF7AB6.toInt()
        c.drawPath(Path().apply {
            moveTo(cx - 12 + shake, cy - 10); quadTo(cx + shake, cy - 16 + (frame % 2) * 6, cx + 12 + shake, cy - 10)
            lineTo(cx + 9 + shake, cy + 4); lineTo(cx - 9 + shake, cy + 4); close()
        }, comic.fill)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 17 + shake, cy - 32, cx + 17 + shake, cy - 25), 3f, 3f, Path.Direction.CW) }, 0xFF6E7E92.toInt(), 2.5f)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 18, cy + 6, cx + 18, cy + 24), 5f, 5f, Path.Direction.CW) }, 0xFFF2564B.toInt(), 3f, shadow = 2.5f)
        comic.sticker(c, Path().apply { addCircle(cx, cy + 15, 4f, Path.Direction.CW) }, Color.WHITE, 2f)
    }

    private fun toaster(c: Canvas, cx: Float, cy: Float, frame: Int) {
        val pop = if (frame % 2 == 0) 10f else 0f
        for (x in listOf(cx - 15, cx + 3)) {
            comic.sticker(c, Path().apply { addRoundRect(RectF(x, cy - 18 - pop, x + 12, cy), 3f, 3f, Path.Direction.CW) }, 0xFFE3A857.toInt(), 2.5f)
        }
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 26, cy - 6, cx + 26, cy + 22), 8f, 8f, Path.Direction.CW) }, 0xFFC7CFD8.toInt(), 3f, shadow = 2.5f)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(cx - 15, cy - 2, cx - 3, cy - 2, comic.stroke)
        c.drawLine(cx + 3, cy - 2, cx + 15, cy - 2, comic.stroke)
        c.drawLine(cx + 26, cy + 4, cx + 32, cy + 4, comic.stroke)
        if (pop > 0) {
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 2.5f
            c.drawLine(cx - 22, cy - 26, cx - 28, cy - 32, comic.stroke)
            c.drawLine(cx + 22, cy - 26, cx + 28, cy - 32, comic.stroke)
        }
    }

    private fun hairDryer(c: Canvas, cx: Float, cy: Float, frame: Int) {
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 2, cy + 2, cx + 10, cy + 26), 4f, 4f, Path.Direction.CW) }, 0xFFFF7AB6.toInt(), 3f)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 22, cy - 16, cx + 18, cy + 6), 11f, 11f, Path.Direction.CW) }, 0xFFFF7AB6.toInt(), 3f, shadow = 2.5f)
        comic.sticker(c, Path().apply { addCircle(cx + 16, cy - 5, 11f, Path.Direction.CW) }, 0xFFFF7AB6.toInt(), 3f)
        comic.sticker(c, Path().apply { addCircle(cx + 16, cy - 5, 5f, Path.Direction.CW) }, Color.WHITE, 2f)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 32, cy - 12, cx - 20, cy + 2), 3f, 3f, Path.Direction.CW) }, 0xFF9AA6B4.toInt(), 2.5f)
        val reach = if (frame % 2 == 0) 14f else 8f
        for ((dy, dx) in listOf(-10f to 0f, -5f to 4f, 0f to 0f)) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 4.5f
            c.drawLine(cx - 36 - dx, cy + dy, cx - 36 - dx - reach, cy + dy, comic.stroke)
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 2f
            c.drawLine(cx - 36 - dx, cy + dy, cx - 36 - dx - reach, cy + dy, comic.stroke)
        }
    }

    private fun kettle(c: Canvas, cx: Float, cy: Float, frame: Int) {
        steam(c, cx - 22, cy - 18, frame)
        comic.sticker(c, Path().apply { moveTo(cx - 14, cy - 4); lineTo(cx - 28, cy - 16); lineTo(cx - 24, cy - 2); close() }, 0xFF4FA3F0.toInt(), 2.5f)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 8f
        c.drawPath(Path().apply { moveTo(cx + 10, cy - 18); cubicTo(cx + 30, cy - 18, cx + 30, cy + 10, cx + 14, cy + 12) }, comic.stroke)
        comic.stroke.color = 0xFF4FA3F0.toInt()
        comic.stroke.strokeWidth = 3.5f
        c.drawPath(Path().apply { moveTo(cx + 10, cy - 18); cubicTo(cx + 30, cy - 18, cx + 30, cy + 10, cx + 14, cy + 12) }, comic.stroke)
        comic.sticker(c, Path().apply {
            moveTo(cx - 12, cy - 20); lineTo(cx + 12, cy - 20); lineTo(cx + 18, cy + 20); lineTo(cx - 18, cy + 20); close()
        }, 0xFF4FA3F0.toInt(), 3f, shadow = 2.5f)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 8, cy - 26, cx + 8, cy - 20), 3f, 3f, Path.Direction.CW) }, INK, 2f)
        comic.sticker(c, Path().apply { addCircle(cx, cy + 6, 4f, Path.Direction.CW) }, if (frame % 2 == 0) 0xFFF2564B.toInt() else 0xFFFFE135.toInt(), 2f)
    }

    private fun steam(c: Canvas, x: Float, y: Float, frame: Int) {
        val sway = if (frame % 2 == 0) 4f else -4f
        for (dx in listOf(0f, 9f)) {
            val curl = Path().apply { moveTo(x + dx, y); cubicTo(x + dx - sway, y - 6, x + dx + sway, y - 10, x + dx, y - 18) }
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 5f
            c.drawPath(curl, comic.stroke)
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 2.5f
            c.drawPath(curl, comic.stroke)
        }
    }

    /** Espresso cup on its saucer, steaming. */
    fun espresso(c: Canvas, cx: Float, cy: Float, frame: Int) {
        steam(c, cx - 8, cy - 10, frame)
        comic.sticker(c, Path().apply { addOval(RectF(cx - 26, cy + 14, cx + 26, cy + 24), Path.Direction.CW) }, Color.WHITE, 2.5f, shadow = 2f)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 7f
        c.drawPath(Path().apply { moveTo(cx + 14, cy - 4); cubicTo(cx + 26, cy - 4, cx + 26, cy + 10, cx + 12, cy + 10) }, comic.stroke)
        comic.stroke.color = Color.WHITE
        comic.stroke.strokeWidth = 3f
        c.drawPath(Path().apply { moveTo(cx + 14, cy - 4); cubicTo(cx + 26, cy - 4, cx + 26, cy + 10, cx + 12, cy + 10) }, comic.stroke)
        comic.sticker(c, Path().apply {
            moveTo(cx - 17, cy - 8); lineTo(cx + 17, cy - 8); lineTo(cx + 15, cy + 8); quadTo(cx, cy + 22, cx - 15, cy + 8); close()
        }, Color.WHITE, 3f)
        comic.fill.color = 0xFF6B3E1F.toInt()
        c.drawOval(RectF(cx - 15, cy - 10, cx + 15, cy - 4), comic.fill)
    }

    /* ---------- Power zone characters ---------- */

    fun character(c: Canvas, cx: Float, cy: Float, character: ZoneCharacter, frame: Int) {
        if (character == ZoneCharacter.ROCKET) return rocket(c, cx, cy, frame)
        val skin = when (character) {
            ZoneCharacter.SLOTH -> 0xFFB49A7E.toInt()
            ZoneCharacter.RED_FACE -> 0xFFFF6B5B.toInt()
            ZoneCharacter.MONSTER -> 0xFF8BD13F.toInt()
            ZoneCharacter.NO_TALKING -> 0xFFFFB38A.toInt()
            else -> 0xFFFFD6A0.toInt()
        }
        val r = 22f
        comic.sticker(c, Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }, skin, 3f, shadow = 2.5f)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 2.5f
        comic.fill.color = INK
        when (character) {
            ZoneCharacter.SLOTH -> {
                comic.fill.color = 0xFF5A4636.toInt()
                for (side in listOf(-1f, 1f)) {
                    c.save()
                    c.rotate(side * 20f, cx + side * 9, cy - 2)
                    c.drawOval(RectF(cx + side * 9 - 7, cy - 7, cx + side * 9 + 7, cy + 3), comic.fill)
                    c.restore()
                }
                comic.stroke.color = Color.WHITE
                comic.stroke.strokeWidth = 2f
                c.drawLine(cx - 13, cy - 2, cx - 5, cy - 2, comic.stroke)
                c.drawLine(cx + 5, cy - 2, cx + 13, cy - 2, comic.stroke)
                comic.stroke.color = INK
                c.drawArc(RectF(cx - 6, cy + 4, cx + 6, cy + 12), 20f, 140f, false, comic.stroke)
                comic.plain(c, "z", cx + 18, cy - 18 - (frame % 3) * 4f, 14f, Color.WHITE)
            }
            ZoneCharacter.CHATTY -> {
                c.drawCircle(cx - 8, cy - 5, 2.5f, comic.fill)
                c.drawCircle(cx + 8, cy - 5, 2.5f, comic.fill)
                comic.fill.color = 0xFF7A1F1F.toInt()
                c.drawPath(Path().apply { moveTo(cx - 10, cy + 4); quadTo(cx, cy + (if (frame % 2 == 0) 18 else 12), cx + 10, cy + 4); close() }, comic.fill)
            }
            ZoneCharacter.TEMPO -> {
                comic.sticker(c, Path().apply { addRect(cx - r, cy - 16, cx + r, cy - 10, Path.Direction.CW) }, 0xFFF2564B.toInt(), 2f)
                c.drawLine(cx - 13, cy - 6, cx - 4, cy - 3, comic.stroke)
                c.drawLine(cx + 13, cy - 6, cx + 4, cy - 3, comic.stroke)
                c.drawCircle(cx - 8, cy, 2.5f, comic.fill)
                c.drawCircle(cx + 8, cy, 2.5f, comic.fill)
                c.drawLine(cx - 7, cy + 10, cx + 7, cy + 10, comic.stroke)
            }
            ZoneCharacter.NO_TALKING -> {
                c.drawLine(cx - 12, cy - 4, cx - 4, cy - 4, comic.stroke)
                c.drawLine(cx + 4, cy - 4, cx + 12, cy - 4, comic.stroke)
                c.drawLine(cx - 9, cy + 8, cx + 9, cy + 8, comic.stroke)
                for (x in listOf(cx - 5, cx, cx + 5)) c.drawLine(x, cy + 5, x, cy + 11, comic.stroke)
            }
            ZoneCharacter.RED_FACE -> {
                c.drawPath(Path().apply { moveTo(cx - 14, cy - 9); lineTo(cx - 5, cy - 4); lineTo(cx - 14, cy + 1) }, comic.stroke)
                c.drawPath(Path().apply { moveTo(cx + 14, cy - 9); lineTo(cx + 5, cy - 4); lineTo(cx + 14, cy + 1) }, comic.stroke)
                comic.fill.color = 0xFF7A1F1F.toInt()
                c.drawOval(RectF(cx - 6, cy + 5, cx + 6, cy + 14), comic.fill)
                sweat(c, cx + r + 4, cy - 14 + (frame % 2) * 6, 1f)
                sweat(c, cx - r - 4, cy - 4 + ((frame + 1) % 2) * 6, 0.8f)
            }
            ZoneCharacter.MONSTER -> {
                c.drawLine(cx - 14, cy - 10, cx - 4, cy - 5, comic.stroke)
                c.drawLine(cx + 14, cy - 10, cx + 4, cy - 5, comic.stroke)
                comic.fill.color = 0xFFFFE135.toInt()
                c.drawCircle(cx - 8, cy - 1, 3.5f, comic.fill)
                c.drawCircle(cx + 8, cy - 1, 3.5f, comic.fill)
                comic.sticker(c, Path().apply {
                    moveTo(cx - 12, cy + 6); lineTo(cx - 8, cy + 12); lineTo(cx - 4, cy + 6); lineTo(cx, cy + 12)
                    lineTo(cx + 4, cy + 6); lineTo(cx + 8, cy + 12); lineTo(cx + 12, cy + 6); close()
                }, Color.WHITE, 2f)
                if (frame % 2 == 0) for (side in listOf(-1f, 1f)) {
                    comic.sticker(c, Path().apply { addCircle(cx + side * (r + 7), cy - 8, 5f, Path.Direction.CW) }, 0xFFE8E1EE.toInt(), 2f)
                }
            }
            ZoneCharacter.ROCKET -> Unit
        }
    }

    private fun rocket(c: Canvas, cx: Float, cy: Float, frame: Int) {
        c.save()
        c.translate(cx, cy)
        c.rotate(40f)
        val flame = if (frame % 2 == 0) 1f else 0.75f
        comic.sticker(c, star(0f, 30f, 15f * flame, 8f * flame, 7), 0xFFFF9F1C.toInt(), 2.5f)
        comic.sticker(c, star(0f, 28f, 8f * flame, 4f * flame, 6), 0xFFFFE135.toInt(), 2f)
        comic.sticker(c, Path().apply { moveTo(-9f, 8f); lineTo(-17f, 20f); lineTo(-9f, 18f); close() }, 0xFFF2564B.toInt(), 2.5f)
        comic.sticker(c, Path().apply { moveTo(9f, 8f); lineTo(17f, 20f); lineTo(9f, 18f); close() }, 0xFFF2564B.toInt(), 2.5f)
        comic.sticker(c, Path().apply { moveTo(0f, -30f); cubicTo(11f, -20f, 12f, -2f, 10f, 18f); lineTo(-10f, 18f); cubicTo(-12f, -2f, -11f, -20f, 0f, -30f); close() }, Color.WHITE, 3f, shadow = 2.5f)
        comic.sticker(c, Path().apply { moveTo(0f, -30f); cubicTo(6f, -25f, 8f, -18f, 8f, -14f); lineTo(-8f, -14f); cubicTo(-8f, -18f, -6f, -25f, 0f, -30f); close() }, 0xFFF2564B.toInt(), 2.5f)
        comic.sticker(c, Path().apply { addCircle(0f, -2f, 5f, Path.Direction.CW) }, 0xFF7EC8FF.toInt(), 2f)
        c.restore()
    }

    /* ---------- % FTP pot ---------- */

    fun pot(c: Canvas, cx: Float, cy: Float, pot: Pot, frame: Int) {
        if (pot != Pot.LUKEWARM) for (i in 0 until 3) {
            val x = cx - 16 + i * 16
            val r = 6f + ((frame + i) % 2) * 2
            comic.sticker(c, star(x, cy + 30, r, r * 0.5f, 5), 0xFFFF9F1C.toInt(), 1.5f)
        }
        if (pot >= Pot.SIMMERING) steam(c, cx - 4, cy - 12, frame)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 5f
        c.drawLine(cx - 34, cy + 2, cx - 26, cy + 2, comic.stroke)
        c.drawLine(cx + 26, cy + 2, cx + 34, cy + 2, comic.stroke)
        comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 28, cy - 4, cx + 28, cy + 24), 6f, 6f, Path.Direction.CW) }, 0xFF9AA6B4.toInt(), 3f, shadow = 2.5f)
        if (pot == Pot.BOILING_OVER) {
            comic.sticker(c, Path().apply {
                moveTo(cx - 30, cy - 2); cubicTo(cx - 26, cy - 14, cx - 12, cy - 12, cx - 8, cy - 6); cubicTo(cx - 2, cy - 16, cx + 12, cy - 14, cx + 14, cy - 6)
                cubicTo(cx + 20, cy - 12, cx + 32, cy - 10, cx + 30, cy - 2); lineTo(cx + 30, cy + 8 + (frame % 3) * 3); quadTo(cx + 26, cy + 14, cx + 24, cy + 2)
                lineTo(cx - 22, cy + 2); quadTo(cx - 24, cy + 16, cx - 30, cy + 8); close()
            }, Color.WHITE, 2.5f)
        }
        val (lift, tilt) = when (pot) {
            Pot.BOILING_OVER -> (if (frame % 2 == 0) 22f else 14f) to (if (frame % 2 == 0) -22f else -12f)
            Pot.BUBBLING -> (if (frame % 2 == 0) 4f else 0f) to (if (frame % 2 == 0) -6f else 4f)
            else -> 0f to 0f
        }
        c.save()
        c.translate(cx + lift / 3, cy - 4 - lift)
        c.rotate(tilt)
        comic.sticker(c, Path().apply { moveTo(-30f, 0f); lineTo(30f, 0f); cubicTo(26f, -12f, -26f, -12f, -30f, 0f); close() }, 0xFFC7CFD8.toInt(), 3f)
        comic.sticker(c, Path().apply { addCircle(0f, -10f, 4f, Path.Direction.CW) }, INK, 2f)
        c.restore()
        if (pot == Pot.BUBBLING) for ((dx, dy) in listOf(-14f to -12f, 12f to -14f)) {
            comic.sticker(c, Path().apply { addCircle(cx + dx, cy + dy - (frame % 2) * 4, 3.5f, Path.Direction.CW) }, Color.WHITE, 2f)
        }
    }

    /* ---------- Food ---------- */

    fun food(c: Canvas, cx: Float, cy: Float, food: Food, frame: Int) {
        val hop = if (frame % 2 == 1) -3f else 0f
        when (food) {
            Food.PRETZEL -> comic.pretzel(c, cx, cy, 0.6f, frame)
            Food.BRATWURST -> {
                for ((dy, dx) in listOf(-8f to 2f, 8f to -2f)) {
                    comic.sticker(c, Path().apply { addRoundRect(RectF(cx - 30 + dx, cy + dy - 7 + hop, cx + 30 + dx, cy + dy + 7 + hop), 7f, 7f, Path.Direction.CW) }, 0xFFC0602E.toInt(), 3f)
                    comic.stroke.color = 0xFF7A3416.toInt()
                    comic.stroke.strokeWidth = 2.5f
                    for (x in listOf(-16f, 0f, 16f)) c.drawLine(cx + x + dx - 3, cy + dy - 4 + hop, cx + x + dx + 3, cy + dy + 4 + hop, comic.stroke)
                }
                comic.stroke.color = 0xFFFFD23F.toInt()
                comic.stroke.strokeWidth = 3f
                c.drawPath(Path().apply {
                    moveTo(cx - 24, cy - 8 + hop); quadTo(cx - 16, cy - 14 + hop, cx - 8, cy - 8 + hop); quadTo(cx, cy - 2 + hop, cx + 8, cy - 8 + hop)
                    quadTo(cx + 16, cy - 14 + hop, cx + 24, cy - 8 + hop)
                }, comic.stroke)
            }
            Food.PIZZA -> {
                comic.sticker(c, Path().apply { moveTo(cx - 26, cy - 22 + hop); quadTo(cx, cy - 32 + hop, cx + 26, cy - 22 + hop); lineTo(cx, cy + 28 + hop); close() }, 0xFFFFD23F.toInt(), 3f, shadow = 2.5f)
                comic.sticker(c, Path().apply {
                    moveTo(cx - 26, cy - 22 + hop); quadTo(cx, cy - 32 + hop, cx + 26, cy - 22 + hop); lineTo(cx + 22, cy - 14 + hop)
                    quadTo(cx, cy - 22 + hop, cx - 22, cy - 14 + hop); close()
                }, 0xFFD9963B.toInt(), 2.5f)
                for ((x, y) in listOf(-8f to -6f, 8f to -4f, 0f to 8f)) {
                    comic.sticker(c, Path().apply { addCircle(cx + x, cy + y + hop, 4.5f, Path.Direction.CW) }, 0xFFE04A3A.toInt(), 2f)
                }
            }
            Food.DONER -> {
                comic.fill.color = 0xFF3FBF66.toInt()
                for (x in listOf(-18f, -8f, 2f, 12f, 20f)) c.drawCircle(cx + x, cy - 12 + hop, 6f, comic.fill)
                comic.fill.color = 0xFFE04A3A.toInt()
                c.drawCircle(cx - 4, cy - 14 + hop, 4f, comic.fill)
                c.drawCircle(cx + 12, cy - 16 + hop, 4f, comic.fill)
                comic.stroke.color = 0xFF8A4A22.toInt()
                comic.stroke.strokeWidth = 4f
                c.drawLine(cx - 16, cy - 8 + hop, cx + 16, cy - 10 + hop, comic.stroke)
                comic.sticker(c, Path().apply {
                    moveTo(cx - 28, cy - 10 + hop); quadTo(cx - 30, cy + 26 + hop, cx, cy + 26 + hop); quadTo(cx + 30, cy + 26 + hop, cx + 28, cy - 10 + hop)
                    quadTo(cx, cy + 4 + hop, cx - 28, cy - 10 + hop); close()
                }, 0xFFE3B36A.toInt(), 3f, shadow = 2.5f)
            }
            Food.WEISSBIER -> {
                comic.sticker(c, Path().apply {
                    moveTo(cx - 10, cy + 26); lineTo(cx + 10, cy + 26); cubicTo(cx + 8, cy + 10, cx + 16, cy - 8, cx + 14, cy - 22)
                    lineTo(cx - 14, cy - 22); cubicTo(cx - 16, cy - 8, cx - 8, cy + 10, cx - 10, cy + 26); close()
                }, 0xFFFFB627.toInt(), 3f, shadow = 2.5f)
                comic.fill.color = 0xFFFFF3B0.toInt()
                for ((x, off) in listOf(-4f to 0, 4f to 7)) c.drawCircle(cx + x, cy + 18 - ((frame * 6 + off) % 30), 2f, comic.fill)
                comic.sticker(c, Path().apply {
                    moveTo(cx - 16, cy - 20); cubicTo(cx - 20, cy - 32, cx - 6, cy - 38, cx, cy - 30); cubicTo(cx + 6, cy - 38, cx + 20, cy - 32, cx + 16, cy - 20); close()
                }, Color.WHITE, 2.5f)
            }
        }
    }

    /* ---------- Climbing ---------- */

    /** Little climber with a backpack walking left, legs stepping every frame. */
    fun climber(c: Canvas, x: Float, y: Float, frame: Int) {
        c.save()
        c.scale(-1f, 1f, x, y)
        val step = if (frame % 2 == 0) 4f else -4f
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(x, y - 12, x - 2, y - 2, comic.stroke)
        c.drawLine(x - 2, y - 2, x - 6 + step, y + 8, comic.stroke)
        c.drawLine(x - 2, y - 2, x + 4 - step, y + 8, comic.stroke)
        c.drawLine(x, y - 9, x + 7, y - 4 + step / 2, comic.stroke)
        comic.sticker(c, Path().apply { addRoundRect(RectF(x - 9, y - 13, x - 2, y - 4), 2f, 2f, Path.Direction.CW) }, 0xFF3FBF66.toInt(), 2f)
        comic.sticker(c, Path().apply { addCircle(x + 1, y - 16, 4.5f, Path.Direction.CW) }, 0xFFFFD6A0.toInt(), 2f)
        c.restore()
    }

    fun confetti(c: Canvas, w: Float, h: Float, frame: Int) {
        for (i in 0 until 16) {
            val x = (i * 47 + (frame % 2) * 4) % w.toInt() + 2f
            val y = ((i * 29 + frame * 14) % h.toInt()).toFloat()
            c.save()
            c.rotate(frame * 40f + i * 31, x, y)
            comic.sticker(c, Path().apply { addRect(x - 3, y - 5, x + 3, y + 5, Path.Direction.CW) }, CONFETTI[i % CONFETTI.size], 1.5f)
            c.restore()
        }
    }

    /* ---------- Weather and sky ---------- */

    /** Water along the bottom edge with two rubber ducks bobbing at the corners, leaving the middle to the caption. */
    fun ducks(c: Canvas, w: Float, h: Float, frame: Int) {
        val water = Path().apply {
            moveTo(-4f, h - 16)
            var x = -4f
            while (x < w + 4) {
                quadTo(x + 10, h - 22 + (frame % 2) * 4, x + 20, h - 16)
                x += 20
            }
            lineTo(w + 4, h + 4); lineTo(-4f, h + 4); close()
        }
        comic.sticker(c, water, 0xFF4FA3F0.toInt(), 2.5f)
        duck(c, 22f, h - 22 + (frame % 2) * 2f, mirrored = false)
        duck(c, w - 24, h - 22 + ((frame + 1) % 2) * 2f, mirrored = true)
    }

    private fun duck(c: Canvas, x: Float, y: Float, mirrored: Boolean) {
        c.save()
        c.translate(x, y)
        if (mirrored) c.scale(-1f, 1f)
        comic.sticker(c, Path().apply { moveTo(-14f, 2f); cubicTo(-14f, -8f, -4f, -10f, 4f, -4f); lineTo(12f, -6f); cubicTo(16f, 4f, 10f, 10f, 0f, 10f); cubicTo(-10f, 10f, -14f, 8f, -14f, 2f); close() }, 0xFFFFD23F.toInt(), 2.5f)
        comic.sticker(c, Path().apply { addCircle(5f, -12f, 7f, Path.Direction.CW) }, 0xFFFFD23F.toInt(), 2.5f)
        comic.sticker(c, Path().apply { moveTo(11f, -13f); lineTo(19f, -11f); lineTo(11f, -9f); close() }, 0xFFFF9F1C.toInt(), 2f)
        comic.fill.color = INK
        c.drawCircle(7f, -14f, 1.5f, comic.fill)
        c.restore()
    }

    /** Face in a strong headwind: eyes wide, hair blown straight back. */
    fun blowDry(c: Canvas, cx: Float, cy: Float, frame: Int) {
        val flutter = if (frame % 2 == 0) 4f else -4f
        comic.stroke.color = 0xFFB8742A.toInt()
        comic.stroke.strokeWidth = 4f
        for ((i, dy) in listOf(-16f, -10f, -4f).withIndex()) {
            c.drawPath(Path().apply {
                moveTo(cx + 10, cy + dy); cubicTo(cx + 22, cy + dy - 4 + flutter, cx + 30, cy + dy + 4 - flutter, cx + 40 + i * 4, cy + dy - 2)
            }, comic.stroke)
        }
        comic.sticker(c, Path().apply { addCircle(cx, cy, 18f, Path.Direction.CW) }, 0xFFFFD6A0.toInt(), 3f, shadow = 2.5f)
        comic.fill.color = Color.WHITE
        c.drawCircle(cx - 7, cy - 3, 5f, comic.fill)
        c.drawCircle(cx + 5, cy - 3, 5f, comic.fill)
        comic.fill.color = INK
        c.drawCircle(cx - 8, cy - 3, 2f, comic.fill)
        c.drawCircle(cx + 4, cy - 3, 2f, comic.fill)
        comic.fill.color = 0xFF7A1F1F.toInt()
        c.drawOval(RectF(cx - 4, cy + 5, cx + 4, cy + 12), comic.fill)
        comic.stroke.color = Color.WHITE
        comic.stroke.strokeWidth = 3f
        for (dy in listOf(-12f, 0f, 12f)) c.drawLine(cx - 30 - flutter, cy + dy, cx - 22, cy + dy, comic.stroke)
    }

    /** Bats fluttering across the night sky below the field name, wings up and down. */
    fun bats(c: Canvas, w: Float, frame: Int) {
        for ((i, y) in listOf(54f, 66f, 60f).withIndex()) {
            val x = ((i * 83 + frame * (10 + i * 4)) % (w.toInt() + 40)) - 20f
            val up = (frame + i) % 2 == 0
            val wing = if (up) -8f else 4f
            comic.fill.color = INK
            c.drawPath(Path().apply {
                moveTo(x, y); quadTo(x - 6, y + wing, x - 14, y + wing - 2); quadTo(x - 10, y + 2, x - 12, y + 6)
                quadTo(x - 6, y + 2, x, y + 5); quadTo(x + 6, y + 2, x + 12, y + 6); quadTo(x + 10, y + 2, x + 14, y + wing - 2)
                quadTo(x + 6, y + wing, x, y); close()
            }, comic.fill)
        }
    }

    /** One drop of sweat; [k] scales it. */
    fun sweat(c: Canvas, x: Float, y: Float, k: Float) {
        comic.sticker(c, Path().apply {
            moveTo(x, y); cubicTo(x + 4 * k, y + 6 * k, x + 6 * k, y + 9 * k, x + 6 * k, y + 12 * k)
            cubicTo(x + 6 * k, y + 16 * k, x - 6 * k, y + 16 * k, x - 6 * k, y + 12 * k); cubicTo(x - 6 * k, y + 9 * k, x - 4 * k, y + 6 * k, x, y); close()
        }, 0xFF7EC8FF.toInt(), 2f)
    }

    /** Off the route: two arrows chasing each other in a circle. */
    fun recalculating(c: Canvas, cx: Float, cy: Float, frame: Int) {
        c.save()
        c.rotate(frame * 45f, cx, cy)
        for (start in listOf(20f, 200f)) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 9f
            c.drawArc(RectF(cx - 20, cy - 20, cx + 20, cy + 20), start, 130f, false, comic.stroke)
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 4f
            c.drawArc(RectF(cx - 20, cy - 20, cx + 20, cy + 20), start, 130f, false, comic.stroke)
            val a = Math.toRadians((start + 130).toDouble())
            val tipX = cx + 20 * cos(a).toFloat()
            val tipY = cy + 20 * sin(a).toFloat()
            c.save()
            c.rotate(start + 130 + 90, tipX, tipY)
            comic.sticker(c, Path().apply { moveTo(tipX - 7, tipY - 4); lineTo(tipX + 7, tipY - 4); lineTo(tipX, tipY + 8); close() }, Color.WHITE, 2f)
            c.restore()
        }
        c.restore()
    }

    private fun star(cx: Float, cy: Float, rOut: Float, rIn: Float, points: Int) = comic.star(cx, cy, rOut, rIn, points)

    private companion object {
        val CONFETTI = intArrayOf(0xFFF2564B.toInt(), 0xFF4FA3F0.toInt(), 0xFFFFE135.toInt(), 0xFFA884F3.toInt(), Color.WHITE, 0xFFFF9F1C.toInt())
    }
}

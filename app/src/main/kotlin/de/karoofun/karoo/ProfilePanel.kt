package de.karoofun.karoo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.max

/** Grade bands in percent, from downhill to very steep. The legend, the fill and the rider's comments share this table. */
enum class GradeBand(val from: Double, val color: Int, val label: String) {
    DOWN(Double.NEGATIVE_INFINITY, 0xFF3B8CFF.toInt(), "<0"),
    FLAT(0.0, 0xFF3DDC6A.toInt(), "0-3"),
    MODERATE(3.0, 0xFFF5D324.toInt(), "3-6"),
    HARD(6.0, 0xFFFF9A1F.toInt(), "6-9"),
    STEEP(9.0, 0xFFFF4D3D.toInt(), "9-12"),
    WALL(12.0, 0xFFD23CFF.toInt(), "12+"),
    ;

    companion object {
        fun of(grade: Double): GradeBand = entries.last { grade >= it.from }
    }
}

/**
 * The ridden elevation profile in comic style: grade-colored mountains under a sky, an ink outline,
 * a summit flag and the rider on the slope. Above it elevation, ascent and grade, big enough to read on gravel.
 *
 * Drawing is split in two passes so the halftone dots can be printed between them:
 * [drawFills] for the colored areas, [drawOverlay] for lines, labels and the rider.
 */
class ProfilePanel(private val comic: Comic, private val texts: DashboardTexts, private val units: Units) {

    private class Geometry(val chart: RectF, val points: List<ProfilePoint>, val start: Double, val span: Double, val top: Double, val bottom: Double) {
        fun x(i: Int) = chart.left + ((points[i].distance - start) / span * chart.width()).toFloat()
        fun y(i: Int) = chart.bottom - ((points[i].elevation - bottom) / (top - bottom) * chart.height()).toFloat()
    }

    private fun geometry(area: RectF, points: List<ProfilePoint>): Geometry {
        val chart = RectF(area.left + 14, area.top + area.height() * CHART_TOP, area.right - 14, area.bottom - 16)
        val start = points.first().distance
        // Early in the ride the line grows from the left instead of stretching a few meters across the tile
        val span = max(points.last().distance - start, MIN_SPAN)
        val lowest = points.minOf { it.elevation }
        val highest = points.maxOf { it.elevation }
        // Flat rides would otherwise blow up barometer noise into mountains
        val range = max(highest - lowest, MIN_RANGE)
        val top = highest + (range - (highest - lowest)) / 2 + range * 0.08
        return Geometry(chart, points, start, span, top, top - range * 1.14)
    }

    fun drawFills(c: Canvas, area: RectF, data: RideSnapshot, grades: DoubleArray, frame: Int) {
        // Clouds drifting through the sky, behind the mountains
        for ((i, cloud) in listOf(Triple(60f, 0.5f, 0.9f), Triple(300f, 0.47f, 0.7f), Triple(400f, 0.55f, 0.8f)).withIndex()) {
            val (x0, y, scale) = cloud
            val x = (x0 + frame * (3 + i)) % (area.width() + 40) - 20
            drawCloud(c, area.left + x, area.top + area.height() * y, scale)
        }
        val points = data.points
        if (points.size < 2) return
        val g = geometry(area, points)
        // Area: one flat-colored path per run of equal grade band
        var runStart = 1
        while (runStart < points.size) {
            val band = GradeBand.of(grades[runStart])
            var runEnd = runStart
            while (runEnd + 1 < points.size && GradeBand.of(grades[runEnd + 1]) == band) runEnd++
            val path = Path()
            path.moveTo(g.x(runStart - 1), g.chart.bottom)
            for (i in runStart - 1..runEnd) path.lineTo(g.x(i), g.y(i))
            path.lineTo(g.x(runEnd), g.chart.bottom)
            path.close()
            comic.fill.color = band.color
            c.drawPath(path, comic.fill)
            runStart = runEnd + 1
        }
    }

    fun drawOverlay(c: Canvas, area: RectF, data: RideSnapshot, grades: DoubleArray, frame: Int) {
        val points = data.points
        if (points.size < 2) {
            drawWaiting(c, area, data)
            return
        }
        val g = geometry(area, points)
        val last = points.lastIndex
        val grade = data.grade ?: grades.last()
        val band = GradeBand.of(grade)

        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(g.chart.left, g.chart.bottom, g.chart.right, g.chart.bottom, comic.stroke)
        val line = Path().apply {
            moveTo(g.x(0), g.y(0))
            for (i in 1..last) lineTo(g.x(i), g.y(i))
        }
        comic.stroke.strokeWidth = 4f
        c.drawPath(line, comic.stroke)

        val summit = points.indices.maxBy { points[it].elevation }
        if (summit < last - 2) drawSummit(c, g, summit, frame)
        drawRider(c, g, grade, frame)
        drawStats(c, area, data, grade, band, frame)
    }

    private fun drawSummit(c: Canvas, g: Geometry, i: Int, frame: Int) {
        val fx = g.x(i)
        val fy = g.y(i)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(fx, fy, fx, fy - 30, comic.stroke)
        val flag = Path().apply {
            moveTo(fx, fy - 30)
            if (frame % 2 == 0) {
                lineTo(fx + 20, fy - 26); lineTo(fx, fy - 19)
            } else {
                quadTo(fx + 12, fy - 32, fx + 20, fy - 25); quadTo(fx + 10, fy - 23, fx, fy - 19)
            }
            close()
        }
        comic.sticker(c, flag, 0xFFFF4D3D.toInt(), 2.5f)
    }

    private fun drawRider(c: Canvas, g: Geometry, grade: Double, frame: Int) {
        val last = g.points.lastIndex
        val k = max(0, last - 3)
        val slope = Math.toDegrees(atan2((g.y(last) - g.y(k)).toDouble(), (g.x(last) - g.x(k)).toDouble())).toFloat()
        val angle = slope.coerceIn(-28f, 28f)
        val bounce = if (frame % 2 == 1) -3f else 0f
        c.save()
        c.translate(g.x(last), g.y(last) + bounce)
        c.rotate(angle)
        if (grade < 0) {
            comic.stroke.color = Color.WHITE
            comic.stroke.strokeWidth = 4f
            val shift = (frame % 3) * 6f
            c.drawLine(-70 + shift, -14f, -48 + shift, -14f, comic.stroke)
            c.drawLine(-62 + shift, -4f, -46 + shift, -4f, comic.stroke)
        }
        c.save()
        c.translate(-50f, -32f)
        comic.bike(c, frame)
        c.restore()
        if (grade >= 6 && frame % 2 == 0) {
            comic.stroke.color = INK
            comic.stroke.strokeWidth = 2.5f
            c.drawLine(-30f, -36f, -36f, -41f, comic.stroke)
            c.drawLine(-22f, -42f, -24f, -49f, comic.stroke)
            c.drawLine(-12f, -40f, -9f, -47f, comic.stroke)
        }
        c.restore()
        comic.sticker(c, Path().apply { addCircle(g.x(last), g.y(last), 6f, Path.Direction.CW) }, Color.WHITE, 3f)
    }

    /** Field name, then elevation, ascent and the grade badge as big as the width allows. */
    private fun drawStats(c: Canvas, area: RectF, data: RideSnapshot, grade: Double, band: GradeBand, frame: Int) {
        tag(c, area)
        val elevationText = units.elevation(data.elevation ?: data.points.last().elevation)
        val ascentText = data.ascent?.let { units.elevation(it) } ?: "–"
        val gradeText = units.grade(grade)

        var size = STAT_SIZE
        fun badgeSize(s: Float) = s * 0.82f
        fun rowWidth(s: Float) = comic.bigNumberWidth(elevationText, s, units.elevationUnit) +
            comic.measure(ARROW, s * 0.45f) + comic.bigNumberWidth(ascentText, s) +
            comic.bigNumberWidth(gradeText, badgeSize(s)) + 28 + 3 * 18
        while (size > 24 && rowWidth(size) > area.width()) size -= 2f
        val baseline = area.top + 50 + size * 0.72f

        val left = area.left + 16
        val elevationWidth = comic.bigNumberWidth(elevationText, size, units.elevationUnit)
        comic.bigNumber(c, elevationText, left + elevationWidth / 2, baseline, size, unit = units.elevationUnit)

        // Grade badge in the band color; it wobbles when it gets steep
        val badgeText = badgeSize(size)
        val badgeWidth = comic.bigNumberWidth(gradeText, badgeText) + 28
        val badge = RectF(area.right - 14 - badgeWidth, baseline - badgeText * 0.72f - 12, area.right - 14, baseline + 12)
        c.save()
        c.rotate(if (grade >= 9) (if (frame % 2 == 0) 4f else -4f) else 2f, badge.centerX(), badge.centerY())
        comic.sticker(c, Path().apply { addRoundRect(badge, badge.height() / 2, badge.height() / 2, Path.Direction.CW) }, band.color, 3.5f, shadow = 4f)
        comic.bigNumber(c, gradeText, badge.centerX(), baseline, badgeText)
        c.restore()

        val elevationEnd = left + elevationWidth
        val arrowWidth = comic.measure(ARROW, size * 0.45f)
        val ascentWidth = arrowWidth + 4 + comic.bigNumberWidth(ascentText, size)
        val ascentX = elevationEnd + (badge.left - elevationEnd - ascentWidth) / 2
        comic.outlined(c, ARROW, ascentX, baseline, size * 0.45f)
        comic.bigNumber(c, ascentText, ascentX + arrowWidth + 4 + comic.bigNumberWidth(ascentText, size) / 2, baseline, size)
    }

    private fun tag(c: Canvas, area: RectF) {
        c.save()
        c.translate(area.left, area.top)
        comic.fieldTag(c, texts.profile)
        c.restore()
    }

    private fun drawWaiting(c: Canvas, area: RectF, data: RideSnapshot) {
        tag(c, area)
        val mid = area.centerY()
        data.elevation?.let { comic.bigNumber(c, units.elevation(it), area.centerX(), mid + 10, STAT_SIZE, unit = units.elevationUnit) }
        val width = comic.measure(texts.waiting, 20f, comic.hand) + 24
        comic.bubble(c, texts.waiting, area.centerX() - width / 2, mid + 36, size = 20f)
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, scale: Float) {
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        val cloud = Path().apply {
            moveTo(10f, 29f)
            cubicTo(0f, 29f, 0f, 17f, 10f, 17f)
            cubicTo(10f, 5f, 28f, 3f, 32f, 13f)
            cubicTo(38f, 3f, 56f, 7f, 54f, 19f)
            cubicTo(66f, 19f, 66f, 29f, 56f, 29f)
            close()
        }
        comic.sticker(c, cloud, Color.WHITE, 2.5f)
        c.restore()
    }

    private companion object {
        const val MIN_SPAN = 2_000.0
        const val MIN_RANGE = 40.0
        const val CHART_TOP = 0.45f
        const val STAT_SIZE = 64f
        const val ARROW = "▲"
    }
}


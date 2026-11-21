package de.karoofun.karoo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Localized texts of the radar tile. */
data class RadarTexts(
    val title: String,
    val now: String,
    val forecast: String,
    val loading: String,
    val waitingForGps: String,
    val outside: String,
    val outsideHint: String,
    val staysDry: String,
    val staysWet: String,
    val dryUntil: String,
    val wetUntil: String,
    val rainFrom: String,
    val dryFrom: String,
    val offline: String,
)

/**
 * Where things sit on the tile. Shared with the tap areas in radar_view.xml: the +/- buttons are drawn in
 * the right quarter, centered in the bands [BUTTONS_TOP]..[BUTTONS_MIDDLE] and [BUTTONS_MIDDLE]..[BUTTONS_BOTTOM]
 * of the height, and the footer with the timeline is the band below, which are exactly the layout weights there.
 * A tap anywhere on the timeline steps one frame further.
 */
class RadarLayout(val width: Int, val height: Int) {
    val header = HEADER
    val footer = if (height >= MIN_FOOTER_HEIGHT) (height * (1 - BUTTONS_BOTTOM)).roundToInt() else 0
    val map = RectF(0f, header.toFloat(), width.toFloat(), (height - footer).toFloat())
    val mapWidth get() = map.width().roundToInt()
    val mapHeight get() = map.height().roundToInt()
    val buttonX = width * 0.875f
    val zoomInY = height * (BUTTONS_TOP + BUTTONS_MIDDLE) / 2
    val zoomOutY = height * (BUTTONS_MIDDLE + BUTTONS_BOTTOM) / 2
    val buttonRadius = minOf(BUTTON_RADIUS, height * (BUTTONS_BOTTOM - BUTTONS_MIDDLE) * 0.42f)

    fun timelineX(minutes: Int) = TIMELINE_MARGIN + minutes / 60f * (width - 2 * TIMELINE_MARGIN)

    companion object {
        const val HEADER = 52
        const val TIMELINE_MARGIN = 28f
        const val MIN_FOOTER_HEIGHT = 420
        const val BUTTONS_TOP = 0.58f
        const val BUTTONS_MIDDLE = 0.725f
        const val BUTTONS_BOTTOM = 0.85f
        const val BUTTON_RADIUS = 25f
    }
}

class RadarRenderer(private val texts: RadarTexts) {
    private val comic = Comic()
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clock = SimpleDateFormat("HH:mm", Locale.GERMANY)

    /** [selected] is the frame picked on the timeline; without one the animation runs. */
    fun render(layout: RadarLayout, state: RadarState, zoom: RadarZoom, rider: RideSnapshot, tick: Int, millis: Long, selected: Int? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, layout.width.toFloat(), layout.height.toFloat()), CORNER, CORNER, Path.Direction.CW) })
        c.drawColor(PAPER_MAP)

        val index = (selected ?: RadarTimeline.frameAt(tick)).let { if (state.frames[RadarTimeline.time(state.now, it)] != null) it else RadarTimeline.NOW_INDEX }
        val stale = state.loadedAt > 0 && millis - state.loadedAt > STALE_MS
        drawMap(c, layout, state, zoom, index, stale)
        val window = state.window ?: state.previous?.window
        when {
            state.outside -> notice(c, layout, texts.outside, texts.outsideHint)
            !rider.hasFix -> notice(c, layout, texts.waitingForGps, null)
            window != null -> drawRider(c, layout, window, rider)
        }
        if (!state.outside) {
            drawScale(c, layout, zoom)
            drawButtons(c, layout)
            comic.plain(c, ATTRIBUTION, layout.map.right - 8, layout.map.bottom - 6, 10f, 0xFF444444.toInt(), comic.numbers, Paint.Align.RIGHT)
        }
        drawHeader(c, layout, state, index, stale, paused = selected != null)
        if (layout.footer > 0) drawFooter(c, layout, state, index, stale, rider)
        c.restore()

        comic.stroke.color = INK
        comic.stroke.strokeWidth = BORDER
        c.drawRoundRect(RectF(BORDER / 2, BORDER / 2, layout.width - BORDER / 2, layout.height - BORDER / 2), CORNER, CORNER, comic.stroke)
        return bitmap
    }

    /** Base map and radar; while a new map loads, the old one is shown scaled to the new zoom. */
    private fun drawMap(c: Canvas, layout: RadarLayout, state: RadarState, zoom: RadarZoom, index: Int, stale: Boolean) {
        val shown = if (state.base != null) state else state.previous ?: return
        val base = shown.base ?: return
        val shownZoom = shown.window?.zoom ?: zoom
        val factor = (shownZoom.widthKm / zoom.widthKm).toFloat()
        c.save()
        c.clipRect(layout.map)
        c.translate(layout.map.centerX(), layout.map.centerY())
        c.scale(factor, factor)
        c.translate(-layout.map.width() / 2, -layout.map.height() / 2)
        val target = RectF(0f, 0f, layout.map.width(), layout.map.height())
        image.alpha = 255
        c.drawBitmap(base, null, target, image)
        val frame = shown.frames[RadarTimeline.time(shown.now, index)]
        if (frame != null) {
            image.alpha = if (stale) STALE_ALPHA else RADAR_ALPHA
            c.drawBitmap(frame.bitmap, null, target, image)
        }
        c.restore()
    }

    private fun drawRider(c: Canvas, layout: RadarLayout, window: RadarWindow, rider: RideSnapshot) {
        val (dx, dy) = window.offsetOf(rider.latitude ?: return, rider.longitude ?: return)
        val x = layout.map.centerX() + dx * layout.map.width() / window.widthPx
        val y = layout.map.centerY() + dy * layout.map.height() / window.heightPx
        if (!layout.map.contains(x, y)) return
        rider.bearing?.let { bearing ->
            c.save()
            c.rotate(bearing.toFloat(), x, y)
            comic.sticker(c, Path().apply { moveTo(x, y); lineTo(x - 16, y - 38); quadTo(x, y - 46, x + 16, y - 38); close() }, CONE, 2f)
            c.restore()
        }
        comic.sticker(c, Path().apply { addCircle(x, y, 12f, Path.Direction.CW) }, Color.WHITE, 3f, shadow = 2.5f)
        comic.fill.color = DOT
        c.drawCircle(x, y, 7f, comic.fill)
    }

    private fun drawScale(c: Canvas, layout: RadarLayout, zoom: RadarZoom) {
        val bar = (zoom.scaleKm / zoom.widthKm * layout.map.width()).toFloat()
        val x = 14f
        val y = layout.map.bottom - 22
        val box = RectF(x - 6, y - 22, x + bar + 6, y + 14)
        comic.fill.color = 0xEBFFF6D6.toInt()
        c.drawRoundRect(box, 8f, 8f, comic.fill)
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 2f
        c.drawRoundRect(box, 8f, 8f, comic.stroke)
        comic.fill.color = INK
        c.drawRect(x, y, x + bar / 2, y + 7, comic.fill)
        comic.fill.color = Color.WHITE
        c.drawRect(x + bar / 2, y, x + bar, y + 7, comic.fill)
        c.drawRect(x + bar / 2, y, x + bar, y + 7, comic.stroke)
        comic.plain(c, "0", x, y - 6, 13f, typeface = comic.numbers)
        comic.plain(c, "${zoom.scaleKm} km", x + bar, y - 6, 13f, typeface = comic.numbers, align = Paint.Align.RIGHT)
    }

    private fun drawButtons(c: Canvas, layout: RadarLayout) {
        for ((y, symbol) in listOf(layout.zoomInY to "+", layout.zoomOutY to "−")) {
            comic.sticker(c, Path().apply { addCircle(layout.buttonX, y, layout.buttonRadius, Path.Direction.CW) }, Color.WHITE, 3f, shadow = 3f)
            comic.plain(c, symbol, layout.buttonX, y + layout.buttonRadius * 0.44f, layout.buttonRadius * 1.3f, typeface = comic.numbers, align = Paint.Align.CENTER)
        }
    }

    private fun drawHeader(c: Canvas, layout: RadarLayout, state: RadarState, index: Int, stale: Boolean, paused: Boolean) {
        comic.fill.color = SKY
        c.drawRect(0f, 0f, layout.width.toFloat(), layout.header.toFloat(), comic.fill)
        comic.halftone(c, layout.width.toFloat(), layout.header.toFloat())
        line(c, layout.header.toFloat(), layout.width)
        comic.bubble(c, texts.title, 10f, 8f, size = 20f, rotation = -2f)

        val hasFrame = state.frames.containsKey(RadarTimeline.time(state.now, index))
        val time = clock.format(Date(RadarTimeline.time(state.now, index)))
        val (label, color) = when {
            state.outside -> "–" to Color.WHITE
            !hasFrame -> texts.loading to Color.WHITE
            stale -> time to 0xFFC9CED6.toInt()
            RadarTimeline.isForecast(index) -> "${texts.forecast} $time" to 0xFFFFD23F.toInt()
            index == RadarTimeline.NOW_INDEX -> "${texts.now} · $time" to Color.WHITE
            else -> time to Color.WHITE
        }.let { (text, fill) -> (if (paused && hasFrame && !state.outside) "‖ $text" else text) to fill }
        val width = max(110f, comic.measure(label, 18f) + 32)
        val box = RectF(layout.width - 18 - width, 8f, layout.width - 18f, 40f)
        comic.sticker(c, Path().apply { addRoundRect(box, 16f, 16f, Path.Direction.CW) }, color, 3f, shadow = 3f)
        comic.plain(c, label, box.centerX(), box.bottom - 10, 18f, typeface = comic.numbers, align = Paint.Align.CENTER)
    }

    private fun drawFooter(c: Canvas, layout: RadarLayout, state: RadarState, index: Int, stale: Boolean, rider: RideSnapshot) {
        val top = layout.map.bottom
        comic.fill.color = PAPER
        c.drawRect(0f, top, layout.width.toFloat(), layout.height.toFloat(), comic.fill)
        line(c, top, layout.width)

        val message = when {
            state.outside -> ""
            stale -> String.format(texts.offline, clock.format(Date(state.loadedAt)))
            else -> when (val outlook = RainOutlook.at(state, rider)) {
                null, RainOutlook.Unknown -> texts.loading
                is RainOutlook.StaysDry -> outlook.until?.let { String.format(texts.dryUntil, clock.format(Date(it))) } ?: texts.staysDry
                is RainOutlook.StaysWet -> outlook.until?.let { String.format(texts.wetUntil, clock.format(Date(it))) } ?: texts.staysWet
                is RainOutlook.RainFrom -> String.format(texts.rainFrom, clock.format(Date(outlook.time)))
                is RainOutlook.DryFrom -> String.format(texts.dryFrom, clock.format(Date(outlook.time)))
            }
        }
        comic.plain(c, message, 16f, top + 30, 22f)
        if (state.outside) return

        val x0 = layout.timelineX(0)
        val x1 = layout.timelineX(60)
        val y = top + 54
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(x0, y, x1, y, comic.stroke)
        RadarTimeline.OFFSETS_MIN.forEachIndexed { i, minutes ->
            val x = layout.timelineX(minutes)
            val on = i == index
            val labeled = RadarTimeline.isLabeled(i)
            val forecast = RadarTimeline.isForecast(i)
            val fill = when {
                on && forecast -> 0xFFFFD23F.toInt()
                on -> 0xFF4FA8F2.toInt()
                else -> Color.WHITE
            }
            val radius = when {
                on -> 10f
                labeled -> 6f
                else -> 4f
            }
            comic.sticker(c, Path().apply { addCircle(x, y, radius, Path.Direction.CW) }, fill, if (labeled || on) 2.5f else 2f)
            if (labeled) comic.plain(c, if (minutes == 0) texts.now else "+$minutes", x, y + 23, 13f, align = Paint.Align.CENTER)
        }
    }

    /** A big tilted sticker in the middle of the map, for "no GPS yet" and "no radar here". */
    private fun notice(c: Canvas, layout: RadarLayout, title: String, hint: String?) {
        val width = max(comic.measure(title, 22f, comic.hand), hint?.let { comic.measure(it, 15f, comic.hand) } ?: 0f) + 40
        val height = if (hint != null) 80f else 54f
        c.save()
        c.rotate(-4f, layout.map.centerX(), layout.map.centerY())
        val box = RectF(layout.map.centerX() - width / 2, layout.map.centerY() - height / 2, layout.map.centerX() + width / 2, layout.map.centerY() + height / 2)
        comic.sticker(c, Path().apply { addRoundRect(box, 18f, 18f, Path.Direction.CW) }, Color.WHITE, 3.5f, shadow = 3f)
        comic.plain(c, title, box.centerX(), box.top + 34, 22f, align = Paint.Align.CENTER)
        if (hint != null) comic.plain(c, hint, box.centerX(), box.top + 62, 15f, align = Paint.Align.CENTER)
        c.restore()
    }

    private fun line(c: Canvas, y: Float, width: Int) {
        comic.stroke.color = INK
        comic.stroke.strokeWidth = 3f
        c.drawLine(0f, y, width.toFloat(), y, comic.stroke)
    }

    private companion object {
        const val ATTRIBUTION = "© basemap.de / BKG · DWD"
        /** Two reload rounds missed. */
        const val STALE_MS = 2 * RadarTimeline.RELOAD_MS
        const val RADAR_ALPHA = 153
        const val STALE_ALPHA = 70
        const val CORNER = 18f
        const val BORDER = 3.5f
        const val SKY = 0xFF6EC3FF.toInt()
        const val PAPER_MAP = 0xFFF3EEDC.toInt()
        const val DOT = 0xFFFF7A1C.toInt()
        const val CONE = 0x80FF7A1C.toInt()
    }
}

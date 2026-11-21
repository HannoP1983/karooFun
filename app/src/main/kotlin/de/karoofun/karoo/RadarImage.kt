package de.karoofun.karoo

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What a pixel of the DWD radar says. Rain comes in the opaque legend colors from 0.1 mm/h up. "No data" is
 * grey at 30 % opacity and the edge of the radar range a magenta line with soft edges; both are neither wet nor dry.
 */
enum class RadarCell {
    DRY, RAIN, NO_DATA;

    companion object {
        /** Legend of the layer dwd:Niederschlagsradar, from 0.1 mm/h to over 150 mm/h. */
        private val RAIN_COLORS = intArrayOf(
            0x33FFFF, 0x1ACC9A, 0x019934, 0x4DB31B, 0x99CC01, 0xCCE601, 0xFFFF01, 0xFFC401,
            0xFF8901, 0xFF4501, 0xFE0000, 0xE5004C, 0xCC0098, 0x6600CB, 0x0000FE,
        )

        /** The PNG8 palette may shift a legend color by a few steps. */
        private const val COLOR_TOLERANCE = 8

        fun of(argb: Int): RadarCell {
            val alpha = argb ushr 24
            return when {
                alpha == 0 -> DRY
                alpha == 255 && RAIN_COLORS.any { close(it, argb) } -> RAIN
                else -> NO_DATA
            }
        }

        private fun close(rgb: Int, argb: Int) = (0..16 step 8).all { shift ->
            abs((rgb shr shift and 0xFF) - (argb shr shift and 0xFF)) <= COLOR_TOLERANCE
        }
    }
}

/**
 * A radar frame as cells, for asking whether it rains at a point. A point counts as wet when at least half of
 * its surroundings within about a kilometer are, weighted by distance: the same soft blur [RadarImage] draws,
 * so the rider's dot sits inside a drawn rain area exactly when the field says rain.
 */
class RadarCells(val width: Int, val height: Int, private val cells: Array<RadarCell>) {

    /** Rain at a point given as fraction of the map width and height; outside the map or without data unknown. */
    fun rainAt(u: Float, v: Float): Boolean? {
        val cx = u * width
        val cy = v * height
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) return null
        var rain = 0.0
        var dry = 0.0
        var none = 0.0
        val reach = RadarImage.BLUR_REACH
        for (y in max(0, cy.toInt() - reach)..min(height - 1, cy.toInt() + reach)) {
            for (x in max(0, cx.toInt() - reach)..min(width - 1, cx.toInt() + reach)) {
                val dx = x + 0.5 - cx
                val dy = y + 0.5 - cy
                val weight = exp(-(dx * dx + dy * dy) / (2 * RadarImage.BLUR_SIGMA * RadarImage.BLUR_SIGMA))
                when (cells[y * width + x]) {
                    RadarCell.RAIN -> rain += weight
                    RadarCell.DRY -> dry += weight
                    RadarCell.NO_DATA -> none += weight
                }
            }
        }
        if (none > rain + dry) return null
        return rain >= dry
    }

    companion object {
        fun of(argb: IntArray, width: Int, height: Int) = RadarCells(width, height, Array(argb.size) { RadarCell.of(argb[it]) })
    }
}

/**
 * Turns the DWD's 1 km squares into round rain areas for the tile. Rain is blurred by about 400 m, scaled up and
 * its edge sharpened again at half coverage, so the outline is smooth and a single wet cell still shows as a dot.
 * Colors of neighboring intensities blend into each other. "No data" and the range border are only scaled.
 */
object RadarImage {
    /** Blur radius: 0.4 km in radar pixels. */
    const val BLUR_SIGMA = 0.4 * RadarWindow.RADAR_PX_PER_KM
    val BLUR_REACH = ceil(3 * BLUR_SIGMA).toInt()

    /** Width of the sharpened edge as coverage, about one and a half pixels of the result. */
    private const val EDGE_SOFTNESS = 0.19f

    /** Scale factor that brings a radar frame [width] pixels wide to about half the map width; the tile scales the rest. */
    fun factorFor(width: Int, mapWidth: Int) = max(1, (mapWidth / 2f / width).roundToInt())

    /** [argb] smoothed and [factor] times larger, as non-premultiplied ARGB like Bitmap.setPixels expects. */
    fun smooth(argb: IntArray, width: Int, height: Int, factor: Int): IntArray {
        val size = width * height
        // Premultiplied channels: rain on its own layer for blurring, the rest (no data, range border) on another
        val rain = Array(4) { FloatArray(size) }
        val other = Array(4) { FloatArray(size) }
        for (i in 0 until size) {
            val pixel = argb[i]
            val layer = when (RadarCell.of(pixel)) {
                RadarCell.DRY -> continue
                RadarCell.RAIN -> rain
                RadarCell.NO_DATA -> other
            }
            val alpha = (pixel ushr 24) / 255f
            layer[0][i] = alpha
            layer[1][i] = (pixel shr 16 and 0xFF) / 255f * alpha
            layer[2][i] = (pixel shr 8 and 0xFF) / 255f * alpha
            layer[3][i] = (pixel and 0xFF) / 255f * alpha
        }
        for (channel in rain) blur(channel, width, height)

        val outWidth = width * factor
        val outHeight = height * factor
        val band = min(EDGE_SOFTNESS / factor, 0.2f)
        val out = IntArray(outWidth * outHeight)
        val r = FloatArray(4)
        val o = FloatArray(4)
        for (oy in 0 until outHeight) {
            val sy = ((oy + 0.5f) / factor - 0.5f).coerceIn(0f, height - 1f)
            val y0 = sy.toInt()
            val y1 = min(y0 + 1, height - 1)
            val fy = sy - y0
            for (ox in 0 until outWidth) {
                val sx = ((ox + 0.5f) / factor - 0.5f).coerceIn(0f, width - 1f)
                val x0 = sx.toInt()
                val x1 = min(x0 + 1, width - 1)
                val fx = sx - x0
                val w00 = (1 - fx) * (1 - fy)
                val w10 = fx * (1 - fy)
                val w01 = (1 - fx) * fy
                val w11 = fx * fy
                val i00 = y0 * width + x0
                val i10 = y0 * width + x1
                val i01 = y1 * width + x0
                val i11 = y1 * width + x1
                for (c in 0..3) {
                    r[c] = rain[c][i00] * w00 + rain[c][i10] * w10 + rain[c][i01] * w01 + rain[c][i11] * w11
                    o[c] = other[c][i00] * w00 + other[c][i10] * w10 + other[c][i01] * w01 + other[c][i11] * w11
                }
                // Rain alpha: coverage sharpened around one half; its color is the blended rain color
                val cover = smoothstep(0.5f - band, 0.5f + band, r[0])
                val alpha = cover + o[0] * (1 - cover)
                if (alpha <= 0f) continue
                var pixel = (alpha * 255).roundToInt() shl 24
                for (c in 1..3) {
                    val rainColor = if (r[0] > 0f) r[c] / r[0] else 0f
                    val value = (rainColor * cover + o[c] * (1 - cover)) / alpha
                    pixel = pixel or ((value.coerceIn(0f, 1f) * 255).roundToInt() shl (24 - 8 * c))
                }
                out[oy * outWidth + ox] = pixel
            }
        }
        return out
    }

    /** Gaussian blur in place, weights renormalized at the image edge so rain reaching the edge stays whole. */
    private fun blur(channel: FloatArray, width: Int, height: Int) {
        val kernel = FloatArray(2 * BLUR_REACH + 1) { exp(-((it - BLUR_REACH) * (it - BLUR_REACH)) / (2 * BLUR_SIGMA * BLUR_SIGMA)).toFloat() }
        val line = FloatArray(max(width, height))
        for (y in 0 until height) {
            for (x in 0 until width) line[x] = channel[y * width + x]
            for (x in 0 until width) channel[y * width + x] = convolve(line, width, x, kernel)
        }
        for (x in 0 until width) {
            for (y in 0 until height) line[y] = channel[y * width + x]
            for (y in 0 until height) channel[y * width + x] = convolve(line, height, y, kernel)
        }
    }

    private fun convolve(line: FloatArray, length: Int, at: Int, kernel: FloatArray): Float {
        var sum = 0f
        var weights = 0f
        for (k in kernel.indices) {
            val i = at + k - BLUR_REACH
            if (i !in 0 until length) continue
            sum += line[i] * kernel[k]
            weights += kernel[k]
        }
        return sum / weights
    }

    private fun smoothstep(low: Float, high: Float, value: Float): Float {
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

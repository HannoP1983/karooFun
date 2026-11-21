package de.karoofun.karoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarImageTest {
    private val dry = 0x00FFFFFF
    private val drizzle = 0xFF33FFFF.toInt()
    private val heavy = 0xFFFE0000.toInt()
    private val noData = 0x4D7E7E7E
    private val border = 0xFFFB00FF.toInt()

    /** A [size]×[size] frame, dry except for [wet] square from [from] to [to] (exclusive). */
    private fun frame(size: Int, from: Int, to: Int, wet: Int = drizzle, outside: Int = dry) =
        IntArray(size * size) { i -> if (i % size in from until to && i / size in from until to) wet else outside }

    @Test
    fun legendColorsAreRainGreyAndBorderAreNoData() {
        assertEquals(RadarCell.DRY, RadarCell.of(dry))
        assertEquals(RadarCell.RAIN, RadarCell.of(drizzle))
        assertEquals(RadarCell.RAIN, RadarCell.of(heavy))
        // PNG8 shifts colors slightly: #53AD22 is the legend's #4DB31B
        assertEquals(RadarCell.RAIN, RadarCell.of(0xFF53AD22.toInt()))
        assertEquals(RadarCell.NO_DATA, RadarCell.of(noData))
        assertEquals(RadarCell.NO_DATA, RadarCell.of(border))
        assertEquals(RadarCell.NO_DATA, RadarCell.of(0x80FB00FF.toInt()))
    }

    @Test
    fun rainInsideAWetAreaDryOutside() {
        // One 1 km cell is 4×4 pixels; a 3 km shower in the middle of 10 km
        val cells = RadarCells.of(frame(40, 14, 26), 40, 40)
        assertEquals(true, cells.rainAt(0.5f, 0.5f))
        assertEquals(false, cells.rainAt(0.1f, 0.1f))
        // Just inside and just outside the edge at pixel 14
        assertEquals(true, cells.rainAt(14.6f / 40, 0.5f))
        assertEquals(false, cells.rainAt(13.4f / 40, 0.5f))
    }

    @Test
    fun aSingleWetCellCountsButAStrayPixelNot() {
        assertEquals(true, RadarCells.of(frame(40, 18, 22), 40, 40).rainAt(0.5f, 0.5f))
        assertEquals(false, RadarCells.of(frame(40, 20, 21), 40, 40).rainAt(0.5f, 0.5f))
    }

    @Test
    fun noDataAndOffTheMapAreUnknown() {
        val grey = RadarCells.of(IntArray(1600) { noData }, 40, 40)
        assertNull(grey.rainAt(0.5f, 0.5f))
        assertNull(RadarCells.of(frame(40, 14, 26), 40, 40).rainAt(1.1f, 0.5f))
    }

    @Test
    fun smoothingRoundsTheCornersOfASquare() {
        val out = RadarImage.smooth(frame(40, 12, 28, heavy), 40, 40, 4)
        val size = 160
        fun alpha(x: Int, y: Int) = out[y * size + x] ushr 24
        assertEquals(255, alpha(80, 80))
        assertEquals(0, alpha(10, 10))
        // Middle of an edge stays where it was, the corner is cut off
        assertTrue(alpha(80, 50) > 200)
        assertEquals(0, alpha(49, 49))
        assertEquals(0xFE0000, out[80 * size + 80] and 0xFFFFFF)
    }

    @Test
    fun aSingleCellStaysVisibleAsADot() {
        val out = RadarImage.smooth(frame(40, 18, 22), 40, 40, 4)
        assertTrue(out[80 * 160 + 80] ushr 24 > 200)
    }

    @Test
    fun noDataIsOnlyScaled() {
        val out = RadarImage.smooth(IntArray(100) { noData }, 10, 10, 2)
        assertEquals(0x4D, out[5 * 20 + 5] ushr 24)
        assertFalse(out.any { it ushr 24 > 0x4E })
    }

    @Test
    fun factorBringsTheFrameToAboutHalfTheMap() {
        assertEquals(6, RadarImage.factorFor(40, 478))
        assertEquals(2, RadarImage.factorFor(100, 478))
        assertEquals(1, RadarImage.factorFor(200, 478))
    }
}

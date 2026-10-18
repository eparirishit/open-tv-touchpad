package dev.opentvtouchpad.input

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorBoundsTest {
    @Test
    fun clampsPointsAtEveryDisplayEdge() {
        val bounds = DisplayBounds(width = 1920, height = 1080)

        assertEquals(PointF(0f, 500f), bounds.clamp(PointF(-1f, 500f)))
        assertEquals(PointF(1919f, 500f), bounds.clamp(PointF(1920f, 500f)))
        assertEquals(PointF(500f, 0f), bounds.clamp(PointF(500f, -1f)))
        assertEquals(PointF(500f, 1079f), bounds.clamp(PointF(500f, 1080f)))
        assertEquals(PointF(0f, 0f), bounds.clamp(PointF(-1f, -1f)))
        assertEquals(PointF(1919f, 1079f), bounds.clamp(PointF(1920f, 1080f)))
    }

    @Test
    fun clampsAgainstChangedDisplayBounds() {
        val point = PointF(1600f, 900f)

        assertEquals(point, DisplayBounds(width = 1920, height = 1080).clamp(point))
        assertEquals(
            PointF(1279f, 719f),
            DisplayBounds(width = 1280, height = 720).clamp(point),
        )
    }
}

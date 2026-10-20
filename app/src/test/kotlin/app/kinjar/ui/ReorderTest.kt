package app.kinjar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    @Test
    fun `a fast drag crosses several rows of different heights in one event`() {
        assertEquals(2 to -30f, settleDrag(listOf(100, 40, 200, 60), from = 0, offset = 230f, spacing = 10f))
    }

    @Test
    fun `dragging up crosses rows above and stops at the top`() {
        assertEquals(0 to -740f, settleDrag(listOf(40, 200, 60), from = 2, offset = -1000f, spacing = 10f))
    }

    @Test
    fun `a row resting near a boundary does not flip back`() {
        val heights = listOf(100, 100)
        val (down, rest) = settleDrag(heights, from = 0, offset = 61f, spacing = 0f)
        assertEquals(1, down)
        assertEquals(1 to rest, settleDrag(heights, from = 1, offset = rest, spacing = 0f))
        assertEquals(1 to rest - 1f, settleDrag(heights, from = 1, offset = rest - 1f, spacing = 0f))
        assertEquals(0 to 39f, settleDrag(heights, from = 1, offset = -61f, spacing = 0f))
    }

    @Test
    fun `edge scroll only reveals clipped rows`() {
        assertEquals(0f, edgeScroll(top = 0f, bottom = 50f, visibleTop = 0f, visibleBottom = 500f, height = 500f, edge = 60f, max = 10f), 0f)
        assertEquals(-10f, edgeScroll(top = 100f, bottom = 150f, visibleTop = 100f, visibleBottom = 500f, height = 900f, edge = 60f, max = 10f), 0f)
        assertEquals(5f, edgeScroll(top = 420f, bottom = 470f, visibleTop = 0f, visibleBottom = 500f, height = 900f, edge = 60f, max = 10f), 0f)
        assertEquals(0f, edgeScroll(top = 200f, bottom = 250f, visibleTop = 100f, visibleBottom = 500f, height = 900f, edge = 60f, max = 10f), 0f)
    }
}

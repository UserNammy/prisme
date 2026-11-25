package fr.prisme.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PrismBackgroundTest {

    @Test
    fun `drift ping-pongs between the amplitudes like the former infinite transition`() {
        assertEquals(-0.015f, driftAt(0L), 1e-6f)
        assertEquals(0f, driftAt(9_000L), 1e-6f)
        assertEquals(0.015f, driftAt(18_000L), 1e-6f)
        assertEquals(0f, driftAt(27_000L), 1e-6f)
        assertEquals(-0.015f, driftAt(36_000L), 1e-6f)
    }

    @Test
    fun `drift keeps looping after many cycles`() {
        assertEquals(driftAt(4_500L), driftAt(36_000L * 100 + 4_500L), 1e-6f)
    }
}

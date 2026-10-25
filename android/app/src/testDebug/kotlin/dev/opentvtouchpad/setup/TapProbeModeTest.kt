package dev.opentvtouchpad.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapProbeModeTest {
    @Test
    fun probeModesHaveFixedOrderAndNoFreeFormValues() {
        assertEquals(
            listOf(
                TapProbeMode.BASELINE_80_MS,
                TapProbeMode.SYSTEM_TAP_TIMEOUT,
                TapProbeMode.CONTROLLER_HANDOFF,
            ),
            TapProbeMode.entries,
        )
    }

    @Test
    fun baselineKeepsEightyMillisecondTap() {
        assertEquals(
            TapProbeConfig(
                mode = TapProbeMode.BASELINE_80_MS,
                durationMillis = 80L,
                requiresControllerHandoff = false,
            ),
            TapProbePolicy.config(
                mode = TapProbeMode.BASELINE_80_MS,
                systemTapTimeoutMillis = 137L,
            ),
        )
    }

    @Test
    fun systemModesUseInjectedAndroidTapTimeout() {
        val systemTimeout = TapProbePolicy.config(
            mode = TapProbeMode.SYSTEM_TAP_TIMEOUT,
            systemTapTimeoutMillis = 137L,
        )
        val controllerHandoff = TapProbePolicy.config(
            mode = TapProbeMode.CONTROLLER_HANDOFF,
            systemTapTimeoutMillis = 137L,
        )

        assertEquals(137L, systemTimeout.durationMillis)
        assertEquals(137L, controllerHandoff.durationMillis)
        assertFalse(systemTimeout.requiresControllerHandoff)
        assertTrue(controllerHandoff.requiresControllerHandoff)
    }
}

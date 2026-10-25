package dev.opentvtouchpad.setup

import dev.opentvtouchpad.input.GestureSpec

internal enum class TapProbeMode {
    BASELINE_80_MS,
    SYSTEM_TAP_TIMEOUT,
    CONTROLLER_HANDOFF,
}

internal data class TapProbeConfig(
    val mode: TapProbeMode,
    val durationMillis: Long,
    val requiresControllerHandoff: Boolean,
)

internal data class TapProbeRequest(
    val config: TapProbeConfig,
    val gesture: GestureSpec.Tap,
)

internal object TapProbePolicy {
    fun config(mode: TapProbeMode, systemTapTimeoutMillis: Long): TapProbeConfig =
        TapProbeConfig(
            mode = mode,
            durationMillis = when (mode) {
                TapProbeMode.BASELINE_80_MS -> BASELINE_DURATION_MILLIS
                TapProbeMode.SYSTEM_TAP_TIMEOUT,
                TapProbeMode.CONTROLLER_HANDOFF,
                -> systemTapTimeoutMillis
            },
            requiresControllerHandoff = mode == TapProbeMode.CONTROLLER_HANDOFF,
        )

    private const val BASELINE_DURATION_MILLIS = 80L
}

package dev.opentvtouchpad.accessibility

import android.content.Context
import android.view.WindowManager

internal object ServiceOverlayFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(
        context: Context,
        windowManager: WindowManager,
        serviceProvider: () -> TouchAccessibilityService?,
    ): ServiceOverlay = NoOpServiceOverlay

    private object NoOpServiceOverlay : ServiceOverlay {
        override fun show() = Unit

        override fun remove() = Unit
    }
}

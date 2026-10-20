package dev.opentvtouchpad.accessibility

import android.content.Context
import android.view.WindowManager
import dev.opentvtouchpad.setup.ServiceGateRuntime

internal object ServiceOverlayFactory {
    fun create(
        context: Context,
        windowManager: WindowManager,
        serviceProvider: () -> TouchAccessibilityService?,
    ): ServiceOverlay = DpadControllerOverlay(
        context,
        windowManager,
        ServiceGateRuntime(serviceProvider),
    )
}

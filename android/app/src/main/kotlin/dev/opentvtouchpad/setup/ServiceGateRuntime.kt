package dev.opentvtouchpad.setup

import dev.opentvtouchpad.accessibility.TouchAccessibilityService
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult

internal class ServiceGateRuntime(
    private val serviceProvider: () -> TouchAccessibilityService?,
) : GateRuntime {
    private var observedService: TouchAccessibilityService? = null

    override val isConnected: Boolean
        get() = serviceProvider() != null

    override fun currentBounds(): DisplayBounds =
        serviceProvider()?.currentBounds() ?: DisplayBounds(1, 1)

    override fun showCursorAt(point: PointF) {
        serviceProvider()?.showCursorAt(point.x.toInt(), point.y.toInt())
    }

    override fun moveCursorTo(point: PointF) {
        serviceProvider()?.moveCursorTo(point.x.toInt(), point.y.toInt())
    }

    override fun submitGesture(spec: GestureSpec): SubmitResult =
        serviceProvider()?.submitGesture(spec) ?: SubmitResult.REJECTED

    override fun cancelGesture() {
        serviceProvider()?.cancelGesture()
    }

    override fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?) {
        val service = serviceProvider()
        if (observedService !== service) {
            observedService?.setGestureOutcomeListener(null)
            observedService = service
        }
        service?.setGestureOutcomeListener(listener)
    }
}

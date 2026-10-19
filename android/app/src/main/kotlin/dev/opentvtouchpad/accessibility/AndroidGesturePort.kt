package dev.opentvtouchpad.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureCallback
import dev.opentvtouchpad.input.GesturePort
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF

class AndroidGesturePort(
    private val gestureDispatcher: (
        GestureDescription,
        AccessibilityService.GestureResultCallback?,
    ) -> Boolean,
    private val displayBounds: () -> DisplayBounds,
) : GesturePort {
    constructor(
        service: AccessibilityService,
        displayBounds: () -> DisplayBounds,
    ) : this(
        gestureDispatcher = { description, callback ->
            service.dispatchGesture(description, callback, null)
        },
        displayBounds = displayBounds,
    )

    override fun dispatch(spec: GestureSpec, callback: GestureCallback): Boolean {
        val description = spec.toGestureDescription() ?: return false
        return gestureDispatcher(
            description,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    callback.onCompleted()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    callback.onCancelled()
                }
            },
        )
    }

    override fun cancelActiveGesture() {
        gestureDispatcher(buildM0CancellationReplacement(), null)
    }

    private fun GestureSpec.toGestureDescription(): GestureDescription? {
        val points = when (this) {
            is GestureSpec.Tap -> listOf(point)
            is GestureSpec.Swipe -> listOf(start, end)
            is GestureSpec.Drag -> points
        }.map(displayBounds()::clamp)
        val durationMillis = when (this) {
            is GestureSpec.Tap -> durationMillis
            is GestureSpec.Swipe -> durationMillis
            is GestureSpec.Drag -> durationMillis
        }
        if (points.isEmpty() || durationMillis <= 0) return null

        return runCatching {
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        points.toAndroidPath(),
                        0,
                        durationMillis,
                    ),
                )
                .build()
        }.getOrNull()
    }

    private fun List<PointF>.toAndroidPath() = Path().apply {
        moveTo(first().x, first().y)
        drop(1).forEach { point -> lineTo(point.x, point.y) }
    }

    private fun buildM0CancellationReplacement(): GestureDescription {
        val bounds = displayBounds()
        val path = Path().apply {
            moveTo(bounds.width.toFloat(), bounds.height.toFloat())
            lineTo(bounds.width + 1f, bounds.height.toFloat())
        }

        // shortcut: M0 cancellation uses an off-display replacement gesture, validate on the certified projector in Task 6.
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
    }
}

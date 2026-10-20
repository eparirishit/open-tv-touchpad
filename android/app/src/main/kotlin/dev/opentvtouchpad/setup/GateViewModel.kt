package dev.opentvtouchpad.setup

import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import kotlin.math.min

internal enum class GateAction {
    SHOW_CURSOR,
    MOVE_UP,
    MOVE_DOWN,
    MOVE_LEFT,
    MOVE_RIGHT,
    TAP,
    SWIPE_VERTICAL,
    SWIPE_HORIZONTAL,
    DRAG_PATH,
    CANCEL,
}

internal data class GateUiState(
    val connected: Boolean = false,
    val bounds: DisplayBounds? = null,
    val cursor: PointF? = null,
    val lastDispatchResult: SubmitResult? = null,
    val callbackDurationMillis: Long? = null,
    val cancellationReason: CancelReason? = null,
    val gestureInFlight: Boolean = false,
    val controlsEnabled: Boolean = false,
    val settingsGuidanceVisible: Boolean = true,
)

internal interface GateRuntime {
    val isConnected: Boolean

    fun currentBounds(): DisplayBounds

    fun showCursorAt(point: PointF)

    fun moveCursorTo(point: PointF)

    fun submitGesture(spec: GestureSpec): SubmitResult

    fun cancelGesture()

    fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?)
}

internal class GateViewModel(
    private val runtime: GateRuntime,
) {
    var state = GateUiState()
        private set

    private var observer: (GateUiState) -> Unit = {}

    fun observe(observer: (GateUiState) -> Unit) {
        this.observer = observer
        observer(state)
    }

    fun detach() {
        runtime.setGestureOutcomeListener(null)
        observer = {}
    }

    fun refresh() {
        if (!runtime.isConnected) {
            showDisconnected()
            return
        }

        val bounds = runtime.currentBounds()
        if (!runtime.isConnected) {
            showDisconnected()
            return
        }
        runtime.setGestureOutcomeListener(::onGestureFinished)
        update(
            state.copy(
                connected = true,
                bounds = bounds,
                cursor = state.cursor?.let(bounds::clamp) ?: bounds.center(),
                controlsEnabled = !state.gestureInFlight,
                settingsGuidanceVisible = false,
            ),
        )
    }

    private fun showDisconnected() {
        runtime.setGestureOutcomeListener(null)
        update(
            state.copy(
                connected = false,
                bounds = null,
                cursor = null,
                gestureInFlight = false,
                controlsEnabled = false,
                settingsGuidanceVisible = true,
            ),
        )
    }

    fun perform(action: GateAction, isRepeat: Boolean) {
        if (!state.connected) return
        if (isRepeat && action !in MOVE_ACTIONS) return
        if (state.gestureInFlight && action != GateAction.CANCEL) return

        when (action) {
            GateAction.SHOW_CURSOR -> runtime.showCursorAt(requireCursor())
            GateAction.MOVE_UP -> moveCursor(deltaY = -movementStep())
            GateAction.MOVE_DOWN -> moveCursor(deltaY = movementStep())
            GateAction.MOVE_LEFT -> moveCursor(deltaX = -movementStep())
            GateAction.MOVE_RIGHT -> moveCursor(deltaX = movementStep())
            GateAction.TAP -> dispatch(GestureSpec.Tap(requireCursor(), TAP_DURATION_MILLIS))
            GateAction.SWIPE_VERTICAL -> dispatch(verticalSwipe())
            GateAction.SWIPE_HORIZONTAL -> dispatch(horizontalSwipe())
            GateAction.DRAG_PATH -> dispatch(dragPath())
            GateAction.CANCEL -> if (state.gestureInFlight) runtime.cancelGesture()
        }
    }

    private fun moveCursor(deltaX: Float = 0f, deltaY: Float = 0f) {
        val bounds = requireBounds()
        val cursor = requireCursor()
        val next = bounds.clamp(PointF(cursor.x + deltaX, cursor.y + deltaY))
        runtime.moveCursorTo(next)
        update(state.copy(cursor = next))
    }

    private fun dispatch(spec: GestureSpec) {
        val result = runtime.submitGesture(spec)
        val inFlight = result != SubmitResult.REJECTED
        update(
            state.copy(
                lastDispatchResult = result,
                callbackDurationMillis = null,
                cancellationReason = null,
                gestureInFlight = inFlight,
                controlsEnabled = !inFlight,
            ),
        )
    }

    private fun onGestureFinished(outcome: GestureOutcome) {
        update(
            state.copy(
                callbackDurationMillis = outcome.callbackDurationMillis,
                cancellationReason = outcome.cancellationReason,
                gestureInFlight = false,
                controlsEnabled = state.connected,
            ),
        )
    }

    private fun verticalSwipe(): GestureSpec.Swipe {
        val bounds = requireBounds()
        return GestureSpec.Swipe(
            start = bounds.point(xPercent = 50, yPercent = 65),
            end = bounds.point(xPercent = 50, yPercent = 35),
            durationMillis = SWIPE_DURATION_MILLIS,
        )
    }

    private fun horizontalSwipe(): GestureSpec.Swipe {
        val bounds = requireBounds()
        return GestureSpec.Swipe(
            start = bounds.point(xPercent = 65, yPercent = 50),
            end = bounds.point(xPercent = 35, yPercent = 50),
            durationMillis = SWIPE_DURATION_MILLIS,
        )
    }

    private fun dragPath(): GestureSpec.Drag {
        val bounds = requireBounds()
        return GestureSpec.Drag(
            points = listOf(
                bounds.point(xPercent = 40, yPercent = 50),
                bounds.point(xPercent = 50, yPercent = 50),
                bounds.point(xPercent = 60, yPercent = 50),
            ),
            durationMillis = DRAG_DURATION_MILLIS,
        )
    }

    private fun movementStep(): Float {
        val bounds = requireBounds()
        return (min(bounds.width, bounds.height) * MOVE_PERCENT / 100)
            .coerceAtLeast(1)
            .toFloat()
    }

    private fun requireBounds(): DisplayBounds = checkNotNull(state.bounds)

    private fun requireCursor(): PointF = checkNotNull(state.cursor)

    private fun update(next: GateUiState) {
        state = next
        observer(next)
    }

    private fun DisplayBounds.center() = point(xPercent = 50, yPercent = 50)

    private fun DisplayBounds.point(xPercent: Int, yPercent: Int) = PointF(
        x = (width * xPercent / 100).toFloat(),
        y = (height * yPercent / 100).toFloat(),
    )

    private companion object {
        val MOVE_ACTIONS = setOf(
            GateAction.MOVE_UP,
            GateAction.MOVE_DOWN,
            GateAction.MOVE_LEFT,
            GateAction.MOVE_RIGHT,
        )
        const val MOVE_PERCENT = 5
        const val TAP_DURATION_MILLIS = 80L
        const val SWIPE_DURATION_MILLIS = 250L
        const val DRAG_DURATION_MILLIS = 500L
    }
}

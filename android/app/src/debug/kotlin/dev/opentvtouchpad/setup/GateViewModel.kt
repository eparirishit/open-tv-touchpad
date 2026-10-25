package dev.opentvtouchpad.setup

import dev.opentvtouchpad.accessibility.ControllerHandoffTerminalState
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

internal enum class ProbeCallbackOutcome {
    COMPLETED,
    CANCELLED,
    TIMEOUT,
    HANDOFF_FRAME_TIMEOUT,
}

internal enum class ProbeControllerState {
    PRESENT,
    HANDOFF,
    REMOVED,
}

internal enum class ManualTargetResult {
    ACTIVATED,
    NOT_ACTIVATED,
}

internal data class GateUiState(
    val connected: Boolean = false,
    val bounds: DisplayBounds? = null,
    val cursor: PointF? = null,
    val lastDispatchResult: SubmitResult? = null,
    val callbackOutcome: ProbeCallbackOutcome? = null,
    val callbackDurationMillis: Long? = null,
    val cancellationReason: CancelReason? = null,
    val gestureInFlight: Boolean = false,
    val controlsEnabled: Boolean = false,
    val settingsGuidanceVisible: Boolean = true,
    val tapProbeMode: TapProbeMode = TapProbeMode.BASELINE_80_MS,
    val controllerHandoffTerminalState: ControllerHandoffTerminalState? = null,
    val controllerState: ProbeControllerState = ProbeControllerState.REMOVED,
    val manualTargetResult: ManualTargetResult? = null,
    val restartRequired: Boolean = false,
)

internal data class GateRuntimeState(
    val gestureInFlight: Boolean,
    val latestOutcome: GestureOutcome?,
)

internal interface GateRuntime {
    val isConnected: Boolean

    fun currentBounds(): DisplayBounds

    fun showCursorAt(point: PointF)

    fun moveCursorTo(point: PointF)

    fun hideCursor()

    fun submitGesture(spec: GestureSpec): SubmitResult

    fun cancelGesture()

    fun requireRestart()

    fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?)

    fun currentGestureState(): GateRuntimeState
}

internal class GateViewModel(
    private val runtime: GateRuntime,
    private val systemTapTimeoutMillis: Long,
) {
    var state = GateUiState()
        private set

    private var observer: (GateUiState) -> Unit = {}
    private var preparedTapProbe: TapProbeRequest? = null
    private var tapProbeOutcomeListener: ((GestureOutcome) -> Unit)? = null
    private var probeAttemptInFlight = false

    fun observe(observer: (GateUiState) -> Unit) {
        this.observer = observer
        observer(state)
    }

    fun detach() {
        runtime.setGestureOutcomeListener(null)
        tapProbeOutcomeListener = null
        probeAttemptInFlight = false
        observer = {}
    }

    fun selectTapProbeMode(mode: TapProbeMode) {
        update(state.copy(tapProbeMode = mode))
    }

    fun recordManualTargetResult(result: ManualTargetResult) {
        update(state.copy(manualTargetResult = result))
    }

    fun publishControllerState(controllerState: ProbeControllerState) {
        update(state.copy(controllerState = controllerState))
    }

    fun prepareTapProbe(): TapProbeRequest? {
        if (
            !runtime.isConnected ||
            !state.controlsEnabled ||
            runtime.currentGestureState().gestureInFlight
        ) return null

        publishCurrentBounds()
        val config = TapProbePolicy.config(state.tapProbeMode, systemTapTimeoutMillis)
        return TapProbeRequest(
            config = config,
            gesture = GestureSpec.Tap(requireCursor(), config.durationMillis),
        ).also { preparedTapProbe = it }
    }

    fun submitTapProbe(request: TapProbeRequest): SubmitResult {
        if (request !== preparedTapProbe) return SubmitResult.REJECTED

        preparedTapProbe = null
        update(state.copy(manualTargetResult = null))
        val result = runtime.submitGesture(request.gesture)
        probeAttemptInFlight = result == SubmitResult.ACCEPTED
        if (!probeAttemptInFlight) tapProbeOutcomeListener = null
        publishDispatchResult(result)
        return result
    }

    fun setTapProbeOutcomeListener(listener: ((GestureOutcome) -> Unit)?) {
        tapProbeOutcomeListener = listener
    }

    fun hideCursor() {
        runtime.hideCursor()
    }

    fun clearPendingInput() {
        preparedTapProbe = null
        tapProbeOutcomeListener = null
        probeAttemptInFlight = false
    }

    fun requireRestart() {
        runtime.requireRestart()
        update(state.copy(restartRequired = true, controlsEnabled = false))
    }

    fun publishControllerHandoffTerminalState(state: ControllerHandoffTerminalState) {
        val isFrameTimeout = state == ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT
        update(
            this.state.copy(
                controllerHandoffTerminalState = state,
                lastDispatchResult = if (isFrameTimeout) null else this.state.lastDispatchResult,
                callbackOutcome = when (state) {
                    ControllerHandoffTerminalState.COMPLETED -> ProbeCallbackOutcome.COMPLETED
                    ControllerHandoffTerminalState.CANCELLED -> ProbeCallbackOutcome.CANCELLED
                    ControllerHandoffTerminalState.TIMEOUT -> ProbeCallbackOutcome.TIMEOUT
                    ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT ->
                        ProbeCallbackOutcome.HANDOFF_FRAME_TIMEOUT
                    ControllerHandoffTerminalState.REJECTED -> null
                },
                callbackDurationMillis = if (isFrameTimeout) {
                    null
                } else {
                    this.state.callbackDurationMillis
                },
                cancellationReason = if (isFrameTimeout) null else this.state.cancellationReason,
                controllerState = if (state == ControllerHandoffTerminalState.COMPLETED) {
                    ProbeControllerState.HANDOFF
                } else {
                    ProbeControllerState.REMOVED
                },
            ),
        )
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
        val gestureState = runtime.currentGestureState()
        val preserveFrameTimeout =
            state.controllerHandoffTerminalState == ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT
        update(
            state.copy(
                connected = true,
                bounds = bounds,
                cursor = state.cursor?.let(bounds::clamp) ?: bounds.center(),
                callbackOutcome = if (preserveFrameTimeout) {
                    state.callbackOutcome
                } else {
                    gestureState.latestOutcome?.toProbeCallbackOutcome()
                },
                callbackDurationMillis = if (preserveFrameTimeout) {
                    state.callbackDurationMillis
                } else {
                    gestureState.latestOutcome?.callbackDurationMillis
                },
                cancellationReason = if (preserveFrameTimeout) {
                    state.cancellationReason
                } else {
                    gestureState.latestOutcome?.cancellationReason
                },
                gestureInFlight = gestureState.gestureInFlight,
                controlsEnabled = !gestureState.gestureInFlight && !state.restartRequired,
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
            GateAction.SHOW_CURSOR -> {
                publishCurrentBounds()
                runtime.showCursorAt(requireCursor())
            }
            GateAction.MOVE_UP -> moveCursor(deltaY = -1f)
            GateAction.MOVE_DOWN -> moveCursor(deltaY = 1f)
            GateAction.MOVE_LEFT -> moveCursor(deltaX = -1f)
            GateAction.MOVE_RIGHT -> moveCursor(deltaX = 1f)
            GateAction.TAP -> dispatch(tap())
            GateAction.SWIPE_VERTICAL -> dispatch(verticalSwipe())
            GateAction.SWIPE_HORIZONTAL -> dispatch(horizontalSwipe())
            GateAction.DRAG_PATH -> dispatch(dragPath())
            GateAction.CANCEL -> if (state.gestureInFlight) runtime.cancelGesture()
        }
    }

    private fun moveCursor(deltaX: Float = 0f, deltaY: Float = 0f) {
        val bounds = publishCurrentBounds()
        val cursor = requireCursor()
        val step = movementStep(bounds)
        val next = bounds.clamp(PointF(cursor.x + deltaX * step, cursor.y + deltaY * step))
        runtime.moveCursorTo(next)
        update(state.copy(cursor = next))
    }

    private fun dispatch(spec: GestureSpec) {
        val result = runtime.submitGesture(spec)
        publishDispatchResult(result)
    }

    private fun publishDispatchResult(result: SubmitResult) {
        val inFlight = result != SubmitResult.REJECTED
        update(
            state.copy(
                lastDispatchResult = result,
                callbackOutcome = null,
                callbackDurationMillis = null,
                cancellationReason = null,
                gestureInFlight = inFlight,
                controlsEnabled = !inFlight,
            ),
        )
    }

    private fun onGestureFinished(outcome: GestureOutcome) {
        val probeListener = tapProbeOutcomeListener.takeIf { probeAttemptInFlight }
        if (probeAttemptInFlight) tapProbeOutcomeListener = null
        probeAttemptInFlight = false
        update(
            state.copy(
                callbackDurationMillis = outcome.callbackDurationMillis,
                callbackOutcome = outcome.toProbeCallbackOutcome(),
                cancellationReason = outcome.cancellationReason,
                gestureInFlight = false,
                controlsEnabled = state.connected && !state.restartRequired,
            ),
        )
        probeListener?.invoke(outcome)
    }

    private fun tap(): GestureSpec.Tap {
        publishCurrentBounds()
        return GestureSpec.Tap(requireCursor(), TAP_DURATION_MILLIS)
    }

    private fun verticalSwipe(): GestureSpec.Swipe {
        val bounds = publishCurrentBounds()
        return GestureSpec.Swipe(
            start = bounds.point(xPercent = 50, yPercent = 65),
            end = bounds.point(xPercent = 50, yPercent = 35),
            durationMillis = SWIPE_DURATION_MILLIS,
        )
    }

    private fun horizontalSwipe(): GestureSpec.Swipe {
        val bounds = publishCurrentBounds()
        return GestureSpec.Swipe(
            start = bounds.point(xPercent = 65, yPercent = 50),
            end = bounds.point(xPercent = 35, yPercent = 50),
            durationMillis = SWIPE_DURATION_MILLIS,
        )
    }

    private fun dragPath(): GestureSpec.Drag {
        val bounds = publishCurrentBounds()
        return GestureSpec.Drag(
            points = listOf(
                bounds.point(xPercent = 40, yPercent = 50),
                bounds.point(xPercent = 50, yPercent = 50),
                bounds.point(xPercent = 60, yPercent = 50),
            ),
            durationMillis = DRAG_DURATION_MILLIS,
        )
    }

    private fun movementStep(bounds: DisplayBounds): Float {
        return (min(bounds.width, bounds.height) * MOVE_PERCENT / 100)
            .coerceAtLeast(1)
            .toFloat()
    }

    private fun publishCurrentBounds(): DisplayBounds {
        val bounds = runtime.currentBounds()
        update(
            state.copy(
                bounds = bounds,
                cursor = state.cursor?.let(bounds::clamp) ?: bounds.center(),
            ),
        )
        return bounds
    }

    private fun requireCursor(): PointF = checkNotNull(state.cursor)

    private fun GestureOutcome.toProbeCallbackOutcome(): ProbeCallbackOutcome =
        when (cancellationReason) {
            null -> ProbeCallbackOutcome.COMPLETED
            CancelReason.TIMEOUT -> ProbeCallbackOutcome.TIMEOUT
            else -> ProbeCallbackOutcome.CANCELLED
        }

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

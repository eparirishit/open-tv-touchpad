package dev.opentvtouchpad.accessibility

import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.SubmitResult
import dev.opentvtouchpad.setup.TapProbeRequest

internal const val FRAME_TIMEOUT_MILLIS = 250L
internal const val RESTORE_DELAY_MILLIS = 100L

internal data class ControllerHandoffToken(val value: Long)

internal enum class ControllerHandoffState {
    IDLE,
    ARMED,
    WAITING_FOR_FRAME,
    DISPATCHING,
    RESTORE_PENDING,
    DEGRADED,
}

internal enum class ControllerHandoffTerminalState {
    COMPLETED,
    REJECTED,
    CANCELLED,
    TIMEOUT,
    HANDOFF_FRAME_TIMEOUT,
}

internal sealed interface ControllerHandoffEvent {
    data class ConfirmDown(
        val keyCode: Int,
        val request: TapProbeRequest,
    ) : ControllerHandoffEvent

    data class ConfirmUp(val keyCode: Int) : ControllerHandoffEvent

    data class FrameReady(val token: ControllerHandoffToken) : ControllerHandoffEvent

    data class FrameDeadline(val token: ControllerHandoffToken) : ControllerHandoffEvent

    data class DispatchReturned(
        val token: ControllerHandoffToken,
        val result: SubmitResult,
    ) : ControllerHandoffEvent

    data class GestureFinished(
        val token: ControllerHandoffToken,
        val outcome: GestureOutcome,
    ) : ControllerHandoffEvent

    data class RestoreDeadline(val token: ControllerHandoffToken) : ControllerHandoffEvent

    data object LifecycleStopped : ControllerHandoffEvent
}

internal sealed interface ControllerHandoffEffect {
    data object RemoveController : ControllerHandoffEffect

    data class RequestFrame(val token: ControllerHandoffToken) : ControllerHandoffEffect

    data class CancelFrame(val token: ControllerHandoffToken) : ControllerHandoffEffect

    data class ScheduleFrameDeadline(
        val token: ControllerHandoffToken,
        val delayMillis: Long,
    ) : ControllerHandoffEffect

    data class CancelFrameDeadline(val token: ControllerHandoffToken) : ControllerHandoffEffect

    data class SubmitRequest(
        val token: ControllerHandoffToken,
        val request: TapProbeRequest,
    ) : ControllerHandoffEffect

    data class ScheduleRestoreDeadline(
        val token: ControllerHandoffToken,
        val delayMillis: Long,
    ) : ControllerHandoffEffect

    data class CancelRestoreDeadline(val token: ControllerHandoffToken) : ControllerHandoffEffect

    data class RestoreController(val token: ControllerHandoffToken) : ControllerHandoffEffect

    data object HideCursor : ControllerHandoffEffect

    data object ClearPendingInput : ControllerHandoffEffect

    data object RequireRestart : ControllerHandoffEffect

    data class PublishTerminalState(
        val state: ControllerHandoffTerminalState,
    ) : ControllerHandoffEffect

    data object RemoveBothOverlays : ControllerHandoffEffect
}

internal class ControllerHandoffMachine {
    var state = ControllerHandoffState.IDLE
        private set

    private var nextTokenValue = 1L
    private var active: ActiveHandoff? = null

    fun handle(event: ControllerHandoffEvent): List<ControllerHandoffEffect> = when (event) {
        is ControllerHandoffEvent.ConfirmDown -> handleConfirmDown(event)
        is ControllerHandoffEvent.ConfirmUp -> handleConfirmUp(event)
        is ControllerHandoffEvent.FrameReady -> handleFrameReady(event)
        is ControllerHandoffEvent.FrameDeadline -> handleFrameDeadline(event)
        is ControllerHandoffEvent.DispatchReturned -> handleDispatchReturned(event)
        is ControllerHandoffEvent.GestureFinished -> handleGestureFinished(event)
        is ControllerHandoffEvent.RestoreDeadline -> handleRestoreDeadline(event)
        ControllerHandoffEvent.LifecycleStopped -> handleLifecycleStopped()
    }

    private fun handleConfirmDown(
        event: ControllerHandoffEvent.ConfirmDown,
    ): List<ControllerHandoffEffect> {
        if (state != ControllerHandoffState.IDLE) return emptyList()

        active = ActiveHandoff(
            token = ControllerHandoffToken(nextTokenValue++),
            confirmKeyCode = event.keyCode,
            request = event.request,
        )
        state = ControllerHandoffState.ARMED
        return emptyList()
    }

    private fun handleConfirmUp(
        event: ControllerHandoffEvent.ConfirmUp,
    ): List<ControllerHandoffEffect> {
        val current = active ?: return emptyList()
        if (state != ControllerHandoffState.ARMED || event.keyCode != current.confirmKeyCode) {
            return emptyList()
        }

        state = ControllerHandoffState.WAITING_FOR_FRAME
        return listOf(
            ControllerHandoffEffect.RemoveController,
            ControllerHandoffEffect.RequestFrame(current.token),
            ControllerHandoffEffect.ScheduleFrameDeadline(current.token, FRAME_TIMEOUT_MILLIS),
        )
    }

    private fun handleFrameReady(
        event: ControllerHandoffEvent.FrameReady,
    ): List<ControllerHandoffEffect> {
        val current = activeFor(ControllerHandoffState.WAITING_FOR_FRAME, event.token)
            ?: return emptyList()

        state = ControllerHandoffState.DISPATCHING
        return listOf(
            ControllerHandoffEffect.CancelFrameDeadline(current.token),
            ControllerHandoffEffect.SubmitRequest(current.token, current.request),
        )
    }

    private fun handleFrameDeadline(
        event: ControllerHandoffEvent.FrameDeadline,
    ): List<ControllerHandoffEffect> {
        val current = activeFor(ControllerHandoffState.WAITING_FOR_FRAME, event.token)
            ?: return emptyList()

        return degrade(
            terminalState = ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT,
            firstEffect = ControllerHandoffEffect.CancelFrame(current.token),
        )
    }

    private fun handleDispatchReturned(
        event: ControllerHandoffEvent.DispatchReturned,
    ): List<ControllerHandoffEffect> {
        val current = activeFor(ControllerHandoffState.DISPATCHING, event.token)
            ?.takeUnless { it.dispatchAccepted }
            ?: return emptyList()

        if (event.result == SubmitResult.ACCEPTED) {
            current.dispatchAccepted = true
            return emptyList()
        }
        return degrade(ControllerHandoffTerminalState.REJECTED)
    }

    private fun handleGestureFinished(
        event: ControllerHandoffEvent.GestureFinished,
    ): List<ControllerHandoffEffect> {
        val current = activeFor(ControllerHandoffState.DISPATCHING, event.token)
            ?.takeIf { it.dispatchAccepted }
            ?: return emptyList()

        val cancellationReason = event.outcome.cancellationReason
        if (cancellationReason != null) {
            return degrade(
                if (cancellationReason == CancelReason.TIMEOUT) {
                    ControllerHandoffTerminalState.TIMEOUT
                } else {
                    ControllerHandoffTerminalState.CANCELLED
                },
            )
        }

        state = ControllerHandoffState.RESTORE_PENDING
        return listOf(
            ControllerHandoffEffect.PublishTerminalState(ControllerHandoffTerminalState.COMPLETED),
            ControllerHandoffEffect.ScheduleRestoreDeadline(current.token, RESTORE_DELAY_MILLIS),
        )
    }

    private fun handleRestoreDeadline(
        event: ControllerHandoffEvent.RestoreDeadline,
    ): List<ControllerHandoffEffect> {
        val current = activeFor(ControllerHandoffState.RESTORE_PENDING, event.token)
            ?: return emptyList()

        state = ControllerHandoffState.IDLE
        active = null
        return listOf(ControllerHandoffEffect.RestoreController(current.token))
    }

    private fun handleLifecycleStopped(): List<ControllerHandoffEffect> {
        val current = active
        val effects = buildList {
            when (state) {
                ControllerHandoffState.WAITING_FOR_FRAME -> current?.let {
                    add(ControllerHandoffEffect.CancelFrame(it.token))
                    add(ControllerHandoffEffect.CancelFrameDeadline(it.token))
                }
                ControllerHandoffState.RESTORE_PENDING -> current?.let {
                    add(ControllerHandoffEffect.CancelRestoreDeadline(it.token))
                }
                else -> Unit
            }
            add(ControllerHandoffEffect.ClearPendingInput)
            add(ControllerHandoffEffect.RemoveBothOverlays)
        }
        state = ControllerHandoffState.IDLE
        active = null
        return effects
    }

    private fun activeFor(
        requiredState: ControllerHandoffState,
        token: ControllerHandoffToken,
    ): ActiveHandoff? = active?.takeIf {
        state == requiredState && it.token == token
    }

    private fun degrade(
        terminalState: ControllerHandoffTerminalState,
        firstEffect: ControllerHandoffEffect? = null,
    ): List<ControllerHandoffEffect> {
        state = ControllerHandoffState.DEGRADED
        active = null
        return buildList {
            firstEffect?.let(::add)
            add(ControllerHandoffEffect.HideCursor)
            add(ControllerHandoffEffect.ClearPendingInput)
            add(ControllerHandoffEffect.RequireRestart)
            add(ControllerHandoffEffect.PublishTerminalState(terminalState))
        }
    }

    private data class ActiveHandoff(
        val token: ControllerHandoffToken,
        val confirmKeyCode: Int,
        val request: TapProbeRequest,
        var dispatchAccepted: Boolean = false,
    )
}

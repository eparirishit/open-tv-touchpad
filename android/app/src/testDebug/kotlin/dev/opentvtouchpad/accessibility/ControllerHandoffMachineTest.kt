package dev.opentvtouchpad.accessibility

import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import dev.opentvtouchpad.setup.TapProbeConfig
import dev.opentvtouchpad.setup.TapProbeMode
import dev.opentvtouchpad.setup.TapProbeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerHandoffMachineTest {
    @Test
    fun repeatKeyDownDoesNotReplaceTheArmedRequest() {
        val machine = ControllerHandoffMachine()
        val replacement = request(durationMillis = 200L)

        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.ConfirmDown(CONFIRM_KEY, REQUEST)))
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.ConfirmDown(CONFIRM_KEY, replacement)))
        val token = releaseController(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.CancelFrameDeadline(token),
                ControllerHandoffEffect.SubmitRequest(token, REQUEST),
            ),
            machine.handle(ControllerHandoffEvent.FrameReady(token)),
        )
    }

    @Test
    fun missingMismatchedAndDuplicateKeyUpCannotRemoveController() {
        val machine = ControllerHandoffMachine()

        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.ConfirmUp(CONFIRM_KEY)))
        machine.handle(ControllerHandoffEvent.ConfirmDown(CONFIRM_KEY, REQUEST))
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.ConfirmUp(CONFIRM_KEY + 1)))

        val firstRelease = machine.handle(ControllerHandoffEvent.ConfirmUp(CONFIRM_KEY))

        assertEquals(ControllerHandoffState.WAITING_FOR_FRAME, machine.state)
        assertEquals(1, firstRelease.count { it == ControllerHandoffEffect.RemoveController })
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.ConfirmUp(CONFIRM_KEY)))
    }

    @Test
    fun matchingKeyUpRemovesControllerAndSchedulesOneBoundedFrameWait() {
        val machine = ControllerHandoffMachine()
        machine.handle(ControllerHandoffEvent.ConfirmDown(CONFIRM_KEY, REQUEST))
        val effects = machine.handle(ControllerHandoffEvent.ConfirmUp(CONFIRM_KEY))
        val token = (effects[1] as ControllerHandoffEffect.RequestFrame).token

        assertEquals(
            listOf(
                ControllerHandoffEffect.RemoveController,
                ControllerHandoffEffect.RequestFrame(token),
                ControllerHandoffEffect.ScheduleFrameDeadline(token, 250L),
            ),
            effects,
        )
    }

    @Test
    fun frameReadySubmitsThePreparedRequestOnce() {
        val machine = ControllerHandoffMachine()
        val token = armAndRelease(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.CancelFrameDeadline(token),
                ControllerHandoffEffect.SubmitRequest(token, REQUEST),
            ),
            machine.handle(ControllerHandoffEvent.FrameReady(token)),
        )
        assertEquals(ControllerHandoffState.DISPATCHING, machine.state)
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.FrameReady(token)))
    }

    @Test
    fun frameTimeoutFailsClosedAndIgnoresTheLateFrame() {
        val machine = ControllerHandoffMachine()
        val token = armAndRelease(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.CancelFrame(token),
                ControllerHandoffEffect.HideCursor,
                ControllerHandoffEffect.ClearPendingInput,
                ControllerHandoffEffect.RequireRestart,
                ControllerHandoffEffect.PublishTerminalState(
                    ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT,
                ),
            ),
            machine.handle(ControllerHandoffEvent.FrameDeadline(token)),
        )
        assertEquals(ControllerHandoffState.DEGRADED, machine.state)
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.FrameReady(token)))
    }

    @Test
    fun acceptedCompletionSchedulesAndRestoresTheControllerOnceAfterOneHundredMilliseconds() {
        val machine = ControllerHandoffMachine()
        val token = beginAcceptedDispatch(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.PublishTerminalState(ControllerHandoffTerminalState.COMPLETED),
                ControllerHandoffEffect.ScheduleRestoreDeadline(token, 100L),
            ),
            machine.handle(ControllerHandoffEvent.GestureFinished(token, COMPLETED)),
        )
        assertEquals(ControllerHandoffState.RESTORE_PENDING, machine.state)
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.GestureFinished(token, COMPLETED)))
        assertEquals(
            listOf(ControllerHandoffEffect.RestoreController(token)),
            machine.handle(ControllerHandoffEvent.RestoreDeadline(token)),
        )
        assertEquals(ControllerHandoffState.IDLE, machine.state)
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.RestoreDeadline(token)))
    }

    @Test
    fun everySynchronousSubmissionFailureDegradesWithoutRestoration() {
        listOf(SubmitResult.REJECTED, SubmitResult.DUPLICATE, SubmitResult.IN_FLIGHT).forEach { result ->
            val machine = ControllerHandoffMachine()
            val token = beginDispatch(machine)

            assertEquals(
                degradedEffects(ControllerHandoffTerminalState.REJECTED),
                machine.handle(ControllerHandoffEvent.DispatchReturned(token, result)),
            )
            assertEquals(ControllerHandoffState.DEGRADED, machine.state)
            assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.RestoreDeadline(token)))
        }
    }

    @Test
    fun cancellationAndCallbackTimeoutDegradeWithoutRestoration() {
        listOf(
            CancelReason.EXPLICIT to ControllerHandoffTerminalState.CANCELLED,
            CancelReason.ANDROID to ControllerHandoffTerminalState.CANCELLED,
            CancelReason.TIMEOUT to ControllerHandoffTerminalState.TIMEOUT,
        ).forEach { (reason, terminalState) ->
            val machine = ControllerHandoffMachine()
            val token = beginAcceptedDispatch(machine)
            val outcome = GestureOutcome(callbackDurationMillis = 321L, cancellationReason = reason)

            assertEquals(
                degradedEffects(terminalState),
                machine.handle(ControllerHandoffEvent.GestureFinished(token, outcome)),
            )
            assertEquals(ControllerHandoffState.DEGRADED, machine.state)
            assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.RestoreDeadline(token)))
        }
    }

    @Test
    fun degradedMachineNeverRetriesAutomatically() {
        val machine = ControllerHandoffMachine()
        val token = beginDispatch(machine)
        machine.handle(ControllerHandoffEvent.DispatchReturned(token, SubmitResult.REJECTED))

        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.ConfirmDown(CONFIRM_KEY, REQUEST)))
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.FrameReady(token)))
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.DispatchReturned(token, SubmitResult.ACCEPTED)))
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.GestureFinished(token, COMPLETED)))
        assertEquals(ControllerHandoffState.DEGRADED, machine.state)
    }

    @Test
    fun lifecycleStopCancelsFrameWorkRemovesBothOverlaysAndFencesTheOldFrame() {
        val machine = ControllerHandoffMachine()
        val oldToken = armAndRelease(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.CancelFrame(oldToken),
                ControllerHandoffEffect.CancelFrameDeadline(oldToken),
                ControllerHandoffEffect.ClearPendingInput,
                ControllerHandoffEffect.RemoveBothOverlays,
            ),
            machine.handle(ControllerHandoffEvent.LifecycleStopped),
        )
        val newToken = armAndRelease(machine)

        assertTrue(newToken.value > oldToken.value)
        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.FrameReady(oldToken)))
        assertEquals(
            listOf(
                ControllerHandoffEffect.CancelFrameDeadline(newToken),
                ControllerHandoffEffect.SubmitRequest(newToken, REQUEST),
            ),
            machine.handle(ControllerHandoffEvent.FrameReady(newToken)),
        )
    }

    @Test
    fun lifecycleStopDuringDispatchIgnoresTheOldGestureCallback() {
        val machine = ControllerHandoffMachine()
        val oldToken = beginAcceptedDispatch(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.ClearPendingInput,
                ControllerHandoffEffect.RemoveBothOverlays,
            ),
            machine.handle(ControllerHandoffEvent.LifecycleStopped),
        )
        val newToken = beginAcceptedDispatch(machine)

        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.GestureFinished(oldToken, COMPLETED)))
        assertEquals(ControllerHandoffState.DISPATCHING, machine.state)
        assertEquals(
            listOf(
                ControllerHandoffEffect.PublishTerminalState(ControllerHandoffTerminalState.COMPLETED),
                ControllerHandoffEffect.ScheduleRestoreDeadline(newToken, 100L),
            ),
            machine.handle(ControllerHandoffEvent.GestureFinished(newToken, COMPLETED)),
        )
    }

    @Test
    fun lifecycleStopCancelsRestorationAndIgnoresTheOldRestoreCallback() {
        val machine = ControllerHandoffMachine()
        val oldToken = beginRestoreWait(machine)

        assertEquals(
            listOf(
                ControllerHandoffEffect.CancelRestoreDeadline(oldToken),
                ControllerHandoffEffect.ClearPendingInput,
                ControllerHandoffEffect.RemoveBothOverlays,
            ),
            machine.handle(ControllerHandoffEvent.LifecycleStopped),
        )
        val newToken = beginRestoreWait(machine)

        assertEquals(emptyList<ControllerHandoffEffect>(), machine.handle(ControllerHandoffEvent.RestoreDeadline(oldToken)))
        assertEquals(ControllerHandoffState.RESTORE_PENDING, machine.state)
        assertEquals(
            listOf(ControllerHandoffEffect.RestoreController(newToken)),
            machine.handle(ControllerHandoffEvent.RestoreDeadline(newToken)),
        )
    }

    private fun armAndRelease(machine: ControllerHandoffMachine): ControllerHandoffToken {
        machine.handle(ControllerHandoffEvent.ConfirmDown(CONFIRM_KEY, REQUEST))
        return releaseController(machine)
    }

    private fun releaseController(machine: ControllerHandoffMachine): ControllerHandoffToken {
        val effects = machine.handle(ControllerHandoffEvent.ConfirmUp(CONFIRM_KEY))
        return (effects.single { it is ControllerHandoffEffect.RequestFrame } as ControllerHandoffEffect.RequestFrame).token
    }

    private fun beginDispatch(machine: ControllerHandoffMachine): ControllerHandoffToken =
        armAndRelease(machine).also { token ->
            machine.handle(ControllerHandoffEvent.FrameReady(token))
        }

    private fun beginAcceptedDispatch(machine: ControllerHandoffMachine): ControllerHandoffToken =
        beginDispatch(machine).also { token ->
            assertEquals(
                emptyList<ControllerHandoffEffect>(),
                machine.handle(ControllerHandoffEvent.DispatchReturned(token, SubmitResult.ACCEPTED)),
            )
        }

    private fun beginRestoreWait(machine: ControllerHandoffMachine): ControllerHandoffToken =
        beginAcceptedDispatch(machine).also { token ->
            machine.handle(ControllerHandoffEvent.GestureFinished(token, COMPLETED))
        }

    private fun degradedEffects(terminalState: ControllerHandoffTerminalState) = listOf(
        ControllerHandoffEffect.HideCursor,
        ControllerHandoffEffect.ClearPendingInput,
        ControllerHandoffEffect.RequireRestart,
        ControllerHandoffEffect.PublishTerminalState(terminalState),
    )

    private fun request(durationMillis: Long) = TapProbeRequest(
        config = TapProbeConfig(
            mode = TapProbeMode.CONTROLLER_HANDOFF,
            durationMillis = durationMillis,
            requiresControllerHandoff = true,
        ),
        gesture = GestureSpec.Tap(point = PointF(100f, 200f), durationMillis = durationMillis),
    )

    private companion object {
        const val CONFIRM_KEY = 23
        val REQUEST = TapProbeRequest(
            config = TapProbeConfig(
                mode = TapProbeMode.CONTROLLER_HANDOFF,
                durationMillis = 137L,
                requiresControllerHandoff = true,
            ),
            gesture = GestureSpec.Tap(point = PointF(100f, 200f), durationMillis = 137L),
        )
        val COMPLETED = GestureOutcome(callbackDurationMillis = 93L, cancellationReason = null)
    }
}

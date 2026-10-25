package dev.opentvtouchpad.setup

import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import dev.opentvtouchpad.accessibility.ControllerHandoffTerminalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateViewModelTest {
    @Test
    fun disconnectedAccessibilityShowsSettingsGuidanceAndDisablesActions() {
        val fixture = Fixture(connected = false)

        fixture.viewModel.refresh()

        assertFalse(fixture.viewModel.state.controlsEnabled)
        assertTrue(fixture.viewModel.state.settingsGuidanceVisible)
        assertNull(fixture.viewModel.state.bounds)
    }

    @Test
    fun connectionLossWhileRefreshingShowsSettingsGuidance() {
        val fixture = Fixture(disconnectAfterFirstConnectionCheck = true)

        fixture.viewModel.refresh()

        assertFalse(fixture.viewModel.state.connected)
        assertFalse(fixture.viewModel.state.controlsEnabled)
        assertTrue(fixture.viewModel.state.settingsGuidanceVisible)
    }

    @Test
    fun keyRepeatMovesCursorWithoutDuplicatingTapOrDrag() {
        val fixture = Fixture()
        fixture.viewModel.refresh()

        fixture.perform(GateAction.SHOW_CURSOR)
        fixture.perform(GateAction.MOVE_RIGHT)
        fixture.perform(GateAction.MOVE_RIGHT, isRepeat = true)
        fixture.perform(GateAction.TAP)
        fixture.finish(callbackDurationMillis = 80)
        fixture.perform(GateAction.TAP, isRepeat = true)
        fixture.perform(GateAction.DRAG_PATH)
        fixture.finish(callbackDurationMillis = 500)
        fixture.perform(GateAction.DRAG_PATH, isRepeat = true)

        assertEquals(PointF(960f, 540f), fixture.shown.single())
        assertEquals(listOf(PointF(1_014f, 540f), PointF(1_068f, 540f)), fixture.moved)
        assertEquals(2, fixture.submitted.size)
        assertTrue(fixture.submitted[0] is GestureSpec.Tap)
        assertTrue(fixture.submitted[1] is GestureSpec.Drag)
    }

    @Test
    fun gesturePathsUseFixedConservativeFractionsOfCurrentBounds() {
        val fixture = Fixture()
        fixture.viewModel.refresh()

        fixture.perform(GateAction.SWIPE_VERTICAL)
        fixture.finish(callbackDurationMillis = 250)
        fixture.perform(GateAction.SWIPE_HORIZONTAL)
        fixture.finish(callbackDurationMillis = 250)
        fixture.perform(GateAction.DRAG_PATH)

        assertEquals(
            GestureSpec.Swipe(
                start = PointF(960f, 702f),
                end = PointF(960f, 378f),
                durationMillis = 250,
            ),
            fixture.submitted[0],
        )
        assertEquals(
            GestureSpec.Swipe(
                start = PointF(1_248f, 540f),
                end = PointF(672f, 540f),
                durationMillis = 250,
            ),
            fixture.submitted[1],
        )
        assertEquals(
            GestureSpec.Drag(
                points = listOf(
                    PointF(768f, 540f),
                    PointF(960f, 540f),
                    PointF(1_152f, 540f),
                ),
                durationMillis = 500,
            ),
            fixture.submitted[2],
        )
    }

    @Test
    fun movementAndEveryGestureRefreshCurrentBoundsBeforeDerivingCoordinates() {
        val fixture = Fixture()
        fixture.viewModel.refresh()

        val showFixture = Fixture()
        showFixture.viewModel.refresh()
        showFixture.bounds = DisplayBounds(width = 800, height = 600)
        showFixture.perform(GateAction.SHOW_CURSOR)
        assertEquals(DisplayBounds(800, 600), showFixture.viewModel.state.bounds)
        assertEquals(PointF(799f, 540f), showFixture.shown.single())

        fixture.bounds = DisplayBounds(width = 1280, height = 720)
        fixture.perform(GateAction.MOVE_RIGHT)
        assertEquals(DisplayBounds(1280, 720), fixture.viewModel.state.bounds)
        assertEquals(PointF(996f, 540f), fixture.moved.single())

        fixture.bounds = DisplayBounds(width = 800, height = 600)
        fixture.perform(GateAction.TAP)
        assertEquals(DisplayBounds(800, 600), fixture.viewModel.state.bounds)
        assertEquals(
            GestureSpec.Tap(PointF(799f, 540f), durationMillis = 80),
            fixture.submitted.last(),
        )
        fixture.finish(callbackDurationMillis = 80)

        fixture.bounds = DisplayBounds(width = 1000, height = 500)
        fixture.perform(GateAction.SWIPE_VERTICAL)
        assertEquals(DisplayBounds(1000, 500), fixture.viewModel.state.bounds)
        assertEquals(
            GestureSpec.Swipe(
                start = PointF(500f, 325f),
                end = PointF(500f, 175f),
                durationMillis = 250,
            ),
            fixture.submitted.last(),
        )
        fixture.finish(callbackDurationMillis = 250)

        fixture.bounds = DisplayBounds(width = 600, height = 1000)
        fixture.perform(GateAction.SWIPE_HORIZONTAL)
        assertEquals(DisplayBounds(600, 1000), fixture.viewModel.state.bounds)
        assertEquals(
            GestureSpec.Swipe(
                start = PointF(390f, 500f),
                end = PointF(210f, 500f),
                durationMillis = 250,
            ),
            fixture.submitted.last(),
        )
        fixture.finish(callbackDurationMillis = 250)

        fixture.bounds = DisplayBounds(width = 1200, height = 800)
        fixture.perform(GateAction.DRAG_PATH)
        assertEquals(DisplayBounds(1200, 800), fixture.viewModel.state.bounds)
        assertEquals(
            GestureSpec.Drag(
                points = listOf(
                    PointF(480f, 400f),
                    PointF(600f, 400f),
                    PointF(720f, 400f),
                ),
                durationMillis = 500,
            ),
            fixture.submitted.last(),
        )
    }

    @Test
    fun acceptedGestureDisablesDispatchUntilCallbackPublishesOutcome() {
        val fixture = Fixture()
        fixture.viewModel.refresh()

        fixture.perform(GateAction.TAP)

        assertFalse(fixture.viewModel.state.controlsEnabled)
        assertTrue(fixture.viewModel.state.gestureInFlight)
        assertEquals(SubmitResult.ACCEPTED, fixture.viewModel.state.lastDispatchResult)

        fixture.perform(GateAction.SWIPE_VERTICAL)
        assertEquals(1, fixture.submitted.size)

        fixture.finish(callbackDurationMillis = 93, cancellationReason = CancelReason.ANDROID)

        assertTrue(fixture.viewModel.state.controlsEnabled)
        assertFalse(fixture.viewModel.state.gestureInFlight)
        assertEquals(93L, fixture.viewModel.state.callbackDurationMillis)
        assertEquals(CancelReason.ANDROID, fixture.viewModel.state.cancellationReason)
    }

    @Test
    fun cancelStaysAvailableForAnInFlightGesture() {
        val fixture = Fixture()
        fixture.viewModel.refresh()
        fixture.perform(GateAction.DRAG_PATH)

        fixture.perform(GateAction.CANCEL)

        assertEquals(1, fixture.cancellationCount)
        assertTrue(fixture.viewModel.state.gestureInFlight)
    }

    @Test
    fun refreshRecoversTerminalOutcomeDeliveredWhileListenerWasDetached() {
        val fixture = Fixture()
        fixture.viewModel.observe {}
        fixture.viewModel.refresh()
        fixture.perform(GateAction.DRAG_PATH)

        fixture.viewModel.detach()
        fixture.finish(
            callbackDurationMillis = 527,
            cancellationReason = CancelReason.ANDROID,
        )
        fixture.viewModel.observe {}
        fixture.viewModel.refresh()

        assertFalse(fixture.viewModel.state.gestureInFlight)
        assertTrue(fixture.viewModel.state.controlsEnabled)
        assertEquals(527L, fixture.viewModel.state.callbackDurationMillis)
        assertEquals(CancelReason.ANDROID, fixture.viewModel.state.cancellationReason)
    }

    @Test
    fun prepareTapRefreshesBoundsWithoutDispatching() {
        val fixture = Fixture(systemTapTimeoutMillis = 137L)
        fixture.viewModel.refresh()
        fixture.bounds = DisplayBounds(width = 800, height = 600)
        fixture.viewModel.selectTapProbeMode(TapProbeMode.SYSTEM_TAP_TIMEOUT)

        val request = fixture.viewModel.prepareTapProbe()

        assertEquals(DisplayBounds(width = 800, height = 600), fixture.viewModel.state.bounds)
        assertEquals(TapProbeMode.SYSTEM_TAP_TIMEOUT, fixture.viewModel.state.tapProbeMode)
        assertEquals(
            TapProbeRequest(
                config = TapProbeConfig(
                    mode = TapProbeMode.SYSTEM_TAP_TIMEOUT,
                    durationMillis = 137L,
                    requiresControllerHandoff = false,
                ),
                gesture = GestureSpec.Tap(
                    point = PointF(799f, 540f),
                    durationMillis = 137L,
                ),
            ),
            request,
        )
        assertTrue(fixture.submitted.isEmpty())
    }

    @Test
    fun prepareTapRequiresConnectedIdleControls() {
        val notRefreshed = Fixture()
        assertNull(notRefreshed.viewModel.prepareTapProbe())

        val disconnected = Fixture()
        disconnected.viewModel.refresh()
        disconnected.connected = false
        assertNull(disconnected.viewModel.prepareTapProbe())

        val inFlight = Fixture()
        inFlight.viewModel.refresh()
        inFlight.perform(GateAction.TAP)
        assertNull(inFlight.viewModel.prepareTapProbe())
    }

    @Test
    fun prepareTapRejectsAuthoritativeInFlightGestureWhenCachedStateIsIdle() {
        val fixture = Fixture()
        fixture.viewModel.refresh()
        fixture.startExternalGesture()

        assertTrue(fixture.viewModel.state.controlsEnabled)
        assertFalse(fixture.viewModel.state.gestureInFlight)
        assertNull(fixture.viewModel.prepareTapProbe())
    }

    @Test
    fun submitTapDispatchesPreparedRequestExactlyOnce() {
        val fixture = Fixture()
        fixture.viewModel.refresh()
        val request = checkNotNull(fixture.viewModel.prepareTapProbe())

        assertEquals(SubmitResult.REJECTED, fixture.viewModel.submitTapProbe(request.copy()))
        assertEquals(SubmitResult.ACCEPTED, fixture.viewModel.submitTapProbe(request))
        assertEquals(SubmitResult.REJECTED, fixture.viewModel.submitTapProbe(request))
        assertEquals(listOf(request.gesture), fixture.submitted)
    }

    @Test
    fun probeOutcomeListenerReceivesOnlyThePreparedAttempt() {
        val fixture = Fixture()
        val probeOutcomes = mutableListOf<GestureOutcome>()
        fixture.viewModel.setTapProbeOutcomeListener(probeOutcomes::add)
        fixture.viewModel.refresh()

        fixture.perform(GateAction.TAP)
        fixture.finish(callbackDurationMillis = 80L)
        assertTrue(probeOutcomes.isEmpty())

        val request = checkNotNull(fixture.viewModel.prepareTapProbe())
        fixture.viewModel.submitTapProbe(request)
        fixture.finish(callbackDurationMillis = 93L)

        fixture.viewModel.setTapProbeOutcomeListener(probeOutcomes::add)
        fixture.viewModel.detach()
        fixture.viewModel.observe {}
        fixture.viewModel.refresh()
        val detachedRequest = checkNotNull(fixture.viewModel.prepareTapProbe())
        fixture.viewModel.submitTapProbe(detachedRequest)
        fixture.finish(callbackDurationMillis = 101L)

        assertEquals(
            listOf(GestureOutcome(callbackDurationMillis = 93L, cancellationReason = null)),
            probeOutcomes,
        )
    }

    @Test
    fun modeSelectionDoesNotAdvancePastControllerHandoff() {
        val fixture = Fixture()

        assertEquals(TapProbeMode.BASELINE_80_MS, fixture.viewModel.state.tapProbeMode)
        fixture.viewModel.selectTapProbeMode(TapProbeMode.SYSTEM_TAP_TIMEOUT)
        assertEquals(TapProbeMode.SYSTEM_TAP_TIMEOUT, fixture.viewModel.state.tapProbeMode)
        fixture.viewModel.selectTapProbeMode(TapProbeMode.CONTROLLER_HANDOFF)
        fixture.viewModel.selectTapProbeMode(TapProbeMode.CONTROLLER_HANDOFF)

        assertEquals(TapProbeMode.CONTROLLER_HANDOFF, fixture.viewModel.state.tapProbeMode)
    }

    @Test
    fun handoffFailureHidesCursorPublishesTerminalStateAndRequiresRestart() {
        val fixture = Fixture()
        fixture.viewModel.refresh()

        val request = checkNotNull(fixture.viewModel.prepareTapProbe())
        fixture.viewModel.submitTapProbe(request)
        fixture.finish(callbackDurationMillis = 93L)

        fixture.viewModel.hideCursor()
        fixture.viewModel.requireRestart()
        fixture.viewModel.publishControllerHandoffTerminalState(
            ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT,
        )
        fixture.viewModel.refresh()

        assertEquals(1, fixture.hideCursorCount)
        assertTrue(fixture.viewModel.state.restartRequired)
        assertFalse(fixture.viewModel.state.controlsEnabled)
        assertEquals(
            ControllerHandoffTerminalState.HANDOFF_FRAME_TIMEOUT,
            fixture.viewModel.state.controllerHandoffTerminalState,
        )
        assertEquals(
            ProbeCallbackOutcome.HANDOFF_FRAME_TIMEOUT,
            fixture.viewModel.state.callbackOutcome,
        )
        assertEquals(null, fixture.viewModel.state.lastDispatchResult)
        assertEquals(null, fixture.viewModel.state.callbackDurationMillis)
    }

    @Test
    fun clearPendingInputRejectsThePreviouslyPreparedProbe() {
        val fixture = Fixture()
        fixture.viewModel.refresh()
        val request = checkNotNull(fixture.viewModel.prepareTapProbe())

        fixture.viewModel.clearPendingInput()

        assertEquals(SubmitResult.REJECTED, fixture.viewModel.submitTapProbe(request))
        assertTrue(fixture.submitted.isEmpty())
    }

    @Test
    fun submittingAnotherProbeClearsThePreviousManualResult() {
        val fixture = Fixture()
        fixture.viewModel.refresh()
        fixture.viewModel.recordManualTargetResult(ManualTargetResult.ACTIVATED)
        val request = checkNotNull(fixture.viewModel.prepareTapProbe())

        fixture.viewModel.submitTapProbe(request)

        assertEquals(null, fixture.viewModel.state.manualTargetResult)
    }

    private class Fixture(
        var connected: Boolean = true,
        var bounds: DisplayBounds = DisplayBounds(width = 1920, height = 1080),
        private val disconnectAfterFirstConnectionCheck: Boolean = false,
        systemTapTimeoutMillis: Long = 137L,
    ) : GateRuntime {
        val shown = mutableListOf<PointF>()
        val moved = mutableListOf<PointF>()
        val submitted = mutableListOf<GestureSpec>()
        var cancellationCount = 0
        var hideCursorCount = 0
        var restartRequired = false
        val viewModel = GateViewModel(this, systemTapTimeoutMillis)
        private var outcomeListener: ((GestureOutcome) -> Unit)? = null
        private var authoritativeInFlight = false
        private var latestOutcome: GestureOutcome? = null
        private var connectionChecks = 0

        override val isConnected: Boolean
            get() {
                connectionChecks += 1
                return connected.also {
                    if (disconnectAfterFirstConnectionCheck && connectionChecks == 1) {
                        connected = false
                    }
                }
            }

        override fun currentBounds(): DisplayBounds = bounds

        override fun showCursorAt(point: PointF) {
            shown += point
        }

        override fun moveCursorTo(point: PointF) {
            moved += point
        }

        override fun hideCursor() {
            hideCursorCount += 1
        }

        override fun submitGesture(spec: GestureSpec): SubmitResult {
            if (restartRequired) return SubmitResult.REJECTED
            submitted += spec
            authoritativeInFlight = true
            latestOutcome = null
            return SubmitResult.ACCEPTED
        }

        override fun cancelGesture() {
            cancellationCount += 1
        }

        override fun requireRestart() {
            restartRequired = true
        }

        override fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?) {
            outcomeListener = listener
        }

        override fun currentGestureState(): GateRuntimeState = GateRuntimeState(
            gestureInFlight = authoritativeInFlight,
            latestOutcome = latestOutcome,
        )

        fun perform(action: GateAction, isRepeat: Boolean = false) {
            viewModel.perform(action, isRepeat)
        }

        fun startExternalGesture() {
            authoritativeInFlight = true
            latestOutcome = null
        }

        fun finish(callbackDurationMillis: Long, cancellationReason: CancelReason? = null) {
            val outcome = GestureOutcome(callbackDurationMillis, cancellationReason)
            authoritativeInFlight = false
            latestOutcome = outcome
            outcomeListener?.invoke(outcome)
        }

    }
}

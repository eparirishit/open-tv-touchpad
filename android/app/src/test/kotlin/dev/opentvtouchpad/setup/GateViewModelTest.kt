package dev.opentvtouchpad.setup

import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
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

    private class Fixture(
        var connected: Boolean = true,
        private val bounds: DisplayBounds = DisplayBounds(width = 1920, height = 1080),
        private val disconnectAfterFirstConnectionCheck: Boolean = false,
    ) : GateRuntime {
        val shown = mutableListOf<PointF>()
        val moved = mutableListOf<PointF>()
        val submitted = mutableListOf<GestureSpec>()
        var cancellationCount = 0
        val viewModel = GateViewModel(this)
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

        override fun submitGesture(spec: GestureSpec): SubmitResult {
            submitted += spec
            authoritativeInFlight = true
            latestOutcome = null
            return SubmitResult.ACCEPTED
        }

        override fun cancelGesture() {
            cancellationCount += 1
        }

        override fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?) {
            outcomeListener = listener
        }

        fun perform(action: GateAction, isRepeat: Boolean = false) {
            viewModel.perform(action, isRepeat)
        }

        fun finish(callbackDurationMillis: Long, cancellationReason: CancelReason? = null) {
            val outcome = GestureOutcome(callbackDurationMillis, cancellationReason)
            authoritativeInFlight = false
            latestOutcome = outcome
            outcomeListener?.invoke(outcome)
        }

        fun gestureInFlight(): Boolean = authoritativeInFlight

        fun latestGestureOutcome(): GestureOutcome? = latestOutcome
    }
}

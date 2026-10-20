package dev.opentvtouchpad.setup

import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import java.lang.reflect.Proxy
import kotlin.jvm.functions.Function1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateViewModelTest {
    @Test
    fun disconnectedAccessibilityShowsSettingsGuidanceAndDisablesActions() {
        val fixture = Fixture(connected = false)

        fixture.refresh()

        assertFalse(fixture.booleanState("getControlsEnabled"))
        assertTrue(fixture.booleanState("getSettingsGuidanceVisible"))
        assertNull(fixture.stateValue("getBounds"))
    }

    @Test
    fun keyRepeatMovesCursorWithoutDuplicatingTapOrDrag() {
        val fixture = Fixture()
        fixture.refresh()

        fixture.perform("SHOW_CURSOR")
        fixture.perform("MOVE_RIGHT")
        fixture.perform("MOVE_RIGHT", isRepeat = true)
        fixture.perform("TAP")
        fixture.finish(callbackDurationMillis = 80, cancellationReason = null)
        fixture.perform("TAP", isRepeat = true)
        fixture.perform("DRAG_PATH")
        fixture.finish(callbackDurationMillis = 500, cancellationReason = null)
        fixture.perform("DRAG_PATH", isRepeat = true)

        assertEquals(PointF(960f, 540f), fixture.shown.single())
        assertEquals(listOf(PointF(1_014f, 540f), PointF(1_068f, 540f)), fixture.moved)
        assertEquals(2, fixture.submitted.size)
        assertTrue(fixture.submitted[0] is GestureSpec.Tap)
        assertTrue(fixture.submitted[1] is GestureSpec.Drag)
    }

    @Test
    fun gesturePathsUseFixedConservativeFractionsOfCurrentBounds() {
        val fixture = Fixture()
        fixture.refresh()

        fixture.perform("SWIPE_VERTICAL")
        fixture.finish(callbackDurationMillis = 250, cancellationReason = null)
        fixture.perform("SWIPE_HORIZONTAL")
        fixture.finish(callbackDurationMillis = 250, cancellationReason = null)
        fixture.perform("DRAG_PATH")

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
        fixture.refresh()

        fixture.perform("TAP")

        assertFalse(fixture.booleanState("getControlsEnabled"))
        assertTrue(fixture.booleanState("getGestureInFlight"))
        assertEquals(SubmitResult.ACCEPTED, fixture.stateValue("getLastDispatchResult"))

        fixture.perform("SWIPE_VERTICAL")
        assertEquals(1, fixture.submitted.size)

        fixture.finish(callbackDurationMillis = 93, cancellationReason = CancelReason.ANDROID)

        assertTrue(fixture.booleanState("getControlsEnabled"))
        assertFalse(fixture.booleanState("getGestureInFlight"))
        assertEquals(93L, fixture.stateValue("getCallbackDurationMillis"))
        assertEquals(CancelReason.ANDROID, fixture.stateValue("getCancellationReason"))
    }

    @Test
    fun cancelStaysAvailableForAnInFlightGesture() {
        val fixture = Fixture()
        fixture.refresh()
        fixture.perform("DRAG_PATH")

        fixture.perform("CANCEL")

        assertEquals(1, fixture.cancellationCount)
        assertTrue(fixture.booleanState("getGestureInFlight"))
    }

    private class Fixture(
        var connected: Boolean = true,
        private val bounds: DisplayBounds = DisplayBounds(width = 1920, height = 1080),
    ) {
        val shown = mutableListOf<PointF>()
        val moved = mutableListOf<PointF>()
        val submitted = mutableListOf<GestureSpec>()
        var cancellationCount = 0
        private var outcomeListener: Function1<Any, Unit>? = null
        private val runtimeClass = Class.forName("dev.opentvtouchpad.setup.GateRuntime")
        private val actionClass = Class.forName("dev.opentvtouchpad.setup.GateAction")
        private val outcomeClass = Class.forName("dev.opentvtouchpad.input.GestureOutcome")
        private val viewModelClass = Class.forName("dev.opentvtouchpad.setup.GateViewModel")
        private val runtime = Proxy.newProxyInstance(
            runtimeClass.classLoader,
            arrayOf(runtimeClass),
        ) { _, method, arguments ->
            when (method.name) {
                "isConnected" -> connected
                "currentBounds" -> bounds
                "showCursorAt" -> shown += arguments!![0] as PointF
                "moveCursorTo" -> moved += arguments!![0] as PointF
                "submitGesture" -> {
                    submitted += arguments!![0] as GestureSpec
                    SubmitResult.ACCEPTED
                }
                "cancelGesture" -> cancellationCount += 1
                "setGestureOutcomeListener" -> {
                    @Suppress("UNCHECKED_CAST")
                    outcomeListener = arguments?.get(0) as Function1<Any, Unit>?
                }
                "toString" -> "FakeGateRuntime"
                else -> error("Unexpected GateRuntime method: ${method.name}")
            }
        }
        private val viewModel = viewModelClass.getConstructor(runtimeClass).newInstance(runtime)

        fun refresh() {
            viewModelClass.getMethod("refresh").invoke(viewModel)
        }

        fun perform(actionName: String, isRepeat: Boolean = false) {
            @Suppress("UNCHECKED_CAST")
            val action = java.lang.Enum.valueOf(actionClass as Class<out Enum<*>>, actionName)
            viewModelClass.getMethod(
                "perform",
                actionClass,
                Boolean::class.javaPrimitiveType,
            ).invoke(viewModel, action, isRepeat)
        }

        fun finish(callbackDurationMillis: Long, cancellationReason: CancelReason?) {
            val outcome = outcomeClass.getConstructor(
                Long::class.javaPrimitiveType,
                CancelReason::class.java,
            ).newInstance(callbackDurationMillis, cancellationReason)
            outcomeListener!!.invoke(outcome)
        }

        fun booleanState(getterName: String): Boolean = stateValue(getterName) as Boolean

        fun stateValue(getterName: String): Any? {
            val state = viewModelClass.getMethod("getState").invoke(viewModel)
            return state.javaClass.getMethod(getterName).invoke(state)
        }
    }
}

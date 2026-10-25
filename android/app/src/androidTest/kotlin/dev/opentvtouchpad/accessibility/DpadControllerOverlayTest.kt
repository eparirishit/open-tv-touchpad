package dev.opentvtouchpad.accessibility

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opentvtouchpad.R
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import dev.opentvtouchpad.setup.GateRuntimeState
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DpadControllerOverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun controllerUsesFocusableNonTouchableAccessibilityWindow() {
        val windowManager = RecordingWindowManager()
        val runtime = RecordingGateRuntime()
        val overlay = newOverlay(windowManager, runtime)

        onMain { invoke(overlay, "show") }

        val added = windowManager.added.single()
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, added.type)
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, added.flags)
        assertFalse(added.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(added.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertFalse(windowManager.view!!.context is Activity)

        onMain { invoke(overlay, "remove") }
    }

    @Test
    fun controllerUsesCompactEndAnchoredWindow() {
        val windowManager = RecordingWindowManager()
        val overlay = newOverlay(windowManager, RecordingGateRuntime())
        val displayBounds = DisplayBounds(width = 1920, height = 1080)

        onMain {
            invoke(overlay, "show")
            windowManager.view!!.measure(
                View.MeasureSpec.makeMeasureSpec(displayBounds.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(displayBounds.height, View.MeasureSpec.AT_MOST),
            )
            windowManager.view!!.layout(
                0,
                0,
                windowManager.view!!.measuredWidth,
                windowManager.view!!.measuredHeight,
            )
        }

        val added = windowManager.added.single()
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, added.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, added.height)
        assertEquals(Gravity.END or Gravity.CENTER_VERTICAL, added.gravity)
        val occupiedWidth = windowManager.view!!.width
        val occupiedHeight = windowManager.view!!.height
        val occupiedArea = occupiedWidth.toLong() * occupiedHeight
        val displayArea = displayBounds.width.toLong() * displayBounds.height
        assertTrue(
            "Overlay width $occupiedWidth must remain under half of ${displayBounds.width}",
            occupiedWidth < displayBounds.width / 2,
        )
        assertTrue(
            "Overlay height $occupiedHeight must remain under three quarters of ${displayBounds.height}",
            occupiedHeight < displayBounds.height * 3 / 4,
        )
        assertTrue(
            "Overlay area $occupiedArea must remain under one third of $displayArea",
            occupiedArea < displayArea / 3,
        )

        onMain { invoke(overlay, "remove") }
    }

    @Test
    fun dpadActionsAreReachableWithoutAnActivityWindow() {
        val windowManager = RecordingWindowManager()
        val runtime = RecordingGateRuntime()
        val overlay = newOverlay(windowManager, runtime)

        onMain { invoke(overlay, "show") }
        val root = windowManager.view as ViewGroup

        onMain {
            root.findViewById<View>(R.id.move_up).requestFocus()
            pressCenter(root)
        }

        assertEquals(listOf(PointF(x = 960f, y = 486f)), runtime.movedPoints)

        onMain {
            root.findViewById<View>(R.id.tap).requestFocus()
            pressCenter(root)
        }
        assertEquals(1, runtime.submittedGestures.size)
        assertTrue(runtime.submittedGestures.single() is GestureSpec.Tap)
        assertEquals(R.id.cancel, root.findFocus()?.id)

        onMain { pressCenter(root) }
        assertEquals(1, runtime.cancelCount)

        onMain { invoke(overlay, "remove") }
    }

    @Test
    fun controllerHasDeterministicDpadFocusOrder() {
        val windowManager = RecordingWindowManager()
        val overlay = newOverlay(windowManager, RecordingGateRuntime())

        onMain { invoke(overlay, "show") }
        val root = windowManager.view as ViewGroup
        val order = listOf(
            requiredId("probe_mode_baseline"),
            requiredId("probe_mode_system_timeout"),
            requiredId("probe_mode_controller_handoff"),
            R.id.show_cursor,
            R.id.move_up,
            R.id.move_down,
            R.id.move_left,
            R.id.move_right,
            R.id.tap,
            R.id.swipe_vertical,
            R.id.swipe_horizontal,
            R.id.drag_path,
            R.id.cancel,
            requiredId("manual_activated"),
            requiredId("manual_not_activated"),
            requiredId("launch_calibration"),
            R.id.accessibility_settings,
        )

        onMain {
            order.forEach { root.findViewById<View>(it).isEnabled = true }
            root.findViewById<View>(order.first()).requestFocus()
            order.drop(1).forEach { expectedId ->
                pressKey(root, KeyEvent.KEYCODE_DPAD_DOWN)
                assertEquals(expectedId, root.findFocus()?.id)
            }
            pressKey(root, KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(order.first(), root.findFocus()?.id)
        }

        onMain { invoke(overlay, "remove") }
    }

    @Test
    fun controllerDpadSelectsModeRecordsManualResultAndLaunchesFixedCalibration() {
        val windowManager = RecordingWindowManager()
        val overlay = newOverlay(windowManager, RecordingGateRuntime())

        onMain { invoke(overlay, "show") }
        val root = windowManager.view as ViewGroup

        onMain {
            root.findViewById<View>(requiredId("probe_mode_controller_handoff")).requestFocus()
            pressCenter(root)
        }
        assertTrue(
            root.findViewById<android.widget.TextView>(requiredId("probe_mode"))
                .text.toString().endsWith("CONTROLLER_HANDOFF"),
        )

        onMain {
            root.findViewById<View>(requiredId("manual_activated")).requestFocus()
            pressCenter(root)
        }
        assertTrue(
            root.findViewById<android.widget.TextView>(requiredId("probe_manual_result"))
                .text.toString().endsWith("ACTIVATED"),
        )

        val monitor = instrumentation.addMonitor(CALIBRATION_ACTIVITY, null, false)
        try {
            onMain {
                root.findViewById<View>(requiredId("launch_calibration")).requestFocus()
                pressCenter(root)
            }
            val launched = instrumentation.waitForMonitorWithTimeout(monitor, 2_000L)
            assertTrue("Calibration activity did not launch", launched != null)
            assertEquals(CALIBRATION_ACTIVITY, launched?.componentName?.className)
            launched?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
            onMain { invoke(overlay, "remove") }
        }
    }

    @Test
    fun controllerRendersBoundedProbeDiagnosticsWithoutCoordinatesOrScreenData() {
        val windowManager = RecordingWindowManager()
        val runtime = RecordingGateRuntime()
        val overlay = newOverlay(windowManager, runtime)

        onMain { invoke(overlay, "show") }
        val root = windowManager.view as ViewGroup
        val diagnosticIds = listOf(
            "probe_mode",
            "probe_admission",
            "probe_callback",
            "probe_callback_duration",
            "probe_display",
            "probe_controller",
            "probe_manual_result",
        ).map(::requiredId)

        diagnosticIds.forEach { id ->
            val value = root.findViewById<android.widget.TextView>(id).text.toString()
            assertFalse(value.contains("package", ignoreCase = true))
            assertFalse(value.contains("screen", ignoreCase = true))
            assertFalse(value.contains("coordinate", ignoreCase = true))
            assertFalse(value.contains("x="))
            assertFalse(value.contains("y="))
        }

        onMain { invoke(overlay, "remove") }
    }

    @Test
    fun serviceShutdownRemovesControllerWindow() {
        val windowManager = RecordingWindowManager()
        val overlay = newOverlay(windowManager, RecordingGateRuntime())
        onMain { invoke(overlay, "show") }
        val service = TouchAccessibilityService()
        val field = TouchAccessibilityService::class.java.getDeclaredField("controllerOverlay")
        field.isAccessible = true
        field.set(service, overlay)

        onMain { service.onDestroy() }

        assertEquals(1, windowManager.removeCount)
        assertNotNull(windowManager.view)
    }

    @Test
    fun matchingKeyUpIsConsumedBeforeRemovalAndOnlyTheFirstFrameSubmits() {
        val fixture = HandoffFixture()
        fixture.show()

        val downConsumed = onMainResult {
            fixture.root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        }

        assertTrue(downConsumed)
        assertTrue(fixture.windows.isControllerAttached())
        assertTrue(fixture.runtime.submittedGestures.isEmpty())

        val upConsumed = onMainResult {
            fixture.root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        }

        assertTrue(upConsumed)
        assertTrue(fixture.windows.isControllerAttached())
        assertTrue(fixture.runtime.submittedGestures.isEmpty())
        assertEquals(1, fixture.schedulers.pendingMainLoopCount)

        fixture.schedulers.deliverMainLoop()

        assertFalse(fixture.windows.isControllerAttached())
        assertTrue(fixture.runtime.submittedGestures.isEmpty())
        assertEquals(1, fixture.schedulers.pendingFrameCount)
        assertEquals(listOf(250L), fixture.schedulers.pendingDeadlineDelays)

        val duplicateConsumed = onMainResult {
            fixture.root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        }
        assertTrue(duplicateConsumed)
        assertEquals(1, fixture.windows.controllerRemoveCount)

        fixture.schedulers.deliverFrame()
        fixture.schedulers.deliverFrame(force = true)

        assertEquals(1, fixture.runtime.submittedGestures.size)
        assertEquals(listOf(false), fixture.runtime.controllerAttachedAtSubmission)
    }

    @Test
    fun interruptCancelsAndFencesDeferredKeyUpHandoff() {
        val fixture = HandoffFixture()
        fixture.show()
        val service = fixture.attachToService()

        onMain {
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER),
            )
        }
        assertEquals(1, fixture.schedulers.pendingMainLoopCount)

        onMain { service.onInterrupt() }

        assertEquals(0, fixture.schedulers.pendingMainLoopCount)
        fixture.schedulers.deliverMainLoop(force = true)
        assertTrue(fixture.windows.activeViews.isEmpty())
        assertEquals(1, fixture.windows.controllerRemoveCount)
        assertEquals(0, fixture.schedulers.pendingFrameCount)
        assertTrue(fixture.runtime.submittedGestures.isEmpty())
    }

    @Test
    fun cursorRemainsVisibleAndInputTransparentDuringFrameWait() {
        val fixture = HandoffFixture()
        fixture.show()
        fixture.releaseTap()

        val cursor = fixture.windows.cursorView
        val params = fixture.windows.paramsFor(cursor)
        assertTrue(fixture.windows.isAttached(cursor))
        assertEquals(View.VISIBLE, cursor.visibility)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertFalse(fixture.windows.isControllerAttached())
    }

    @Test
    fun lateFrameFailsClosedEvenWhenItsCallbackRunsBeforeDeadlineRunnable() {
        val fixture = HandoffFixture()
        fixture.show()
        fixture.releaseTap()

        fixture.schedulers.advanceBy(251L)
        fixture.schedulers.deliverFrame()

        assertTrue(fixture.runtime.submittedGestures.isEmpty())
        assertFalse(fixture.windows.isControllerAttached())
        assertEquals(View.GONE, fixture.windows.cursorView.visibility)
        assertTrue(restartRequired(fixture.overlay))
    }

    @Test
    fun everyDegradedOutcomeFailsClosedAndRequiresRestart() {
        listOf<(HandoffFixture) -> Unit>(
            { fixture ->
                fixture.runtime.submitResult = SubmitResult.REJECTED
                fixture.releaseTap()
                fixture.schedulers.deliverFrame()
            },
            { fixture ->
                fixture.releaseTap()
                fixture.schedulers.deliverFrame()
                fixture.runtime.finish(CancelReason.ANDROID)
            },
            { fixture ->
                fixture.releaseTap()
                fixture.schedulers.deliverFrame()
                fixture.runtime.finish(CancelReason.TIMEOUT)
            },
            { fixture ->
                fixture.releaseTap()
                fixture.schedulers.deliverDeadline(250L)
            },
        ).forEach { degrade ->
            val fixture = HandoffFixture()
            fixture.show()

            degrade(fixture)

            assertFalse(fixture.windows.isControllerAttached())
            assertEquals(View.GONE, fixture.windows.cursorView.visibility)
            assertEquals(1, fixture.runtime.hideCursorCount)
            assertTrue(restartRequired(fixture.overlay))
        }
    }

    @Test
    fun everyNonHandoffFailureRemovesControllerAndRequiresRestart() {
        listOf<Triple<String, String, (HandoffFixture) -> Unit>>(
            Triple("rejected", "BASELINE_80_MS") { fixture ->
                fixture.runtime.submitResult = SubmitResult.REJECTED
                fixture.releaseTap()
            },
            Triple("cancelled", "SYSTEM_TAP_TIMEOUT") { fixture ->
                fixture.releaseTap()
                fixture.runtime.finish(CancelReason.ANDROID)
            },
            Triple("timeout", "BASELINE_80_MS") { fixture ->
                fixture.releaseTap()
                fixture.runtime.finish(CancelReason.TIMEOUT)
            },
        ).forEach { (label, mode, degrade) ->
            val fixture = HandoffFixture()
            fixture.show(mode)

            degrade(fixture)

            assertFalse(label, fixture.windows.isControllerAttached())
            assertEquals(View.GONE, fixture.windows.cursorView.visibility)
            assertTrue(restartRequired(fixture.overlay))
        }
    }

    @Test
    fun degradedProbeLeavesBoundedNonBlockingDiagnosticsVisible() {
        val fixture = HandoffFixture()
        fixture.runtime.submitResult = SubmitResult.REJECTED
        fixture.show("SYSTEM_TAP_TIMEOUT")

        fixture.releaseTap()

        val diagnostics = fixture.windows.diagnosticView
        val params = fixture.windows.paramsFor(diagnostics)
        assertTrue(fixture.windows.isAttached(diagnostics))
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(diagnostics.contentDescription.toString().contains("SYSTEM_TAP_TIMEOUT"))
        assertTrue(diagnostics.contentDescription.toString().contains("REJECTED"))
        assertTrue(diagnostics.contentDescription.toString().contains("REMOVED"))
        val diagnosticText = diagnostics.contentDescription.toString()
        assertFalse(diagnosticText, diagnosticText.contains("x="))
        assertFalse(diagnosticText, diagnosticText.contains("y="))
    }

    @Test
    fun rejectedDirectProbeConsumesMatchingKeyUpBeforeRemovingController() {
        val fixture = HandoffFixture()
        fixture.runtime.submitResult = SubmitResult.REJECTED
        fixture.show("BASELINE_80_MS")

        fixture.pressTapDown()

        assertTrue(fixture.windows.isControllerAttached())

        fixture.releaseTapUp(deliverMainLoop = false)

        assertTrue(fixture.windows.isControllerAttached())

        fixture.schedulers.deliverMainLoop()

        assertFalse(fixture.windows.isControllerAttached())
        assertTrue(restartRequired(fixture.overlay))
    }

    @Test
    fun secondConsumedConfirmStaysOwnedUntilReleaseBeforeHandoff() {
        val fixture = HandoffFixture()
        fixture.show()

        onMain {
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER),
            )
        }
        fixture.schedulers.deliverMainLoop()

        assertTrue(fixture.windows.isControllerAttached())
        assertTrue(fixture.runtime.submittedGestures.isEmpty())

        onMain {
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
            )
        }
        fixture.schedulers.deliverMainLoop()
        fixture.schedulers.deliverFrame()

        assertEquals(1, fixture.runtime.submittedGestures.size)
        assertTrue(fixture.runtime.controllerAttachedAtSubmission.single().not())
    }

    @Test
    fun queuedDoublePressDoesNotReplaceDeferredHandoffRequest() {
        val fixture = HandoffFixture()
        fixture.show()

        onMain {
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
            )
        }
        fixture.schedulers.deliverMainLoop()
        fixture.schedulers.deliverFrame()

        assertEquals(1, fixture.runtime.submittedGestures.size)
    }

    @Test
    fun queuedDoublePressDefersDirectFailureUntilAllConsumedKeysRelease() {
        val fixture = HandoffFixture()
        fixture.runtime.submitResult = SubmitResult.REJECTED
        fixture.show("BASELINE_80_MS")

        onMain {
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER),
            )
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
            )
        }
        fixture.schedulers.deliverMainLoop()

        assertEquals(1, fixture.runtime.submittedGestures.size)
        assertTrue(fixture.windows.isControllerAttached())

        onMain {
            fixture.root.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
            )
        }
        fixture.schedulers.deliverMainLoop()

        assertFalse(fixture.windows.isControllerAttached())
        assertTrue(restartRequired(fixture.overlay))
    }

    @Test
    fun completionRestoresControllerExactlyOnceAfterOneHundredMilliseconds() {
        val fixture = HandoffFixture()
        fixture.show()
        fixture.releaseTap()
        fixture.schedulers.deliverFrame()

        fixture.runtime.finish()

        assertFalse(fixture.windows.isControllerAttached())
        assertEquals(listOf(100L), fixture.schedulers.pendingDeadlineDelays)

        fixture.schedulers.deliverDeadline(100L)
        fixture.schedulers.deliverDeadline(100L, force = true)

        assertTrue(fixture.windows.isControllerAttached())
        assertEquals(2, fixture.windows.controllerAddCount)
    }

    @Test
    fun interruptBeforeFrameRemovesBothOverlaysAndFencesQueuedFrame() {
        val fixture = HandoffFixture()
        fixture.show()
        fixture.releaseTap()
        val service = fixture.attachToService()

        onMain { service.onInterrupt() }
        fixture.schedulers.deliverFrame(force = true)

        assertTrue(fixture.windows.activeViews.isEmpty())
        assertTrue(fixture.runtime.submittedGestures.isEmpty())
    }

    @Test
    fun unbindBeforeGestureCallbackRemovesBothOverlaysAndFencesQueuedOutcome() {
        val fixture = HandoffFixture()
        fixture.show()
        fixture.releaseTap()
        fixture.schedulers.deliverFrame()
        val queuedOutcome = fixture.runtime.outcomeCallback
        val service = fixture.attachToService()

        onMain { service.onUnbind(null) }
        onMain { queuedOutcome?.invoke(GestureOutcome(93L, null)) }

        assertTrue(fixture.windows.activeViews.isEmpty())
        assertEquals(1, fixture.windows.controllerAddCount)
        assertTrue(fixture.schedulers.pendingDeadlineDelays.isEmpty())
    }

    @Test
    fun destroyBeforeRestorationRemovesBothOverlaysAndFencesQueuedRestore() {
        val fixture = HandoffFixture()
        fixture.show()
        fixture.releaseTap()
        fixture.schedulers.deliverFrame()
        fixture.runtime.finish()
        val service = fixture.attachToService()

        onMain { service.onDestroy() }
        fixture.schedulers.deliverDeadline(100L, force = true)

        assertTrue(fixture.windows.activeViews.isEmpty())
        assertEquals(1, fixture.windows.controllerAddCount)
    }

    private fun newOverlay(
        windowManager: RecordingWindowManager,
        runtime: RecordingGateRuntime,
    ): Any {
        val overlayClass = Class.forName(
            "dev.opentvtouchpad.accessibility.DpadControllerOverlay",
        )
        val runtimeClass = Class.forName("dev.opentvtouchpad.setup.GateRuntime")
        lateinit var overlay: Any
        onMain {
            overlay = overlayClass.getConstructor(
                Context::class.java,
                WindowManager::class.java,
                runtimeClass,
            ).newInstance(
                instrumentation.targetContext,
                windowManager.instance,
                runtime.instance,
            )
        }
        return overlay
    }

    private fun newOverlay(
        windowManager: WindowManager,
        runtime: RecordingGateRuntime,
        schedulers: ManualHandoffSchedulers,
    ): Any {
        val overlayClass = Class.forName(
            "dev.opentvtouchpad.accessibility.DpadControllerOverlay",
        )
        val runtimeClass = Class.forName("dev.opentvtouchpad.setup.GateRuntime")
        val frameSchedulerClass = Class.forName(
            "dev.opentvtouchpad.accessibility.ControllerHandoffFrameScheduler",
        )
        val deadlineSchedulerClass = Class.forName(
            "dev.opentvtouchpad.accessibility.ControllerHandoffDeadlineScheduler",
        )
        val mainLoopSchedulerClass = Class.forName(
            "dev.opentvtouchpad.accessibility.ControllerHandoffMainLoopScheduler",
        )
        lateinit var overlay: Any
        onMain {
            overlay = overlayClass.getConstructor(
                Context::class.java,
                WindowManager::class.java,
                runtimeClass,
                frameSchedulerClass,
                deadlineSchedulerClass,
                mainLoopSchedulerClass,
            ).newInstance(
                instrumentation.targetContext,
                windowManager,
                runtime.instance,
                schedulers.frameScheduler(frameSchedulerClass),
                schedulers.deadlineScheduler(deadlineSchedulerClass),
                schedulers.mainLoopScheduler(mainLoopSchedulerClass),
            )
        }
        return overlay
    }

    private fun invoke(target: Any, methodName: String) {
        target.javaClass.getMethod(methodName).invoke(target)
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }

    private fun <T> onMainResult(block: () -> T): T {
        var result: T? = null
        onMain { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun pressCenter(root: View) {
        pressKey(root, KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun pressKey(root: View, keyCode: Int) {
        root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun requiredId(name: String): Int {
        val context = instrumentation.targetContext
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        assertTrue("Missing view id: $name", id != 0)
        return id
    }

    private fun selectMode(overlay: Any, modeName: String) {
        val viewModel = viewModel(overlay)
        val modeClass = Class.forName("dev.opentvtouchpad.setup.TapProbeMode")
        val mode = modeClass.enumConstants.single { (it as Enum<*>).name == modeName }
        viewModel.javaClass.getMethod("selectTapProbeMode", modeClass).invoke(viewModel, mode)
    }

    private fun restartRequired(overlay: Any): Boolean {
        val stateField = viewModel(overlay).javaClass.getDeclaredField("state").apply {
            isAccessible = true
        }
        val state = stateField.get(viewModel(overlay))
        return state.javaClass.getDeclaredField("restartRequired").apply {
            isAccessible = true
        }.getBoolean(state)
    }

    private fun viewModel(overlay: Any): Any {
        val controllerView = overlay.javaClass.getDeclaredField("controllerView").apply {
            isAccessible = true
        }.get(overlay)
        return controllerView.javaClass.getDeclaredField("viewModel").apply {
            isAccessible = true
        }.get(controllerView)
    }

    private inner class HandoffFixture {
        val windows = HandoffWindowManager()
        val schedulers = ManualHandoffSchedulers()
        val runtime = RecordingGateRuntime()
        val cursor = CursorOverlay(
            instrumentation.targetContext,
            windows.instance,
        ) { DisplayBounds(width = 1920, height = 1080) }
        val overlay: Any
        lateinit var root: ViewGroup

        init {
            runtime.onHideCursor = cursor::hide
            runtime.controllerAttached = windows::isControllerAttached
            overlay = newOverlay(windows.instance, runtime, schedulers)
        }

        fun show(modeName: String = "CONTROLLER_HANDOFF") {
            onMain {
                cursor.showAt(960, 540)
                invoke(overlay, "show")
                selectMode(overlay, modeName)
                root = windows.controllerView as ViewGroup
                root.findViewById<View>(R.id.tap).requestFocus()
            }
        }

        fun releaseTap() {
            onMain {
                root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            }
            schedulers.deliverMainLoop()
        }

        fun pressTapDown() {
            onMain {
                root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            }
        }

        fun releaseTapUp(deliverMainLoop: Boolean = true) {
            onMain {
                root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            }
            if (deliverMainLoop) schedulers.deliverMainLoop()
        }

        fun attachToService(): TouchAccessibilityService = TouchAccessibilityService().also { service ->
            TouchAccessibilityService::class.java.getDeclaredField("controllerOverlay").apply {
                isAccessible = true
                set(service, overlay)
            }
            TouchAccessibilityService::class.java.getDeclaredField("cursorOverlay").apply {
                isAccessible = true
                set(service, cursor)
            }
        }
    }

    private companion object {
        const val CALIBRATION_ACTIVITY = "dev.opentvtouchpad.setup.CalibrationActivity"
    }
}

private class RecordingGateRuntime : InvocationHandler {
    val movedPoints = mutableListOf<PointF>()
    val submittedGestures = mutableListOf<GestureSpec>()
    var cancelCount = 0
    var hideCursorCount = 0
    var submitResult = SubmitResult.ACCEPTED
    var onHideCursor: () -> Unit = {}
    var controllerAttached: () -> Boolean = { true }
    val controllerAttachedAtSubmission = mutableListOf<Boolean>()
    var outcomeCallback: Function1<GestureOutcome, Unit>? = null

    private val runtimeClass = Class.forName("dev.opentvtouchpad.setup.GateRuntime")
    val instance: Any = Proxy.newProxyInstance(
        runtimeClass.classLoader,
        arrayOf(runtimeClass),
        this,
    )

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any>?): Any? =
        when (method.name) {
            "isConnected" -> true
            "currentBounds" -> DisplayBounds(width = 1920, height = 1080)
            "showCursorAt" -> Unit
            "moveCursorTo" -> {
                movedPoints += arguments!![0] as PointF
                Unit
            }
            "submitGesture" -> {
                submittedGestures += arguments!![0] as GestureSpec
                controllerAttachedAtSubmission += controllerAttached()
                submitResult
            }
            "cancelGesture" -> {
                cancelCount += 1
                Unit
            }
            "hideCursor" -> {
                hideCursorCount += 1
                onHideCursor()
                Unit
            }
            "setGestureOutcomeListener" -> {
                @Suppress("UNCHECKED_CAST")
                outcomeCallback = arguments?.firstOrNull() as? Function1<GestureOutcome, Unit>
                Unit
            }
            "currentGestureState" -> GateRuntimeState(
                gestureInFlight = false,
                latestOutcome = null,
            )
            "toString" -> "RecordingGateRuntime"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> proxy === arguments?.firstOrNull()
            else -> defaultValue(method.returnType)
        }

    fun finish(reason: CancelReason? = null) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            outcomeCallback?.invoke(GestureOutcome(callbackDurationMillis = 93L, cancellationReason = reason))
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}

private class ManualHandoffSchedulers {
    private data class Pending(
        val delayMillis: Long?,
        val callback: Function0<Unit>,
        var cancelled: Boolean = false,
        var delivered: Boolean = false,
    )

    private val frames = mutableListOf<Pending>()
    private val deadlines = mutableListOf<Pending>()
    private val mainLoopCallbacks = mutableListOf<Pending>()
    var currentTimeMillis = 0L
        private set

    val pendingFrameCount: Int
        get() = frames.count { !it.cancelled && !it.delivered }

    val pendingDeadlineDelays: List<Long>
        get() = deadlines.filter { !it.cancelled && !it.delivered }.map { checkNotNull(it.delayMillis) }

    val pendingMainLoopCount: Int
        get() = mainLoopCallbacks.count { !it.cancelled && !it.delivered }

    fun frameScheduler(schedulerClass: Class<*>): Any = Proxy.newProxyInstance(
        schedulerClass.classLoader,
        arrayOf(schedulerClass),
    ) { _, method, arguments ->
        if (method.name != "postFrame") return@newProxyInstance defaultValue(method.returnType)
        @Suppress("UNCHECKED_CAST")
        val pending = Pending(null, arguments!![0] as Function0<Unit>)
        frames += pending
        { pending.cancelled = true }
    }

    fun deadlineScheduler(schedulerClass: Class<*>): Any = Proxy.newProxyInstance(
        schedulerClass.classLoader,
        arrayOf(schedulerClass),
    ) { _, method, arguments ->
        if (method.name == "nowMillis") return@newProxyInstance currentTimeMillis
        if (method.name != "schedule") return@newProxyInstance defaultValue(method.returnType)
        @Suppress("UNCHECKED_CAST")
        val pending = Pending(arguments!![0] as Long, arguments[1] as Function0<Unit>)
        deadlines += pending
        { pending.cancelled = true }
    }

    fun mainLoopScheduler(schedulerClass: Class<*>): Any = Proxy.newProxyInstance(
        schedulerClass.classLoader,
        arrayOf(schedulerClass),
    ) { _, method, arguments ->
        if (method.name != "post") return@newProxyInstance defaultValue(method.returnType)
        @Suppress("UNCHECKED_CAST")
        val pending = Pending(null, arguments!![0] as Function0<Unit>)
        mainLoopCallbacks += pending
        { pending.cancelled = true }
    }

    fun deliverFrame(force: Boolean = false) {
        deliver(frames, null, force)
    }

    fun deliverDeadline(delayMillis: Long, force: Boolean = false) {
        deliver(deadlines, delayMillis, force)
    }

    fun deliverMainLoop(force: Boolean = false) {
        deliver(mainLoopCallbacks, null, force)
    }

    fun advanceBy(durationMillis: Long) {
        currentTimeMillis += durationMillis
    }

    private fun deliver(pending: List<Pending>, delayMillis: Long?, force: Boolean) {
        val next = pending.firstOrNull {
            it.delayMillis == delayMillis && !it.delivered && (force || !it.cancelled)
        } ?: pending.firstOrNull {
            force && it.delayMillis == delayMillis
        } ?: return
        next.delivered = true
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            next.callback.invoke()
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        else -> null
    }
}

private class HandoffWindowManager : InvocationHandler {
    private val params = linkedMapOf<View, CapturedLayoutParams>()
    val activeViews = linkedSetOf<View>()
    private val addCounts = mutableMapOf<View, Int>()
    private val removeCounts = mutableMapOf<View, Int>()

    val instance: WindowManager = Proxy.newProxyInstance(
        WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java),
        this,
    ) as WindowManager

    val controllerView: View
        get() = params.keys.single { it.findViewById<View>(R.id.tap) != null }

    val cursorView: View
        get() = params.keys.single {
            it.findViewById<View>(R.id.tap) == null && it.tag != DIAGNOSTIC_TAG
        }

    val diagnosticView: View
        get() = params.keys.single { it.tag == DIAGNOSTIC_TAG }

    val controllerAddCount: Int
        get() = addCounts[controllerView] ?: 0

    val controllerRemoveCount: Int
        get() = removeCounts[controllerView] ?: 0

    fun isControllerAttached(): Boolean = controllerView in activeViews

    fun isAttached(view: View): Boolean = view in activeViews

    fun paramsFor(view: View): CapturedLayoutParams = checkNotNull(params[view])

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any>?): Any? {
        when (method.name) {
            "addView" -> {
                val view = arguments!![0] as View
                params[view] = capture(arguments[1] as WindowManager.LayoutParams)
                activeViews += view
                addCounts[view] = (addCounts[view] ?: 0) + 1
            }
            "updateViewLayout" -> {
                val view = arguments!![0] as View
                params[view] = capture(arguments[1] as WindowManager.LayoutParams)
            }
            "removeViewImmediate", "removeView" -> {
                val view = arguments!![0] as View
                activeViews -= view
                removeCounts[view] = (removeCounts[view] ?: 0) + 1
            }
        }
        return defaultValue(method.returnType)
    }

    private fun capture(params: WindowManager.LayoutParams) = CapturedLayoutParams(
        type = params.type,
        flags = params.flags,
        width = params.width,
        height = params.height,
        gravity = params.gravity,
        x = params.x,
        y = params.y,
    )

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }

    private companion object {
        const val DIAGNOSTIC_TAG = "probe_failure_diagnostics"
    }
}

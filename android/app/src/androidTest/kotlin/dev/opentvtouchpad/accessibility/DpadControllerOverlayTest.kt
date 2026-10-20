package dev.opentvtouchpad.accessibility

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opentvtouchpad.R
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import dev.opentvtouchpad.setup.GateRuntimeState
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
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

    private fun invoke(target: Any, methodName: String) {
        target.javaClass.getMethod(methodName).invoke(target)
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }

    private fun pressCenter(root: View) {
        pressKey(root, KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun pressKey(root: View, keyCode: Int) {
        root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}

private class RecordingGateRuntime : InvocationHandler {
    val movedPoints = mutableListOf<PointF>()
    val submittedGestures = mutableListOf<GestureSpec>()
    var cancelCount = 0

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
                SubmitResult.ACCEPTED
            }
            "cancelGesture" -> {
                cancelCount += 1
                Unit
            }
            "setGestureOutcomeListener" -> Unit
            "currentGestureState" -> GateRuntimeState(
                gestureInFlight = false,
                latestOutcome = null,
            )
            "toString" -> "RecordingGateRuntime"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> proxy === arguments?.firstOrNull()
            else -> defaultValue(method.returnType)
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

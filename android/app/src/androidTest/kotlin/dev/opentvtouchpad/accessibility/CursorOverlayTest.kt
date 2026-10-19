package dev.opentvtouchpad.accessibility

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opentvtouchpad.input.DisplayBounds
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.jvm.functions.Function0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CursorOverlayTest {
    @Test
    fun overlayUsesAccessibilityWindowWithoutInputCapabilities() {
        val windowManager = RecordingWindowManager()
        val overlay = newOverlay(windowManager, DisplayBounds(width = 1920, height = 1080))

        invoke(overlay, "showAt", -100, 2_000)

        val added = windowManager.added.single()
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, added.type)
        assertTrue(added.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(added.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertEquals(0, added.x)
        assertEquals(1080 - added.height, added.y)
        assertTrue(added.width > 0)
        assertTrue(added.height > 0)
        assertEquals(View.VISIBLE, windowManager.view!!.visibility)
    }

    @Test
    fun moveHideAndRemoveStayBoundedAndReleaseTheWindow() {
        val windowManager = RecordingWindowManager()
        val overlay = newOverlay(windowManager, DisplayBounds(width = 1280, height = 720))
        invoke(overlay, "showAt", 640, 360)

        invoke(overlay, "moveTo", 2_000, -50)

        val updated = windowManager.updated.single()
        assertEquals(1280 - updated.width, updated.x)
        assertEquals(0, updated.y)

        invoke(overlay, "hide")
        assertEquals(View.GONE, windowManager.view!!.visibility)

        invoke(overlay, "remove")
        invoke(overlay, "remove")
        assertEquals(1, windowManager.removeCount)
    }

    private fun newOverlay(
        recordingWindowManager: RecordingWindowManager,
        bounds: DisplayBounds,
    ): Any {
        val overlayClass = Class.forName(
            "dev.opentvtouchpad.accessibility.CursorOverlay",
        )
        return overlayClass.getConstructor(
            Context::class.java,
            WindowManager::class.java,
            Function0::class.java,
        ).newInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
            recordingWindowManager.instance,
            { bounds },
        )
    }

    private fun invoke(target: Any, methodName: String, vararg arguments: Any) {
        val parameterTypes = arguments.map { Int::class.javaPrimitiveType }.toTypedArray()
        target.javaClass.getMethod(methodName, *parameterTypes).invoke(target, *arguments)
    }

    private data class CapturedLayoutParams(
        val type: Int,
        val flags: Int,
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
    )

    private class RecordingWindowManager : InvocationHandler {
        val added = mutableListOf<CapturedLayoutParams>()
        val updated = mutableListOf<CapturedLayoutParams>()
        var view: View? = null
        var removeCount = 0

        val instance: WindowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java),
            this,
        ) as WindowManager

        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any>?): Any? {
            when (method.name) {
                "addView" -> {
                    view = arguments!![0] as View
                    added += capture(arguments[1] as WindowManager.LayoutParams)
                }
                "updateViewLayout" -> {
                    updated += capture(arguments!![1] as WindowManager.LayoutParams)
                }
                "removeViewImmediate" -> removeCount += 1
                "removeView" -> removeCount += 1
            }
            return defaultValue(method.returnType)
        }

        private fun capture(params: WindowManager.LayoutParams) = CapturedLayoutParams(
            type = params.type,
            flags = params.flags,
            width = params.width,
            height = params.height,
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
    }
}

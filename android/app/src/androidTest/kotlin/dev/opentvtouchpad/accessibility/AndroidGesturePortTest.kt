package dev.opentvtouchpad.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Handler
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureCallback
import dev.opentvtouchpad.input.GestureCoordinator
import dev.opentvtouchpad.input.GesturePort
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidGesturePortTest {
    @Test
    fun translatesDomainGesturesOnlyAtTheAndroidBoundary() {
        val dispatcher = RecordingDispatcher()
        val port = newPort(dispatcher, DisplayBounds(width = 1920, height = 1080))
        val callback = RecordingCallback()

        assertTrue(
            port.dispatch(
                GestureSpec.Tap(PointF(100f, 200f), durationMillis = 80),
                callback,
            ),
        )
        assertTrue(
            port.dispatch(
                GestureSpec.Swipe(
                    start = PointF(300f, 400f),
                    end = PointF(500f, 600f),
                    durationMillis = 250,
                ),
                callback,
            ),
        )
        assertTrue(
            port.dispatch(
                GestureSpec.Drag(
                    points = listOf(
                        PointF(700f, 800f),
                        PointF(750f, 850f),
                        PointF(800f, 900f),
                    ),
                    durationMillis = 500,
                ),
                callback,
            ),
        )

        assertStroke(dispatcher.descriptions[0], 80, 100f, 200f, 100f, 200f)
        assertStroke(dispatcher.descriptions[1], 250, 300f, 400f, 500f, 600f)
        assertStroke(dispatcher.descriptions[2], 500, 700f, 800f, 800f, 900f)
    }

    @Test
    fun forwardsAndroidCompletionAndCancellationCallbacks() {
        val dispatcher = RecordingDispatcher()
        val port = newPort(dispatcher, DisplayBounds(width = 1920, height = 1080))
        val callback = RecordingCallback()
        val spec = GestureSpec.Tap(PointF(100f, 200f), durationMillis = 80)

        port.dispatch(spec, callback)
        dispatcher.callbacks.single()!!.onCompleted(dispatcher.descriptions.single())
        dispatcher.callbacks.single()!!.onCancelled(dispatcher.descriptions.single())

        assertEquals(1, callback.completionCount)
        assertEquals(1, callback.cancellationCount)
    }

    @Test
    fun cancellationSynchronouslyDispatchesOffDisplayReplacementGesture() {
        val dispatcher = RecordingDispatcher()
        val port = newPort(dispatcher, DisplayBounds(width = 1920, height = 1080))

        port.cancelActiveGesture()

        assertEquals(1, dispatcher.descriptions.size)
        assertStroke(
            dispatcher.descriptions.single(),
            durationMillis = 1,
            startX = 1920f,
            startY = 1080f,
            endX = 1921f,
            endY = 1080f,
        )
    }

    @Test
    fun clampsGesturePointsToCurrentBoundsBeforeAndroidConversion() {
        val dispatcher = RecordingDispatcher()
        val port = newPort(dispatcher, DisplayBounds(width = 100, height = 50))

        port.dispatch(
            GestureSpec.Swipe(
                start = PointF(-10f, -20f),
                end = PointF(120f, 80f),
                durationMillis = 250,
            ),
            RecordingCallback(),
        )

        assertStroke(dispatcher.descriptions.single(), 250, 0f, 0f, 99f, 49f)
    }

    @Test
    fun serviceChecksTimeoutAfterGestureDurationAndCallbackGrace() {
        val method = Class.forName(
            "dev.opentvtouchpad.accessibility.TouchAccessibilityService",
        ).getDeclaredMethod(
            "timeoutCheckDelayMillis",
            GestureSpec::class.java,
            Long::class.javaPrimitiveType,
        )

        assertEquals(
            2_250L,
            method.invoke(
                null,
                GestureSpec.Swipe(
                    start = PointF(0f, 0f),
                    end = PointF(10f, 10f),
                    durationMillis = 250,
                ),
                2_000L,
            ),
        )
    }

    @Test
    fun explicitCancellationKeepsTimeoutWatchdogUntilAndroidCallback() {
        val service = TouchAccessibilityService()
        val port = RecordingGesturePort()
        val coordinator = GestureCoordinator(
            port = port,
            clock = { 0L },
            callbackTimeoutMillis = 2_000,
        )
        service.javaClass.getDeclaredField("gestureCoordinator").apply {
            isAccessible = true
            set(service, coordinator)
        }
        val timeoutCheck = service.javaClass.getDeclaredField("timeoutCheck").run {
            isAccessible = true
            get(service) as Runnable
        }
        val mainHandler = service.javaClass.getDeclaredField("mainHandler").run {
            isAccessible = true
            get(service) as Handler
        }
        val spec = GestureSpec.Tap(PointF(10f, 10f), durationMillis = 80)

        try {
            assertEquals(SubmitResult.ACCEPTED, service.submitGesture(spec))
            assertTrue(mainHandler.hasCallbacks(timeoutCheck))

            service.cancelGesture()

            assertEquals(1, port.cancellationCount)
            assertTrue(mainHandler.hasCallbacks(timeoutCheck))
        } finally {
            mainHandler.removeCallbacks(timeoutCheck)
        }
    }

    @Test
    fun interruptCancelsGestureWithoutDisconnectingServiceRuntime() {
        val service = TouchAccessibilityService()
        val port = RecordingGesturePort()
        val coordinator = GestureCoordinator(
            port = port,
            clock = { 0L },
            callbackTimeoutMillis = 2_000,
        )
        service.javaClass.getDeclaredField("gestureCoordinator").apply {
            isAccessible = true
            set(service, coordinator)
        }
        val timeoutCheck = service.javaClass.getDeclaredField("timeoutCheck").run {
            isAccessible = true
            get(service) as Runnable
        }
        val mainHandler = service.javaClass.getDeclaredField("mainHandler").run {
            isAccessible = true
            get(service) as Handler
        }
        val firstTap = GestureSpec.Tap(PointF(10f, 10f), durationMillis = 80)
        val secondTap = GestureSpec.Tap(PointF(20f, 20f), durationMillis = 80)

        try {
            assertEquals(SubmitResult.ACCEPTED, service.submitGesture(firstTap))

            service.onInterrupt()

            assertEquals(1, port.cancellationCount)
            port.callbacks.single().onCancelled()
            assertEquals(SubmitResult.ACCEPTED, service.submitGesture(secondTap))
        } finally {
            mainHandler.removeCallbacks(timeoutCheck)
        }
    }

    private fun newPort(
        dispatcher: RecordingDispatcher,
        bounds: DisplayBounds,
    ): GesturePort {
        val portClass = Class.forName(
            "dev.opentvtouchpad.accessibility.AndroidGesturePort",
        )
        return portClass.getConstructor(
            Function2::class.java,
            Function0::class.java,
        ).newInstance(
            { gesture: GestureDescription,
                callback: AccessibilityService.GestureResultCallback? ->
                dispatcher.dispatch(gesture, callback)
            },
            { bounds },
        ) as GesturePort
    }

    private fun assertStroke(
        description: GestureDescription,
        durationMillis: Long,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ) {
        assertEquals(1, description.strokeCount)
        val stroke = description.getStroke(0)
        assertEquals(durationMillis, stroke.duration)
        if (startX == endX && startY == endY) {
            val bounds = RectF()
            stroke.path.computeBounds(bounds, true)
            assertEquals(startX, bounds.left, 0.01f)
            assertEquals(startY, bounds.top, 0.01f)
            assertEquals(endX, bounds.right, 0.01f)
            assertEquals(endY, bounds.bottom, 0.01f)
            return
        }
        val pathMeasure = PathMeasure(stroke.path, false)
        val coordinates = FloatArray(2)
        assertTrue(pathMeasure.getPosTan(0f, coordinates, null))
        assertEquals(startX, coordinates[0], 0.01f)
        assertEquals(startY, coordinates[1], 0.01f)
        assertTrue(pathMeasure.getPosTan(pathMeasure.length, coordinates, null))
        assertEquals(endX, coordinates[0], 0.01f)
        assertEquals(endY, coordinates[1], 0.01f)
    }

    private class RecordingDispatcher {
        val descriptions = mutableListOf<GestureDescription>()
        val callbacks =
            mutableListOf<AccessibilityService.GestureResultCallback?>()

        fun dispatch(
            description: GestureDescription,
            callback: AccessibilityService.GestureResultCallback?,
        ): Boolean {
            descriptions += description
            callbacks += callback
            return true
        }
    }

    private class RecordingCallback : GestureCallback {
        var completionCount = 0
        var cancellationCount = 0

        override fun onCompleted() {
            completionCount += 1
        }

        override fun onCancelled() {
            cancellationCount += 1
        }
    }

    private class RecordingGesturePort : GesturePort {
        var cancellationCount = 0
        val callbacks = mutableListOf<GestureCallback>()

        override fun dispatch(spec: GestureSpec, callback: GestureCallback): Boolean {
            callbacks += callback
            return true
        }

        override fun cancelActiveGesture() {
            cancellationCount += 1
        }
    }
}

package dev.opentvtouchpad.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Handler
import android.os.Message
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureCallback
import dev.opentvtouchpad.input.GestureCoordinator
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GesturePort
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun unbindCancelsInFlightDragAndTearsDownRuntime() {
        assertDragLifecycleTeardown { service -> service.onUnbind(null) }
    }

    @Test
    fun destroyCancelsInFlightDragAndTearsDownRuntime() {
        assertDragLifecycleTeardown(TouchAccessibilityService::onDestroy)
    }

    @Test
    fun unbindSerializesBehindCapturedSubmissionAndCancelsAcceptedDrag() {
        val fixture = LifecycleRaceFixture()

        try {
            fixture.runRace { service -> service.onUnbind(null) }

            assertEquals(1, fixture.port.cancellationCount.get())
            assertFalse(fixture.mainHandler.hasCallbacks(fixture.timeoutCheck))
            assertEquals(View.GONE, fixture.windowManager.view!!.visibility)
            assertEquals(1, fixture.windowManager.removeCount)
            assertNull(fixture.service.privateField("gestureCoordinator"))
            assertNull(fixture.service.privateField("cursorOverlay"))
            assertNull(TouchAccessibilityService.connectedService)
            assertEquals(SubmitResult.REJECTED, fixture.service.submitGesture(fixture.successor))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun interruptSerializesBehindCapturedSubmissionAndCancelsAcceptedDrag() {
        val fixture = LifecycleRaceFixture()

        try {
            fixture.runRace(TouchAccessibilityService::onInterrupt)

            assertEquals(1, fixture.port.cancellationCount.get())
            assertTrue(fixture.mainHandler.hasCallbacks(fixture.timeoutCheck))
            assertEquals(View.GONE, fixture.windowManager.view!!.visibility)
            assertEquals(0, fixture.windowManager.removeCount)
            assertSame(fixture.coordinator, fixture.service.privateField("gestureCoordinator"))
            assertSame(fixture.overlay, fixture.service.privateField("cursorOverlay"))
            assertSame(fixture.service, TouchAccessibilityService.connectedService)
            fixture.port.callbacks.single().onCancelled()
            assertEquals(SubmitResult.ACCEPTED, fixture.service.submitGesture(fixture.successor))
            fixture.port.callbacks.last().onCancelled()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun serviceSkipsDetachedListenerAndRetainsOutcomeForRefresh() {
        val service = TouchAccessibilityService()
        val handler = QueuedHandler(
            (service.privateField("mainHandler") as Handler).looper,
        )
        service.setPrivateField("mainHandler", handler)
        val detachedOutcomes = mutableListOf<GestureOutcome>()
        val attachedOutcomes = mutableListOf<GestureOutcome>()
        val replayListenerOutcomes = mutableListOf<GestureOutcome>()
        val detachedListener: (GestureOutcome) -> Unit = { detachedOutcomes += it }
        val attachedListener: (GestureOutcome) -> Unit = { attachedOutcomes += it }
        val replayListener: (GestureOutcome) -> Unit = { replayListenerOutcomes += it }
        val outcome = GestureOutcome(
            callbackDurationMillis = 527,
            cancellationReason = CancelReason.ANDROID,
        )
        service.addGestureOutcomeListener(detachedListener)
        service.addGestureOutcomeListener(attachedListener)

        service.publishGestureOutcome(outcome)
        service.removeGestureOutcomeListener(detachedListener)
        handler.runQueued()

        assertTrue(detachedOutcomes.isEmpty())
        assertEquals(listOf(outcome), attachedOutcomes)

        service.removeGestureOutcomeListener(attachedListener)
        service.addGestureOutcomeListener(replayListener)
        assertFalse(service.isGestureInFlight())
        assertEquals(outcome, service.latestGestureOutcome())
        assertTrue(detachedOutcomes.isEmpty())
        assertTrue(replayListenerOutcomes.isEmpty())
    }

    private fun assertDragLifecycleTeardown(
        lifecycleEvent: (TouchAccessibilityService) -> Unit,
    ) {
        val service = TouchAccessibilityService()
        val port = RecordingGesturePort()
        val coordinator = GestureCoordinator(
            port = port,
            clock = { 0L },
            callbackTimeoutMillis = 2_000,
        )
        val windowManager = RecordingWindowManager()
        val overlay = CursorOverlay(
            InstrumentationRegistry.getInstrumentation().targetContext,
            windowManager.instance,
        ) { DisplayBounds(width = 1920, height = 1080) }
        val timeoutCheck = service.javaClass.getDeclaredField("timeoutCheck").run {
            isAccessible = true
            get(service) as Runnable
        }
        val mainHandler = service.javaClass.getDeclaredField("mainHandler").run {
            isAccessible = true
            get(service) as Handler
        }
        val drag = GestureSpec.Drag(
            points = listOf(PointF(10f, 10f), PointF(100f, 100f)),
            durationMillis = 500,
        )
        service.setPrivateField("gestureCoordinator", coordinator)
        service.setPrivateField("cursorOverlay", overlay)
        setConnectedService(service)
        overlay.showAt(10, 10)

        try {
            assertEquals(SubmitResult.ACCEPTED, service.submitGesture(drag))
            assertTrue(mainHandler.hasCallbacks(timeoutCheck))
            assertEquals(View.VISIBLE, windowManager.view!!.visibility)

            lifecycleEvent(service)

            assertEquals(1, port.cancellationCount)
            assertFalse(mainHandler.hasCallbacks(timeoutCheck))
            assertEquals(View.GONE, windowManager.view!!.visibility)
            assertEquals(1, windowManager.removeCount)
            assertNull(service.privateField("gestureCoordinator"))
            assertNull(service.privateField("cursorOverlay"))
            assertNull(TouchAccessibilityService.connectedService)
            assertEquals(SubmitResult.REJECTED, service.submitGesture(drag))
        } finally {
            mainHandler.removeCallbacks(timeoutCheck)
            setConnectedService(null)
            if (windowManager.removeCount == 0) overlay.remove()
        }
    }

    private fun TouchAccessibilityService.setPrivateField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    private fun TouchAccessibilityService.privateField(name: String): Any? =
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateField)
        }

    private fun setConnectedService(service: TouchAccessibilityService?) {
        TouchAccessibilityService::class.java.getDeclaredField("connectedService").apply {
            isAccessible = true
            set(null, service)
        }
    }

    private inner class LifecycleRaceFixture {
        val service = TouchAccessibilityService()
        val port = BlockingGesturePort()
        val coordinator = GestureCoordinator(
            port = port,
            clock = { 0L },
            callbackTimeoutMillis = 2_000,
        )
        val windowManager = RecordingWindowManager()
        val overlay = CursorOverlay(
            InstrumentationRegistry.getInstrumentation().targetContext,
            windowManager.instance,
        ) { DisplayBounds(width = 1920, height = 1080) }
        val timeoutCheck = service.privateField("timeoutCheck") as Runnable
        val mainHandler = BlockingHandler(
            (service.privateField("mainHandler") as Handler).looper,
        )
        val drag = GestureSpec.Drag(
            points = listOf(PointF(10f, 10f), PointF(100f, 100f)),
            durationMillis = 500,
        )
        val successor = GestureSpec.Tap(PointF(20f, 20f), durationMillis = 80)

        init {
            service.setPrivateField("gestureCoordinator", coordinator)
            service.setPrivateField("cursorOverlay", overlay)
            service.setPrivateField("mainHandler", mainHandler)
            setConnectedService(service)
            overlay.showAt(10, 10)
        }

        fun runRace(lifecycleEvent: (TouchAccessibilityService) -> Unit) {
            val executor = Executors.newFixedThreadPool(2)
            val lifecycleStarted = CountDownLatch(1)
            val lifecycleCompleted = CountDownLatch(1)
            lateinit var lifecycleThread: Thread

            try {
                val submission = executor.submit<SubmitResult> {
                    service.submitGesture(drag)
                }
                assertTrue(port.dispatchEntered.await(5, TimeUnit.SECONDS))
                val lifecycle = executor.submit {
                    lifecycleThread = Thread.currentThread()
                    lifecycleStarted.countDown()
                    try {
                        lifecycleEvent(service)
                    } finally {
                        lifecycleCompleted.countDown()
                    }
                }
                assertTrue(lifecycleStarted.await(5, TimeUnit.SECONDS))
                awaitThreadState(lifecycleThread, Thread.State.BLOCKED)

                port.releaseDispatch.countDown()
                assertTrue(mainHandler.postEntered.await(5, TimeUnit.SECONDS))
                lifecycleCompleted.await(1, TimeUnit.SECONDS)
                assertEquals(1L, lifecycleCompleted.count)

                mainHandler.releasePost.countDown()
                assertEquals(SubmitResult.ACCEPTED, submission.get(5, TimeUnit.SECONDS))
                lifecycle.get(5, TimeUnit.SECONDS)
            } finally {
                port.releaseDispatch.countDown()
                mainHandler.releasePost.countDown()
                executor.shutdownNow()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        fun close() {
            mainHandler.removeCallbacks(timeoutCheck)
            setConnectedService(null)
            if (windowManager.removeCount == 0) overlay.remove()
        }
    }

    private fun awaitThreadState(thread: Thread, expected: Thread.State) {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadlineNanos) {
            if (thread.state == expected) return
            Thread.yield()
        }
        fail("Thread ${thread.name} never reached $expected; was ${thread.state}")
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

    private class BlockingGesturePort : GesturePort {
        val dispatchEntered = CountDownLatch(1)
        val releaseDispatch = CountDownLatch(1)
        val cancellationCount = AtomicInteger()
        val callbacks = mutableListOf<GestureCallback>()

        override fun dispatch(spec: GestureSpec, callback: GestureCallback): Boolean {
            callbacks += callback
            if (callbacks.size == 1) {
                dispatchEntered.countDown()
                return releaseDispatch.await(5, TimeUnit.SECONDS)
            }
            return true
        }

        override fun cancelActiveGesture() {
            cancellationCount.incrementAndGet()
        }
    }

    private class BlockingHandler(looper: android.os.Looper) : Handler(looper) {
        val postEntered = CountDownLatch(1)
        val releasePost = CountDownLatch(1)
        private val postCount = AtomicInteger()

        override fun sendMessageAtTime(message: Message, uptimeMillis: Long): Boolean {
            if (postCount.getAndIncrement() == 0) {
                postEntered.countDown()
                if (!releasePost.await(5, TimeUnit.SECONDS)) return false
            }
            return super.sendMessageAtTime(message, uptimeMillis)
        }
    }

    private class QueuedHandler(looper: android.os.Looper) : Handler(looper) {
        private val queuedMessages = ArrayDeque<Message>()

        override fun sendMessageAtTime(message: Message, uptimeMillis: Long): Boolean {
            queuedMessages += message
            return true
        }

        fun runQueued() {
            while (queuedMessages.isNotEmpty()) {
                checkNotNull(queuedMessages.removeFirst().callback).run()
            }
        }
    }
}

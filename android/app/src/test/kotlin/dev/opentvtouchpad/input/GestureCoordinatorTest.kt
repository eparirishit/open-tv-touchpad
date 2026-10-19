package dev.opentvtouchpad.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureCoordinatorTest {
    private val firstTap = GestureSpec.Tap(PointF(100f, 200f), durationMillis = 80)
    private val secondTap = GestureSpec.Tap(PointF(300f, 400f), durationMillis = 80)

    @Test
    fun dispatchesOneAcceptedGesture() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(firstTap))
        assertEquals(listOf(firstTap), port.dispatched)
    }

    @Test
    fun rejectsDuplicateSubmissionWhileGestureIsInFlight() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(firstTap))
        assertEquals(SubmitResult.DUPLICATE, coordinator.submit(firstTap))
        assertEquals(listOf(firstTap), port.dispatched)
    }

    @Test
    fun rejectsConcurrentSubmissionWithoutQueueingIt() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(firstTap))
        assertEquals(SubmitResult.IN_FLIGHT, coordinator.submit(secondTap))

        port.callbacks.single().onCompleted()

        assertEquals(listOf(firstTap), port.dispatched)
    }

    @Test
    fun completionReturnsCoordinatorToIdle() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)
        coordinator.submit(firstTap)

        port.callbacks.single().onCompleted()

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(secondTap))
        assertEquals(listOf(firstTap, secondTap), port.dispatched)
    }

    @Test
    fun androidCancellationReturnsCoordinatorToIdle() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)
        coordinator.submit(firstTap)

        port.callbacks.single().onCancelled()

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(secondTap))
        assertEquals(listOf(firstTap, secondTap), port.dispatched)
    }

    @Test
    fun explicitCancellationWaitsForAndroidCallbackBeforeReturningToIdle() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)
        coordinator.submit(firstTap)
        coordinator.submit(secondTap)

        coordinator.cancel(CancelReason.EXPLICIT)

        assertEquals(listOf(firstTap), port.dispatched)
        assertEquals(SubmitResult.IN_FLIGHT, coordinator.submit(secondTap))

        port.callbacks.single().onCancelled()

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(secondTap))
    }

    @Test
    fun explicitCancellationSignalsPortBeforeAdmittingSuccessor() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)
        coordinator.submit(firstTap)
        var resultDuringCancellation: SubmitResult? = null
        port.onCancelActiveGesture = {
            resultDuringCancellation = coordinator.submit(secondTap)
        }

        coordinator.cancel(CancelReason.EXPLICIT)

        assertEquals(1, port.cancellationCount)
        assertEquals(SubmitResult.IN_FLIGHT, resultDuringCancellation)
        assertEquals(SubmitResult.IN_FLIGHT, coordinator.submit(secondTap))

        port.callbacks.single().onCancelled()

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(secondTap))
    }

    @Test
    fun callbackTimeoutFailsClosedUntilCoordinatorIsRecreated() {
        val port = FakeGesturePort()
        val clock = FakeMonotonicClock()
        val coordinator = coordinator(port, clock)
        coordinator.submit(firstTap)

        clock.advanceBy(999)
        assertFalse(coordinator.checkTimeout())
        clock.advanceBy(1)
        assertTrue(coordinator.checkTimeout())

        assertEquals(listOf(firstTap), port.dispatched)
        assertTrue(coordinator.requiresRestart())
        assertEquals(SubmitResult.REJECTED, coordinator.submit(secondTap))

        port.callbacks.single().onCancelled()

        assertEquals(SubmitResult.REJECTED, coordinator.submit(secondTap))

        val replacementPort = FakeGesturePort()
        val replacement = coordinator(replacementPort)
        assertFalse(replacement.requiresRestart())
        assertEquals(SubmitResult.ACCEPTED, replacement.submit(secondTap))
    }

    @Test
    fun lateTimedOutCallbackCannotRecoverCoordinator() {
        val port = FakeGesturePort()
        val clock = FakeMonotonicClock()
        val coordinator = coordinator(port, clock)
        coordinator.submit(firstTap)
        val timedOutCallback = port.callbacks.single()

        clock.advanceBy(1_000)
        coordinator.checkTimeout()

        timedOutCallback.onCancelled()

        assertTrue(coordinator.requiresRestart())
        assertEquals(SubmitResult.REJECTED, coordinator.submit(secondTap))
    }

    @Test
    fun staleCallbackCannotCompleteSuccessorGesture() {
        val port = FakeGesturePort()
        val coordinator = coordinator(port)
        coordinator.submit(firstTap)
        val staleCallback = port.callbacks.single()
        coordinator.cancel(CancelReason.EXPLICIT)
        staleCallback.onCancelled()
        coordinator.submit(secondTap)

        staleCallback.onCompleted()

        assertEquals(SubmitResult.DUPLICATE, coordinator.submit(secondTap))
        assertEquals(listOf(firstTap, secondTap), port.dispatched)
    }

    @Test
    fun dispatchRejectionReturnsCoordinatorToIdle() {
        val port = FakeGesturePort(acceptDispatch = false)
        val coordinator = coordinator(port)

        assertEquals(SubmitResult.REJECTED, coordinator.submit(firstTap))
        port.acceptDispatch = true

        assertEquals(SubmitResult.ACCEPTED, coordinator.submit(secondTap))
    }

    private fun coordinator(
        port: GesturePort,
        clock: MonotonicClock = FakeMonotonicClock(),
    ) = GestureCoordinator(
        port = port,
        clock = clock,
        callbackTimeoutMillis = 1_000,
    )

    private class FakeGesturePort(
        var acceptDispatch: Boolean = true,
    ) : GesturePort {
        val dispatched = mutableListOf<GestureSpec>()
        val callbacks = mutableListOf<GestureCallback>()
        var cancellationCount = 0
        var onCancelActiveGesture: (() -> Unit)? = null

        override fun dispatch(spec: GestureSpec, callback: GestureCallback): Boolean {
            dispatched += spec
            callbacks += callback
            return acceptDispatch
        }

        override fun cancelActiveGesture() {
            cancellationCount += 1
            onCancelActiveGesture?.invoke()
        }
    }

    private class FakeMonotonicClock : MonotonicClock {
        private var nowMillis = 0L

        override fun nowMillis(): Long = nowMillis

        fun advanceBy(durationMillis: Long) {
            nowMillis += durationMillis
        }
    }
}

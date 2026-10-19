package dev.opentvtouchpad.input

fun interface MonotonicClock {
    fun nowMillis(): Long
}

enum class SubmitResult {
    ACCEPTED,
    DUPLICATE,
    IN_FLIGHT,
    REJECTED,
}

enum class CancelReason {
    EXPLICIT,
    ANDROID,
    TIMEOUT,
}

class GestureCoordinator(
    private val port: GesturePort,
    private val clock: MonotonicClock,
    private val callbackTimeoutMillis: Long,
) {
    private var nextToken = 0L
    private var inFlight: InFlight? = null
    private var cancellationRequested = false
    private var degraded = false

    @Synchronized
    fun submit(spec: GestureSpec): SubmitResult {
        if (degraded) return SubmitResult.REJECTED

        inFlight?.let {
            return if (it.spec == spec) SubmitResult.DUPLICATE else SubmitResult.IN_FLIGHT
        }

        val token = nextToken++
        inFlight = InFlight(spec, token, clock.nowMillis())
        cancellationRequested = false
        val accepted = port.dispatch(
            spec,
            object : GestureCallback {
                override fun onCompleted() = finish(token)

                override fun onCancelled() = finish(token)
            },
        )

        if (!accepted) {
            finish(token)
            return SubmitResult.REJECTED
        }
        return SubmitResult.ACCEPTED
    }

    @Synchronized
    fun cancel(reason: CancelReason) {
        inFlight?.let { cancel(it.token, reason) }
    }

    @Synchronized
    fun checkTimeout(): Boolean {
        val current = inFlight ?: return false
        if (clock.nowMillis() - current.startedAtMillis < callbackTimeoutMillis) return false

        cancel(current.token, CancelReason.TIMEOUT)
        finish(current.token)
        return true
    }

    @Synchronized
    fun recoverAfterHealthCheck(): Boolean {
        if (!degraded || inFlight != null) return false

        degraded = false
        return true
    }

    @Synchronized
    private fun finish(token: Long) {
        if (inFlight?.token != token) return

        inFlight = null
        cancellationRequested = false
    }

    @Synchronized
    private fun cancel(token: Long, reason: CancelReason) {
        if (inFlight?.token != token) return

        if (reason == CancelReason.TIMEOUT) degraded = true
        if (!cancellationRequested) {
            cancellationRequested = true
            port.cancelActiveGesture()
        }
    }

    private data class InFlight(
        val spec: GestureSpec,
        val token: Long,
        val startedAtMillis: Long,
    )
}

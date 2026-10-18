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

    @Synchronized
    fun submit(spec: GestureSpec): SubmitResult {
        inFlight?.let {
            return if (it.spec == spec) SubmitResult.DUPLICATE else SubmitResult.IN_FLIGHT
        }

        val token = nextToken++
        inFlight = InFlight(spec, token, clock.nowMillis())
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
        return true
    }

    @Synchronized
    private fun finish(token: Long) {
        if (inFlight?.token == token) inFlight = null
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    private fun cancel(token: Long, reason: CancelReason) {
        if (inFlight?.token != token) return

        port.cancelActiveGesture()
        finish(token)
    }

    private data class InFlight(
        val spec: GestureSpec,
        val token: Long,
        val startedAtMillis: Long,
    )
}

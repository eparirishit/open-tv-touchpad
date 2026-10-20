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

data class GestureOutcome(
    val callbackDurationMillis: Long,
    val cancellationReason: CancelReason?,
)

class GestureCoordinator(
    private val port: GesturePort,
    private val clock: MonotonicClock,
    private val callbackTimeoutMillis: Long,
    private val onFinished: (GestureOutcome) -> Unit = {},
) {
    private var nextToken = 0L
    private var inFlight: InFlight? = null
    private var cancellationRequested = false
    private var restartRequired = false

    @Synchronized
    fun submit(spec: GestureSpec): SubmitResult {
        if (restartRequired) return SubmitResult.REJECTED

        inFlight?.let {
            return if (it.spec == spec) SubmitResult.DUPLICATE else SubmitResult.IN_FLIGHT
        }

        val token = nextToken++
        inFlight = InFlight(spec, token, clock.nowMillis())
        cancellationRequested = false
        val accepted = port.dispatch(
            spec,
            object : GestureCallback {
                override fun onCompleted() = finishCompleted(token)

                override fun onCancelled() = finishCancelled(token)
            },
        )

        if (!accepted) {
            clear(token)
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
        val callbackDeadlineMillis = current.spec.durationMillis + callbackTimeoutMillis
        if (clock.nowMillis() - current.startedAtMillis < callbackDeadlineMillis) return false

        cancel(current.token, CancelReason.TIMEOUT)
        finishCancelled(current.token)
        return true
    }

    @Synchronized
    fun requiresRestart(): Boolean = restartRequired

    @Synchronized
    private fun finishCompleted(token: Long) {
        finish(token, cancellationReason = null)
    }

    @Synchronized
    private fun finishCancelled(token: Long) {
        val cancellationReason = inFlight
            ?.takeIf { it.token == token }
            ?.cancellationReason
            ?: CancelReason.ANDROID
        finish(token, cancellationReason)
    }

    private fun finish(token: Long, cancellationReason: CancelReason?) {
        val completed = inFlight?.takeIf { it.token == token } ?: return

        clear(token)
        onFinished(
            GestureOutcome(
                callbackDurationMillis = (clock.nowMillis() - completed.startedAtMillis)
                    .coerceAtLeast(0),
                cancellationReason = cancellationReason,
            ),
        )
    }

    private fun clear(token: Long) {
        if (inFlight?.token != token) return

        inFlight = null
        cancellationRequested = false
    }

    @Synchronized
    private fun cancel(token: Long, reason: CancelReason) {
        val current = inFlight?.takeIf { it.token == token } ?: return

        if (reason == CancelReason.TIMEOUT) restartRequired = true
        current.cancellationReason = reason
        if (!cancellationRequested) {
            cancellationRequested = true
            port.cancelActiveGesture()
        }
    }

    private data class InFlight(
        val spec: GestureSpec,
        val token: Long,
        val startedAtMillis: Long,
        var cancellationReason: CancelReason? = null,
    )
}

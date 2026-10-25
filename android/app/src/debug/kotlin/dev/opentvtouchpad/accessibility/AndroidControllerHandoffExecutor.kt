package dev.opentvtouchpad.accessibility

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import dev.opentvtouchpad.setup.GateViewModel

internal fun interface ControllerHandoffFrameScheduler {
    fun postFrame(callback: () -> Unit): () -> Unit
}

internal interface ControllerHandoffDeadlineScheduler {
    fun schedule(delayMillis: Long, callback: () -> Unit): () -> Unit

    fun nowMillis(): Long
}

internal fun interface ControllerHandoffMainLoopScheduler {
    fun post(callback: () -> Unit): () -> Unit
}

internal interface ControllerHandoffWindow {
    fun detachController()

    fun attachController()

    fun removeBothOverlays()
}

internal class AndroidControllerHandoffExecutor(
    private val viewModel: GateViewModel,
    private val controllerWindow: ControllerHandoffWindow,
    private val frameScheduler: ControllerHandoffFrameScheduler,
    private val deadlineScheduler: ControllerHandoffDeadlineScheduler,
    private val machine: ControllerHandoffMachine = ControllerHandoffMachine(),
) {
    private val frameCallbacks = mutableMapOf<ControllerHandoffToken, () -> Unit>()
    private val frameDeadlines = mutableMapOf<ControllerHandoffToken, () -> Unit>()
    private val frameDeadlineMillis = mutableMapOf<ControllerHandoffToken, Long>()
    private val restoreDeadlines = mutableMapOf<ControllerHandoffToken, () -> Unit>()
    private var stopped = false

    fun handle(event: ControllerHandoffEvent) {
        if (stopped) return
        machine.handle(event).forEach(::execute)
    }

    fun shutdown() {
        if (stopped) return
        machine.handle(ControllerHandoffEvent.LifecycleStopped).forEach(::execute)
        stopped = true
        cancelAll(frameCallbacks)
        cancelAll(frameDeadlines)
        frameDeadlineMillis.clear()
        cancelAll(restoreDeadlines)
    }

    private fun execute(effect: ControllerHandoffEffect) {
        when (effect) {
            ControllerHandoffEffect.RemoveController -> controllerWindow.detachController()
            is ControllerHandoffEffect.RequestFrame -> requestFrame(effect.token)
            is ControllerHandoffEffect.CancelFrame -> cancel(frameCallbacks, effect.token)
            is ControllerHandoffEffect.ScheduleFrameDeadline -> scheduleFrameDeadline(effect)
            is ControllerHandoffEffect.CancelFrameDeadline -> cancelFrameDeadline(effect.token)
            is ControllerHandoffEffect.SubmitRequest -> submit(effect)
            is ControllerHandoffEffect.ScheduleRestoreDeadline -> schedule(
                restoreDeadlines,
                effect.token,
                effect.delayMillis,
                ControllerHandoffEvent::RestoreDeadline,
            )
            is ControllerHandoffEffect.CancelRestoreDeadline -> cancel(restoreDeadlines, effect.token)
            is ControllerHandoffEffect.RestoreController -> controllerWindow.attachController()
            ControllerHandoffEffect.HideCursor -> viewModel.hideCursor()
            ControllerHandoffEffect.ClearPendingInput -> viewModel.clearPendingInput()
            ControllerHandoffEffect.RequireRestart -> viewModel.requireRestart()
            is ControllerHandoffEffect.PublishTerminalState ->
                viewModel.publishControllerHandoffTerminalState(effect.state)
            ControllerHandoffEffect.RemoveBothOverlays -> controllerWindow.removeBothOverlays()
        }
    }

    private fun requestFrame(token: ControllerHandoffToken) {
        cancel(frameCallbacks, token)
        frameCallbacks[token] = frameScheduler.postFrame {
            frameCallbacks.remove(token)
            val deadlineMillis = frameDeadlineMillis[token]
            if (deadlineMillis != null && deadlineScheduler.nowMillis() >= deadlineMillis) {
                cancelFrameDeadline(token)
                handle(ControllerHandoffEvent.FrameDeadline(token))
            } else {
                handle(ControllerHandoffEvent.FrameReady(token))
            }
        }
    }

    private fun scheduleFrameDeadline(effect: ControllerHandoffEffect.ScheduleFrameDeadline) {
        frameDeadlineMillis[effect.token] = deadlineScheduler.nowMillis() + effect.delayMillis
        schedule(
            frameDeadlines,
            effect.token,
            effect.delayMillis,
        ) { token ->
            frameDeadlineMillis.remove(token)
            ControllerHandoffEvent.FrameDeadline(token)
        }
    }

    private fun cancelFrameDeadline(token: ControllerHandoffToken) {
        frameDeadlineMillis.remove(token)
        cancel(frameDeadlines, token)
    }

    private fun submit(effect: ControllerHandoffEffect.SubmitRequest) {
        viewModel.setTapProbeOutcomeListener { outcome ->
            handle(ControllerHandoffEvent.GestureFinished(effect.token, outcome))
        }
        val result = viewModel.submitTapProbe(effect.request)
        handle(ControllerHandoffEvent.DispatchReturned(effect.token, result))
    }

    private fun schedule(
        callbacks: MutableMap<ControllerHandoffToken, () -> Unit>,
        token: ControllerHandoffToken,
        delayMillis: Long,
        event: (ControllerHandoffToken) -> ControllerHandoffEvent,
    ) {
        cancel(callbacks, token)
        callbacks[token] = deadlineScheduler.schedule(delayMillis) {
            callbacks.remove(token)
            handle(event(token))
        }
    }

    private fun cancel(
        callbacks: MutableMap<ControllerHandoffToken, () -> Unit>,
        token: ControllerHandoffToken,
    ) {
        callbacks.remove(token)?.invoke()
    }

    private fun cancelAll(callbacks: MutableMap<ControllerHandoffToken, () -> Unit>) {
        callbacks.values.toList().forEach { it() }
        callbacks.clear()
    }
}

internal class AndroidFrameScheduler(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : ControllerHandoffFrameScheduler {
    override fun postFrame(callback: () -> Unit): () -> Unit {
        val frameCallback = Choreographer.FrameCallback { callback() }
        choreographer.postFrameCallback(frameCallback)
        return { choreographer.removeFrameCallback(frameCallback) }
    }
}

internal class AndroidMonotonicDeadlineScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ControllerHandoffDeadlineScheduler {
    override fun nowMillis(): Long = clock()

    override fun schedule(delayMillis: Long, callback: () -> Unit): () -> Unit {
        require(delayMillis in 0..FRAME_TIMEOUT_MILLIS)
        val deadlineMillis = nowMillis() + delayMillis
        lateinit var runnable: Runnable
        runnable = Runnable {
            val remainingMillis = deadlineMillis - nowMillis()
            if (remainingMillis > 0L) {
                handler.postDelayed(runnable, remainingMillis)
            } else {
                callback()
            }
        }
        handler.postDelayed(runnable, delayMillis)
        return { handler.removeCallbacks(runnable) }
    }
}

internal class AndroidMainLoopScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : ControllerHandoffMainLoopScheduler {
    override fun post(callback: () -> Unit): () -> Unit {
        val runnable = Runnable(callback)
        handler.post(runnable)
        return { handler.removeCallbacks(runnable) }
    }
}

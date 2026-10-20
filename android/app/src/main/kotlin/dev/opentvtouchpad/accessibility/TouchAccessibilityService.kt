package dev.opentvtouchpad.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureCoordinator
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.SubmitResult

class TouchAccessibilityService : AccessibilityService() {
    private val runtimeLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutCheck = Runnable {
        synchronized(runtimeLock) { gestureCoordinator?.checkTimeout() }
    }
    private var gestureCoordinator: GestureCoordinator? = null
    private var cursorOverlay: CursorOverlay? = null
    private var gestureOutcomeListener: ((GestureOutcome) -> Unit)? = null
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        synchronized(runtimeLock) {
            windowManager = getSystemService(WindowManager::class.java)
            cursorOverlay = CursorOverlay(this, windowManager, ::currentDisplayBounds)
            gestureCoordinator = GestureCoordinator(
                port = AndroidGesturePort(this, ::currentDisplayBounds),
                clock = SystemClock::elapsedRealtime,
                callbackTimeoutMillis = CALLBACK_TIMEOUT_MILLIS,
                onFinished = { outcome ->
                    mainHandler.post {
                        val listener = synchronized(runtimeLock) { gestureOutcomeListener }
                        listener?.invoke(outcome)
                    }
                },
            )
            connectedService = this
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        synchronized(runtimeLock) {
            gestureCoordinator?.cancel(CancelReason.ANDROID)
            cursorOverlay?.hide()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdownRuntime(CancelReason.ANDROID)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdownRuntime(CancelReason.ANDROID)
        super.onDestroy()
    }

    fun submitGesture(spec: GestureSpec): SubmitResult = synchronized(runtimeLock) {
        val coordinator = gestureCoordinator ?: return@synchronized SubmitResult.REJECTED
        coordinator.submit(spec).also { result ->
            if (result == SubmitResult.ACCEPTED) {
                mainHandler.removeCallbacks(timeoutCheck)
                mainHandler.postDelayed(
                    timeoutCheck,
                    timeoutCheckDelayMillis(spec, CALLBACK_TIMEOUT_MILLIS),
                )
            }
        }
    }

    fun cancelGesture() {
        synchronized(runtimeLock) {
            gestureCoordinator?.cancel(CancelReason.EXPLICIT)
        }
    }

    fun showCursorAt(x: Int, y: Int) {
        synchronized(runtimeLock) {
            cursorOverlay?.showAt(x, y)
        }
    }

    fun moveCursorTo(x: Int, y: Int) {
        synchronized(runtimeLock) {
            cursorOverlay?.moveTo(x, y)
        }
    }

    fun hideCursor() {
        synchronized(runtimeLock) {
            cursorOverlay?.hide()
        }
    }

    fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?) {
        synchronized(runtimeLock) {
            gestureOutcomeListener = listener
        }
    }

    fun currentBounds(): DisplayBounds = currentDisplayBounds()

    private fun shutdownRuntime(reason: CancelReason) {
        synchronized(runtimeLock) {
            gestureCoordinator?.cancel(reason)
            mainHandler.removeCallbacks(timeoutCheck)
            cursorOverlay?.hide()
            cursorOverlay?.remove()
            gestureOutcomeListener = null
            gestureCoordinator = null
            cursorOverlay = null
            if (connectedService === this) connectedService = null
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayBounds(): DisplayBounds {
        val width: Int
        val height: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val size = Point()
            windowManager.defaultDisplay.getRealSize(size)
            width = size.x
            height = size.y
        }
        return DisplayBounds(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    companion object {
        @Volatile
        var connectedService: TouchAccessibilityService? = null
            private set

        private const val CALLBACK_TIMEOUT_MILLIS = 2_000L

        @PublishedApi
        @JvmStatic
        internal fun timeoutCheckDelayMillis(
            spec: GestureSpec,
            callbackTimeoutMillis: Long,
        ): Long = spec.durationMillis + callbackTimeoutMillis
    }
}

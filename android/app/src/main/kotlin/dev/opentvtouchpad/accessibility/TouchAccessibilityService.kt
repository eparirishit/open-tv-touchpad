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
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.SubmitResult

class TouchAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutCheck = Runnable { gestureCoordinator?.checkTimeout() }
    private var gestureCoordinator: GestureCoordinator? = null
    private var cursorOverlay: CursorOverlay? = null
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        cursorOverlay = CursorOverlay(this, windowManager, ::currentDisplayBounds)
        gestureCoordinator = GestureCoordinator(
            port = AndroidGesturePort(this, ::currentDisplayBounds),
            clock = SystemClock::elapsedRealtime,
            callbackTimeoutMillis = CALLBACK_TIMEOUT_MILLIS,
        )
        connectedService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        shutdownRuntime(CancelReason.ANDROID)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdownRuntime(CancelReason.ANDROID)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdownRuntime(CancelReason.ANDROID)
        super.onDestroy()
    }

    fun submitGesture(spec: GestureSpec): SubmitResult {
        val coordinator = gestureCoordinator ?: return SubmitResult.REJECTED
        val result = coordinator.submit(spec)
        if (result == SubmitResult.ACCEPTED) {
            mainHandler.removeCallbacks(timeoutCheck)
            mainHandler.postDelayed(
                timeoutCheck,
                timeoutCheckDelayMillis(spec, CALLBACK_TIMEOUT_MILLIS),
            )
        }
        return result
    }

    fun cancelGesture() {
        gestureCoordinator?.cancel(CancelReason.EXPLICIT)
    }

    fun showCursorAt(x: Int, y: Int) {
        cursorOverlay?.showAt(x, y)
    }

    fun moveCursorTo(x: Int, y: Int) {
        cursorOverlay?.moveTo(x, y)
    }

    fun hideCursor() {
        cursorOverlay?.hide()
    }

    fun currentBounds(): DisplayBounds = currentDisplayBounds()

    private fun shutdownRuntime(reason: CancelReason) {
        gestureCoordinator?.cancel(reason)
        mainHandler.removeCallbacks(timeoutCheck)
        cursorOverlay?.hide()
        cursorOverlay?.remove()
        gestureCoordinator = null
        cursorOverlay = null
        if (connectedService === this) connectedService = null
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

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
    private var controllerOverlay: ServiceOverlay? = null
    private val gestureOutcomeListeners = linkedSetOf<(GestureOutcome) -> Unit>()
    private var latestGestureOutcome: GestureOutcome? = null
    private var restartRequired = false
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        synchronized(runtimeLock) {
            restartRequired = false
            windowManager = getSystemService(WindowManager::class.java)
            cursorOverlay = CursorOverlay(this, windowManager, ::currentDisplayBounds)
            lateinit var coordinator: GestureCoordinator
            coordinator = GestureCoordinator(
                port = AndroidGesturePort(this, ::currentDisplayBounds),
                clock = SystemClock::elapsedRealtime,
                callbackTimeoutMillis = CALLBACK_TIMEOUT_MILLIS,
                onFinished = { outcome -> publishGestureOutcome(coordinator, outcome) },
            )
            gestureCoordinator = coordinator
            publishConnectedService(this)
            controllerOverlay = ServiceOverlayFactory.create(
                this,
                windowManager,
                serviceProvider = { this },
            ).also(ServiceOverlay::show)
        }
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

    fun submitGesture(spec: GestureSpec): SubmitResult = synchronized(runtimeLock) {
        if (restartRequired) return@synchronized SubmitResult.REJECTED
        val coordinator = gestureCoordinator ?: return@synchronized SubmitResult.REJECTED
        coordinator.submit(spec).also { result ->
            if (result == SubmitResult.ACCEPTED) {
                latestGestureOutcome = null
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

    fun requireRestart() {
        synchronized(runtimeLock) {
            restartRequired = true
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

    fun addGestureOutcomeListener(listener: (GestureOutcome) -> Unit) {
        synchronized(runtimeLock) {
            gestureOutcomeListeners += listener
        }
    }

    fun removeGestureOutcomeListener(listener: (GestureOutcome) -> Unit) {
        synchronized(runtimeLock) {
            gestureOutcomeListeners -= listener
        }
    }

    internal fun publishGestureOutcome(
        sourceCoordinator: GestureCoordinator,
        outcome: GestureOutcome,
    ) {
        val listeners = synchronized(runtimeLock) {
            if (gestureCoordinator !== sourceCoordinator) return
            latestGestureOutcome = outcome
            gestureOutcomeListeners.toList()
        }
        mainHandler.post {
            listeners.forEach { listener ->
                val stillObserved = synchronized(runtimeLock) {
                    listener in gestureOutcomeListeners
                }
                if (stillObserved) listener(outcome)
            }
        }
    }

    internal fun isGestureInFlight(): Boolean = synchronized(runtimeLock) {
        gestureCoordinator?.isInFlight() == true
    }

    internal fun latestGestureOutcome(): GestureOutcome? = synchronized(runtimeLock) {
        latestGestureOutcome
    }

    fun currentBounds(): DisplayBounds = currentDisplayBounds()

    private fun shutdownRuntime(reason: CancelReason) {
        synchronized(runtimeLock) {
            gestureCoordinator?.cancel(reason)
            mainHandler.removeCallbacks(timeoutCheck)
            shutdownOverlays()
            gestureOutcomeListeners.clear()
            latestGestureOutcome = null
            gestureCoordinator = null
            cursorOverlay = null
            controllerOverlay = null
            if (connectedService === this) publishConnectedService(null)
        }
    }

    private fun shutdownOverlays() {
        controllerOverlay?.remove()
        cursorOverlay?.hide()
        cursorOverlay?.remove()
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
        private val connectionLock = Any()
        private val connectionListeners = linkedSetOf<() -> Unit>()

        @Volatile
        var connectedService: TouchAccessibilityService? = null
            private set

        internal fun addConnectionListener(listener: () -> Unit) {
            synchronized(connectionLock) {
                connectionListeners += listener
            }
        }

        internal fun removeConnectionListener(listener: () -> Unit) {
            synchronized(connectionLock) {
                connectionListeners -= listener
            }
        }

        private fun publishConnectedService(service: TouchAccessibilityService?) {
            val listeners = synchronized(connectionLock) {
                connectedService = service
                connectionListeners.toList()
            }
            listeners.forEach { listener ->
                val stillObserved = synchronized(connectionLock) {
                    listener in connectionListeners
                }
                if (stillObserved) listener()
            }
        }

        private const val CALLBACK_TIMEOUT_MILLIS = 2_000L

        @PublishedApi
        @JvmStatic
        internal fun timeoutCheckDelayMillis(
            spec: GestureSpec,
            callbackTimeoutMillis: Long,
        ): Long = spec.durationMillis + callbackTimeoutMillis
    }
}

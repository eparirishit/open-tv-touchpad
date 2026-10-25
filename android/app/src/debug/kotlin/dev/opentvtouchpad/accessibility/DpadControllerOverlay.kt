package dev.opentvtouchpad.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import dev.opentvtouchpad.R
import dev.opentvtouchpad.setup.GateAction
import dev.opentvtouchpad.setup.GateRuntime
import dev.opentvtouchpad.setup.GateUiState
import dev.opentvtouchpad.setup.GateViewModel
import dev.opentvtouchpad.setup.CalibrationActivity
import dev.opentvtouchpad.setup.ManualTargetResult
import dev.opentvtouchpad.setup.ProbeControllerState
import dev.opentvtouchpad.setup.TapProbeRequest
import dev.opentvtouchpad.setup.TapProbeMode
import dev.opentvtouchpad.input.CancelReason
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.SubmitResult

internal class DpadControllerOverlay @JvmOverloads constructor(
    context: Context,
    private val windowManager: WindowManager,
    private val runtime: GateRuntime,
    frameScheduler: ControllerHandoffFrameScheduler = AndroidFrameScheduler(),
    deadlineScheduler: ControllerHandoffDeadlineScheduler = AndroidMonotonicDeadlineScheduler(),
    private val mainLoopScheduler: ControllerHandoffMainLoopScheduler = AndroidMainLoopScheduler(),
) : ServiceOverlay {
    private val viewModel = GateViewModel(runtime, ViewConfiguration.getTapTimeout().toLong())
    private val controllerView = ControllerView(
        context,
        viewModel,
        ::handleTapKeyEvent,
        ::hasArmedTapKey,
        ::renderFailureDiagnostics,
    )
    private val diagnosticView = TextView(context).apply {
        tag = DIAGNOSTIC_TAG
        setBackgroundColor(0xD9000000.toInt())
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(12, 8, 12, 8)
    }
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private var attached = false
    private var diagnosticAttached = false
    private var started = false
    private val ownedConfirmKeyCodes = mutableSetOf<Int>()
    private var armedHandoffKeyCode: Int? = null
    private var releasedHandoffKeyCode: Int? = null
    private var armedDirectKeyCode: Int? = null
    private var pendingDirectFailure: ControllerHandoffTerminalState? = null
    private var deferredDirectFailure: (() -> Unit)? = null
    private var directFailureFence = 0L
    private var deferredConfirmUp: (() -> Unit)? = null
    private var confirmUpFence = 0L
    private val handoffExecutor = AndroidControllerHandoffExecutor(
        viewModel = viewModel,
        controllerWindow = object : ControllerHandoffWindow {
            override fun detachController() {
                viewModel.publishControllerState(ProbeControllerState.HANDOFF)
                removeControllerWindow()
            }

            override fun attachController() {
                addControllerWindow()
                viewModel.publishControllerState(ProbeControllerState.PRESENT)
            }

            override fun removeBothOverlays() {
                removeControllerWindow()
                removeDiagnosticWindow()
                viewModel.publishControllerState(ProbeControllerState.REMOVED)
                viewModel.hideCursor()
            }
        },
        frameScheduler = frameScheduler,
        deadlineScheduler = deadlineScheduler,
    )

    override fun show() {
        addControllerWindow()
        if (!started) {
            started = true
            controllerView.start()
        }
    }

    override fun remove() {
        cancelDeferredDirectFailure()
        cancelDeferredConfirmUp()
        ownedConfirmKeyCodes.clear()
        armedHandoffKeyCode = null
        releasedHandoffKeyCode = null
        armedDirectKeyCode = null
        handoffExecutor.shutdown()
        if (started) {
            started = false
            controllerView.stop()
        }
        removeControllerWindow()
        removeDiagnosticWindow()
    }

    private fun handleTapKeyEvent(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return true
                val tapAlreadyArmed = hasArmedTapKey()
                ownedConfirmKeyCodes += event.keyCode
                if (tapAlreadyArmed) return true
                CalibrationActivity.resetResultForProbe()
                val request = viewModel.prepareTapProbe() ?: return true
                if (request.config.requiresControllerHandoff) {
                    armedHandoffKeyCode = event.keyCode
                    handoffExecutor.handle(
                        ControllerHandoffEvent.ConfirmDown(event.keyCode, request),
                    )
                } else {
                    armedDirectKeyCode = event.keyCode
                    submitDirectProbe(request)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!ownedConfirmKeyCodes.remove(event.keyCode)) return true
                if (event.keyCode == armedHandoffKeyCode) {
                    armedHandoffKeyCode = null
                    releasedHandoffKeyCode = event.keyCode
                }
                if (event.keyCode == armedDirectKeyCode) {
                    armedDirectKeyCode = null
                }
                finishTapAfterOwnedKeysReleased()
            }
        }
        return true
    }

    private fun hasArmedTapKey(): Boolean =
        ownedConfirmKeyCodes.isNotEmpty() ||
            armedHandoffKeyCode != null ||
            releasedHandoffKeyCode != null ||
            armedDirectKeyCode != null ||
            pendingDirectFailure != null ||
            deferredConfirmUp != null ||
            deferredDirectFailure != null

    private fun submitDirectProbe(request: TapProbeRequest) {
        viewModel.setTapProbeOutcomeListener { outcome ->
            if (outcome.cancellationReason != null) {
                queueDirectFailure(outcome.toTerminalState())
            }
        }
        val result = viewModel.submitTapProbe(request)
        if (result != SubmitResult.ACCEPTED) {
            queueDirectFailure(ControllerHandoffTerminalState.REJECTED)
        }
    }

    private fun queueDirectFailure(terminalState: ControllerHandoffTerminalState) {
        if (ownedConfirmKeyCodes.isEmpty()) {
            failClosed(terminalState)
            return
        }
        pendingDirectFailure = terminalState
        finishTapAfterOwnedKeysReleased()
    }

    private fun failClosed(terminalState: ControllerHandoffTerminalState) {
        removeControllerWindow()
        viewModel.hideCursor()
        viewModel.clearPendingInput()
        viewModel.requireRestart()
        viewModel.publishControllerHandoffTerminalState(terminalState)
    }

    private fun GestureOutcome.toTerminalState(): ControllerHandoffTerminalState =
        if (cancellationReason == CancelReason.TIMEOUT) {
            ControllerHandoffTerminalState.TIMEOUT
        } else {
            ControllerHandoffTerminalState.CANCELLED
        }

    private fun finishTapAfterOwnedKeysReleased() {
        if (ownedConfirmKeyCodes.isNotEmpty()) return
        releasedHandoffKeyCode?.let(::deferConfirmUp)
        if (armedDirectKeyCode == null && pendingDirectFailure != null) {
            deferDirectFailure()
        }
    }

    private fun deferDirectFailure() {
        if (deferredDirectFailure != null) return
        val fence = ++directFailureFence
        deferredDirectFailure = mainLoopScheduler.post {
            if (fence != directFailureFence) return@post
            deferredDirectFailure = null
            if (ownedConfirmKeyCodes.isNotEmpty()) return@post
            val terminalState = pendingDirectFailure ?: return@post
            pendingDirectFailure = null
            failClosed(terminalState)
        }
    }

    private fun cancelDeferredDirectFailure() {
        directFailureFence += 1
        deferredDirectFailure?.invoke()
        deferredDirectFailure = null
        pendingDirectFailure = null
    }

    private fun deferConfirmUp(keyCode: Int) {
        if (deferredConfirmUp != null) return
        val fence = ++confirmUpFence
        deferredConfirmUp = mainLoopScheduler.post {
            if (fence != confirmUpFence) return@post
            deferredConfirmUp = null
            if (ownedConfirmKeyCodes.isNotEmpty()) return@post
            releasedHandoffKeyCode = null
            handoffExecutor.handle(ControllerHandoffEvent.ConfirmUp(keyCode))
        }
    }

    private fun cancelDeferredConfirmUp() {
        confirmUpFence += 1
        deferredConfirmUp?.invoke()
        deferredConfirmUp = null
        releasedHandoffKeyCode = null
    }

    private fun addControllerWindow() {
        if (attached) return
        windowManager.addView(controllerView, layoutParams)
        attached = true
        viewModel.publishControllerState(ProbeControllerState.PRESENT)
    }

    private fun removeControllerWindow() {
        if (!attached) return
        windowManager.removeViewImmediate(controllerView)
        attached = false
    }

    private fun renderFailureDiagnostics(state: GateUiState) {
        if (!state.restartRequired || state.controllerState != ProbeControllerState.REMOVED) {
            removeDiagnosticWindow()
            return
        }

        val none = controllerView.context.getString(R.string.gate_none)
        val diagnostics = listOf(
            controllerView.context.getString(R.string.probe_mode_value, state.tapProbeMode.name),
            controllerView.context.getString(
                R.string.probe_admission_value,
                state.lastDispatchResult?.name ?: none,
            ),
            controllerView.context.getString(
                R.string.probe_callback_value,
                state.callbackOutcome?.name ?: none,
            ),
            controllerView.context.getString(
                R.string.probe_callback_duration_value,
                state.callbackDurationMillis?.toString() ?: none,
            ),
            state.bounds?.let {
                controllerView.context.getString(R.string.probe_display_value, it.width, it.height)
            } ?: controllerView.context.getString(R.string.probe_display_unavailable),
            controllerView.context.getString(
                R.string.probe_controller_value,
                state.controllerState.name,
            ),
            controllerView.context.getString(
                R.string.probe_manual_result_value,
                state.manualTargetResult?.name ?: none,
            ),
        ).joinToString("\n")
        diagnosticView.text = diagnostics
        diagnosticView.contentDescription = diagnostics
        if (!diagnosticAttached) {
            windowManager.addView(diagnosticView, diagnosticLayoutParams)
            diagnosticAttached = true
        }
    }

    private fun removeDiagnosticWindow() {
        if (!diagnosticAttached) return
        windowManager.removeViewImmediate(diagnosticView)
        diagnosticAttached = false
    }

    private val diagnosticLayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.END or Gravity.BOTTOM
    }

    private class ControllerView(
        context: Context,
        private val viewModel: GateViewModel,
        private val onTapKeyEvent: (KeyEvent) -> Boolean,
        private val hasArmedTapKey: () -> Boolean,
        private val onStateChanged: (GateUiState) -> Unit,
    ) : FrameLayout(context) {
        private val actionButtons: Map<GateAction, Button>
        private val commandButtons: Map<Button, () -> Unit>
        private val focusOrder: List<Button>

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            LayoutInflater.from(context).inflate(R.layout.controller_overlay, this, true)
            actionButtons = mapOf(
                GateAction.SHOW_CURSOR to findViewById(R.id.show_cursor),
                GateAction.MOVE_UP to findViewById(R.id.move_up),
                GateAction.MOVE_DOWN to findViewById(R.id.move_down),
                GateAction.MOVE_LEFT to findViewById(R.id.move_left),
                GateAction.MOVE_RIGHT to findViewById(R.id.move_right),
                GateAction.TAP to findViewById(R.id.tap),
                GateAction.SWIPE_VERTICAL to findViewById(R.id.swipe_vertical),
                GateAction.SWIPE_HORIZONTAL to findViewById(R.id.swipe_horizontal),
                GateAction.DRAG_PATH to findViewById(R.id.drag_path),
                GateAction.CANCEL to findViewById(R.id.cancel),
            )
            actionButtons.forEach { (action, button) ->
                button.setOnClickListener { viewModel.perform(action, isRepeat = false) }
            }
            commandButtons = mapOf(
                findViewById<Button>(R.id.probe_mode_baseline) to {
                    viewModel.selectTapProbeMode(TapProbeMode.BASELINE_80_MS)
                },
                findViewById<Button>(R.id.probe_mode_system_timeout) to {
                    viewModel.selectTapProbeMode(TapProbeMode.SYSTEM_TAP_TIMEOUT)
                },
                findViewById<Button>(R.id.probe_mode_controller_handoff) to {
                    viewModel.selectTapProbeMode(TapProbeMode.CONTROLLER_HANDOFF)
                },
                findViewById<Button>(R.id.manual_activated) to {
                    viewModel.recordManualTargetResult(ManualTargetResult.ACTIVATED)
                },
                findViewById<Button>(R.id.manual_not_activated) to {
                    viewModel.recordManualTargetResult(ManualTargetResult.NOT_ACTIVATED)
                },
                findViewById<Button>(R.id.launch_calibration) to {
                    context.startActivity(
                        Intent(context, CalibrationActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            focusOrder = listOf(
                findViewById<Button>(R.id.probe_mode_baseline),
                findViewById<Button>(R.id.probe_mode_system_timeout),
                findViewById<Button>(R.id.probe_mode_controller_handoff),
            ) + actionButtons.values + listOf(
                findViewById<Button>(R.id.manual_activated),
                findViewById<Button>(R.id.manual_not_activated),
                findViewById<Button>(R.id.launch_calibration),
                findViewById<Button>(R.id.accessibility_settings),
            )
        }

        fun start() {
            viewModel.observe(::render)
            viewModel.refresh()
        }

        fun stop() {
            viewModel.detach()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode in DIRECTION_KEYS && event.action == KeyEvent.ACTION_DOWN) {
                val step = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                val currentIndex = focusOrder.indexOf(findFocus()).coerceAtLeast(0)
                repeat(focusOrder.size) { offset ->
                    val index = Math.floorMod(currentIndex + step * (offset + 1), focusOrder.size)
                    val candidate = focusOrder[index]
                    if (candidate.isEnabled && candidate.visibility == View.VISIBLE) {
                        candidate.requestFocus()
                        return true
                    }
                }
                return true
            }
            if (event.keyCode !in CONFIRM_KEYS) return super.dispatchKeyEvent(event)
            if (hasArmedTapKey()) return onTapKeyEvent(event)
            val action = actionButtons.entries.firstOrNull { it.value === findFocus() }?.key
            if (action != null) {
                if (action == GateAction.TAP) return onTapKeyEvent(event)
                if (event.action == KeyEvent.ACTION_DOWN) {
                    viewModel.perform(action, isRepeat = event.repeatCount > 0)
                }
                return true
            }
            val command = (findFocus() as? Button)?.let(commandButtons::get)
                ?: return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) command()
            return true
        }

        private fun render(state: GateUiState) {
            findViewById<TextView>(R.id.gate_status).setText(
                if (state.connected) {
                    R.string.gate_status_connected
                } else {
                    R.string.gate_status_disabled
                },
            )
            renderProbeDiagnostics(state)
            onStateChanged(state)
            actionButtons.forEach { (action, button) ->
                button.isEnabled = if (action == GateAction.CANCEL) {
                    state.connected && state.gestureInFlight
                } else {
                    state.controlsEnabled
                }
            }
            findViewById<Button>(R.id.accessibility_settings).apply {
                visibility = View.VISIBLE
                isEnabled = state.settingsGuidanceVisible
            }

            val focused = findFocus()
            if (focused !in focusOrder || focused?.isEnabled == false) {
                when {
                    state.gestureInFlight -> actionButtons.getValue(GateAction.CANCEL).requestFocus()
                    state.connected -> actionButtons.getValue(GateAction.SHOW_CURSOR).requestFocus()
                    else -> findViewById<Button>(R.id.accessibility_settings).requestFocus()
                }
            }
        }

        private fun renderProbeDiagnostics(state: GateUiState) {
            val none = context.getString(R.string.gate_none)
            findViewById<TextView>(R.id.probe_mode).text = context.getString(
                R.string.probe_mode_value,
                state.tapProbeMode.name,
            )
            findViewById<TextView>(R.id.probe_admission).text = context.getString(
                R.string.probe_admission_value,
                state.lastDispatchResult?.name ?: none,
            )
            findViewById<TextView>(R.id.probe_callback).text = context.getString(
                R.string.probe_callback_value,
                state.callbackOutcome?.name ?: none,
            )
            findViewById<TextView>(R.id.probe_callback_duration).text = context.getString(
                R.string.probe_callback_duration_value,
                state.callbackDurationMillis?.toString() ?: none,
            )
            findViewById<TextView>(R.id.probe_display).text = state.bounds?.let {
                context.getString(R.string.probe_display_value, it.width, it.height)
            } ?: context.getString(R.string.probe_display_unavailable)
            findViewById<TextView>(R.id.probe_controller).text = context.getString(
                R.string.probe_controller_value,
                state.controllerState.name,
            )
            findViewById<TextView>(R.id.probe_manual_result).text = context.getString(
                R.string.probe_manual_result_value,
                state.manualTargetResult?.name ?: none,
            )
        }

        private companion object {
            val CONFIRM_KEYS = setOf(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
            )
            val DIRECTION_KEYS = setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
            )
        }
    }

    private companion object {
        const val DIAGNOSTIC_TAG = "probe_failure_diagnostics"
    }
}

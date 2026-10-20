package dev.opentvtouchpad.setup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import dev.opentvtouchpad.R
import dev.opentvtouchpad.accessibility.TouchAccessibilityService
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.GestureOutcome
import dev.opentvtouchpad.input.GestureSpec
import dev.opentvtouchpad.input.PointF
import dev.opentvtouchpad.input.SubmitResult

class GateActivity : Activity() {
    private lateinit var viewModel: GateViewModel
    private lateinit var actionButtons: Map<GateAction, Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gate)

        viewModel = GateViewModel(AccessibilityGateRuntime())
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
        findViewById<Button>(R.id.accessibility_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.observe(::render)
        viewModel.refresh()
    }

    override fun onStop() {
        viewModel.detach()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in CONFIRM_KEYS) return super.dispatchKeyEvent(event)
        val action = actionButtons.entries.firstOrNull { it.value === currentFocus }?.key
            ?: return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            viewModel.perform(action, isRepeat = event.repeatCount > 0)
        }
        return true
    }

    private fun render(state: GateUiState) {
        findViewById<TextView>(R.id.gate_status).setText(
            if (state.connected) R.string.gate_status_connected else R.string.gate_status_disabled,
        )
        findViewById<TextView>(R.id.display_bounds).text = state.bounds?.let {
            getString(R.string.gate_bounds_value, it.width, it.height)
        } ?: getString(R.string.gate_bounds_unavailable)
        findViewById<TextView>(R.id.last_dispatch_result).text = getString(
            R.string.gate_dispatch_value,
            state.lastDispatchResult?.name ?: getString(R.string.gate_none),
        )
        findViewById<TextView>(R.id.callback_duration).text = getString(
            R.string.gate_callback_value,
            state.callbackDurationMillis?.toString() ?: getString(R.string.gate_none),
        )
        findViewById<TextView>(R.id.cancellation_reason).text = getString(
            R.string.gate_cancellation_value,
            state.cancellationReason?.name ?: getString(R.string.gate_none),
        )

        actionButtons.forEach { (action, button) ->
            button.isEnabled = if (action == GateAction.CANCEL) {
                state.connected && state.gestureInFlight
            } else {
                state.controlsEnabled
            }
        }
        val settingsButton = findViewById<Button>(R.id.accessibility_settings)
        settingsButton.visibility = View.VISIBLE
        settingsButton.isEnabled = state.settingsGuidanceVisible

        if (currentFocus == null || currentFocus?.isEnabled == false) {
            if (state.connected) {
                actionButtons.getValue(GateAction.SHOW_CURSOR).requestFocus()
            } else {
                settingsButton.requestFocus()
            }
        }
    }

    private class AccessibilityGateRuntime : GateRuntime {
        private var observedService: TouchAccessibilityService? = null

        override val isConnected: Boolean
            get() = TouchAccessibilityService.connectedService != null

        override fun currentBounds(): DisplayBounds =
            TouchAccessibilityService.connectedService?.currentBounds() ?: DisplayBounds(1, 1)

        override fun showCursorAt(point: PointF) {
            TouchAccessibilityService.connectedService
                ?.showCursorAt(point.x.toInt(), point.y.toInt())
        }

        override fun moveCursorTo(point: PointF) {
            TouchAccessibilityService.connectedService
                ?.moveCursorTo(point.x.toInt(), point.y.toInt())
        }

        override fun submitGesture(spec: GestureSpec): SubmitResult =
            TouchAccessibilityService.connectedService?.submitGesture(spec)
                ?: SubmitResult.REJECTED

        override fun cancelGesture() {
            TouchAccessibilityService.connectedService?.cancelGesture()
        }

        override fun setGestureOutcomeListener(listener: ((GestureOutcome) -> Unit)?) {
            val service = TouchAccessibilityService.connectedService
            if (observedService !== service) {
                observedService?.setGestureOutcomeListener(null)
                observedService = service
            }
            service?.setGestureOutcomeListener(listener)
        }

    }

    private companion object {
        val CONFIRM_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        )
    }
}

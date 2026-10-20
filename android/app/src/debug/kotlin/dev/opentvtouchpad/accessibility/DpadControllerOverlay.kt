package dev.opentvtouchpad.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import dev.opentvtouchpad.R
import dev.opentvtouchpad.setup.GateAction
import dev.opentvtouchpad.setup.GateRuntime
import dev.opentvtouchpad.setup.GateUiState
import dev.opentvtouchpad.setup.GateViewModel

internal class DpadControllerOverlay(
    context: Context,
    private val windowManager: WindowManager,
    runtime: GateRuntime,
) : ServiceOverlay {
    private val controllerView = ControllerView(context, GateViewModel(runtime))
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

    override fun show() {
        if (attached) return
        windowManager.addView(controllerView, layoutParams)
        attached = true
        controllerView.start()
    }

    override fun remove() {
        if (!attached) return
        controllerView.stop()
        windowManager.removeViewImmediate(controllerView)
        attached = false
    }

    private class ControllerView(
        context: Context,
        private val viewModel: GateViewModel,
    ) : FrameLayout(context) {
        private val actionButtons: Map<GateAction, Button>
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
            focusOrder = actionButtons.values.toList() +
                findViewById<Button>(R.id.accessibility_settings)
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
            val action = actionButtons.entries.firstOrNull { it.value === findFocus() }?.key
                ?: return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) {
                viewModel.perform(action, isRepeat = event.repeatCount > 0)
            }
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
}

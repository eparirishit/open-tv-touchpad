package dev.opentvtouchpad.setup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.TextView
import dev.opentvtouchpad.R
import dev.opentvtouchpad.accessibility.TouchAccessibilityService

class GateActivity : Activity() {
    private lateinit var viewModel: GateViewModel
    private lateinit var actionButtons: Map<GateAction, Button>
    private lateinit var commandButtons: Map<Button, () -> Unit>
    private val serviceConnectionListener = { viewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gate)

        viewModel = GateViewModel(
            ServiceGateRuntime { TouchAccessibilityService.connectedService },
            ViewConfiguration.getTapTimeout().toLong(),
        )
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
            findViewById<Button>(R.id.manual_activated) to {
                viewModel.recordManualTargetResult(ManualTargetResult.ACTIVATED)
            },
            findViewById<Button>(R.id.manual_not_activated) to {
                viewModel.recordManualTargetResult(ManualTargetResult.NOT_ACTIVATED)
            },
            findViewById<Button>(R.id.launch_calibration) to {
                startActivity(Intent(this, CalibrationActivity::class.java))
            },
            findViewById<Button>(R.id.accessibility_settings) to ::openAccessibilitySettings,
        )
        commandButtons.forEach { (button, command) ->
            button.setOnClickListener { command() }
        }
    }

    private fun openAccessibilitySettings() {
        val primaryIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val handlerPackageName = packageManager.resolveActivity(
            primaryIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val action = SettingsActionPolicy.actionFor(handlerPackageName)

        SettingsNavigator { launchAction ->
            try {
                startActivity(Intent(launchAction))
            } catch (error: ActivityNotFoundException) {
                throw SettingsNavigator.ActivityUnavailableException(error)
            }
        }.open(action)
    }

    override fun onStart() {
        super.onStart()
        viewModel.observe(::render)
        TouchAccessibilityService.addConnectionListener(serviceConnectionListener)
        viewModel.refresh()
    }

    override fun onStop() {
        TouchAccessibilityService.removeConnectionListener(serviceConnectionListener)
        viewModel.detach()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in CONFIRM_KEYS) return super.dispatchKeyEvent(event)
        val action = actionButtons.entries.firstOrNull { it.value === currentFocus }?.key
        val command = (currentFocus as? Button)?.let(commandButtons::get)

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (action != null) {
                viewModel.perform(action, isRepeat = event.repeatCount > 0)
            } else if (event.repeatCount == 0) {
                command?.invoke() ?: return super.dispatchKeyEvent(event)
            }
        }
        return action != null || command != null
    }

    private fun render(state: GateUiState) {
        findViewById<TextView>(R.id.gate_status).setText(
            if (state.connected) R.string.gate_status_connected else R.string.gate_status_disabled,
        )
        renderProbeDiagnostics(state)

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

    private fun renderProbeDiagnostics(state: GateUiState) {
        val none = getString(R.string.gate_none)
        findViewById<TextView>(R.id.probe_mode).text = getString(
            R.string.probe_mode_value,
            state.tapProbeMode.name,
        )
        findViewById<TextView>(R.id.probe_admission).text = getString(
            R.string.probe_admission_value,
            state.lastDispatchResult?.name ?: none,
        )
        findViewById<TextView>(R.id.probe_callback).text = getString(
            R.string.probe_callback_value,
            state.callbackOutcome?.name ?: none,
        )
        findViewById<TextView>(R.id.probe_callback_duration).text = getString(
            R.string.probe_callback_duration_value,
            state.callbackDurationMillis?.toString() ?: none,
        )
        findViewById<TextView>(R.id.probe_display).text = state.bounds?.let {
            getString(R.string.probe_display_value, it.width, it.height)
        } ?: getString(R.string.probe_display_unavailable)
        findViewById<TextView>(R.id.probe_controller).text = getString(
            R.string.probe_controller_value,
            state.controllerState.name,
        )
        findViewById<TextView>(R.id.probe_manual_result).text = getString(
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
    }
}

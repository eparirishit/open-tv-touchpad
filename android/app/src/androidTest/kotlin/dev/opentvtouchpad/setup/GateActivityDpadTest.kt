package dev.opentvtouchpad.setup

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opentvtouchpad.accessibility.TouchAccessibilityService
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.PointF
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GateActivityDpadTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation by lazy {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    }
    private lateinit var activity: Activity

    @Before
    fun launchGate() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, GateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        instrumentation.waitForIdleSync()
    }

    @After
    fun finishGate() {
        instrumentation.runOnMainSync(activity::finish)
    }

    @Test
    fun everyGateActionHasDeterministicDownFocusOrder() {
        val actionNames = listOf(
            "show_cursor",
            "move_up",
            "move_down",
            "move_left",
            "move_right",
            "tap",
            "swipe_vertical",
            "swipe_horizontal",
            "drag_path",
            "cancel",
            "manual_activated",
            "manual_not_activated",
            "launch_calibration",
            "accessibility_settings",
        )
        val actionIds = actionNames.map(::requiredId)

        instrumentation.runOnMainSync {
            actionIds.forEach { activity.findViewById<Button>(it).isEnabled = true }
            activity.findViewById<View>(actionIds.first()).requestFocus()
        }

        actionIds.drop(1).forEach { expectedId ->
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
            instrumentation.waitForIdleSync()
            assertEquals(expectedId, activity.currentFocus?.id)
        }
    }

    @Test
    fun manualResultAndCalibrationLaunchAreClosedDpadControls() {
        val manualResult = activity.findViewById<TextView>(requiredId("probe_manual_result"))

        pressConfirm("manual_activated")
        assertTrue(manualResult.text.toString().endsWith("ACTIVATED"))

        pressConfirm("manual_not_activated")
        assertTrue(manualResult.text.toString().endsWith("NOT_ACTIVATED"))

        val monitor = instrumentation.addMonitor(CALIBRATION_ACTIVITY, null, false)
        try {
            pressConfirm("launch_calibration")
            val launched = instrumentation.waitForMonitorWithTimeout(monitor, 2_000L)
            assertTrue("Calibration activity did not launch", launched != null)
            assertEquals(CALIBRATION_ACTIVITY, launched?.componentName?.className)
            launched?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun activityDoesNotOfferUnsupportedM01ProbeModes() {
        listOf(
            "probe_mode_baseline",
            "probe_mode_system_timeout",
            "probe_mode_controller_handoff",
        ).forEach { idName ->
            assertEquals(null, activity.findViewById<View>(requiredId(idName)))
        }
    }

    @Test
    fun probeDiagnosticsExposeOnlyTheApprovedClosedFields() {
        val diagnosticIds = listOf(
            "probe_mode",
            "probe_admission",
            "probe_callback",
            "probe_callback_duration",
            "probe_display",
            "probe_controller",
            "probe_manual_result",
        ).map(::requiredId)

        assertEquals(7, diagnosticIds.toSet().size)
        diagnosticIds.forEach { id ->
            val value = activity.findViewById<TextView>(id).text.toString()
            assertFalse(value.contains("package", ignoreCase = true))
            assertFalse(value.contains("screen", ignoreCase = true))
            assertFalse(value.contains("coordinate", ignoreCase = true))
            assertFalse(value.contains("x="))
            assertFalse(value.contains("y="))
        }
    }

    @Test
    fun disabledAccessibilityShowsWorkingSettingsGuidance() {
        val status = activity.findViewById<TextView>(requiredId("gate_status"))
        val settingsButton = activity.findViewById<Button>(requiredId("accessibility_settings"))

        assertTrue(status.text.toString().contains("disabled", ignoreCase = true))
        assertEquals(View.VISIBLE, settingsButton.visibility)
        assertTrue(settingsButton.isEnabled)

        val primaryIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val handlerPackageName = activity.packageManager.resolveActivity(
            primaryIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val expectedAction = SettingsActionPolicy.actionFor(handlerPackageName)
        val monitor = instrumentation.addMonitor(
            IntentFilter(expectedAction),
            Instrumentation.ActivityResult(Activity.RESULT_OK, null),
            true,
        )
        try {
            instrumentation.runOnMainSync(settingsButton::performClick)
            instrumentation.waitForIdleSync()
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun inFlightGestureDisablesDispatchActionsAndFocusesCancel() {
        val render = GateActivity::class.java.getDeclaredMethod("render", GateUiState::class.java)
            .apply { isAccessible = true }
        val state = GateUiState(
            connected = true,
            bounds = DisplayBounds(width = 1920, height = 1080),
            cursor = PointF(960f, 540f),
            gestureInFlight = true,
            controlsEnabled = false,
            settingsGuidanceVisible = false,
        )

        instrumentation.runOnMainSync { render.invoke(activity, state) }

        assertTrue(
            listOf(
                "show_cursor",
                "move_up",
                "move_down",
                "move_left",
                "move_right",
                "tap",
                "swipe_vertical",
                "swipe_horizontal",
                "drag_path",
            ).all { !activity.findViewById<Button>(requiredId(it)).isEnabled },
        )
        val cancel = activity.findViewById<Button>(requiredId("cancel"))
        assertTrue(cancel.isEnabled)
        assertEquals(cancel.id, activity.currentFocus?.id)
    }

    @Test
    fun visibleActivityTracksLiveServiceConnectionAndStopsObservingAfterStop() {
        val resolver = instrumentation.targetContext.contentResolver
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val accessibilityEnabled = Settings.Secure.getInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        )

        try {
            setTouchServiceEnabled(false, enabledServices)
            waitForServiceConnection(connected = false)
            instrumentation.waitForIdleSync()
            assertDisconnectedGuidance()

            val connectionStartedAt = SystemClock.elapsedRealtime()
            setTouchServiceEnabled(true, enabledServices)
            waitForServiceConnection(connected = true)
            instrumentation.waitForIdleSync()

            assertTrue(
                "Live service connection did not update the visible gate promptly",
                SystemClock.elapsedRealtime() - connectionStartedAt < SERVICE_CONNECTION_TIMEOUT_MILLIS,
            )
            assertConnectedControls()

            instrumentation.runOnMainSync {
                instrumentation.callActivityOnPause(activity)
                instrumentation.callActivityOnStop(activity)
            }
            setTouchServiceEnabled(false, enabledServices)
            waitForServiceConnection(connected = false)
            instrumentation.waitForIdleSync()

            assertConnectedControls()

            instrumentation.runOnMainSync {
                instrumentation.callActivityOnRestart(activity)
                instrumentation.callActivityOnStart(activity)
                instrumentation.callActivityOnResume(activity)
            }
            instrumentation.waitForIdleSync()

            assertDisconnectedGuidance()
        } finally {
            writeSecureSetting(
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                enabledServices,
            )
            writeSecureSetting(
                Settings.Secure.ACCESSIBILITY_ENABLED,
                accessibilityEnabled.toString(),
            )
        }
    }

    private fun assertConnectedControls() {
        assertTrue(
            activity.findViewById<TextView>(requiredId("gate_status"))
                .text.toString().contains("connected", ignoreCase = true),
        )
        assertTrue(
            listOf(
                "show_cursor",
                "move_up",
                "move_down",
                "move_left",
                "move_right",
                "tap",
                "swipe_vertical",
                "swipe_horizontal",
                "drag_path",
            ).all { activity.findViewById<Button>(requiredId(it)).isEnabled },
        )
        assertTrue(!activity.findViewById<Button>(requiredId("cancel")).isEnabled)
        val settingsButton = activity.findViewById<Button>(requiredId("accessibility_settings"))
        assertEquals(View.VISIBLE, settingsButton.visibility)
        assertTrue(!settingsButton.isEnabled)
    }

    private fun assertDisconnectedGuidance() {
        assertTrue(
            activity.findViewById<TextView>(requiredId("gate_status"))
                .text.toString().contains("disabled", ignoreCase = true),
        )
        assertTrue(
            listOf(
                "show_cursor",
                "move_up",
                "move_down",
                "move_left",
                "move_right",
                "tap",
                "swipe_vertical",
                "swipe_horizontal",
                "drag_path",
                "cancel",
            ).all { !activity.findViewById<Button>(requiredId(it)).isEnabled },
        )
        assertTrue(activity.findViewById<Button>(requiredId("accessibility_settings")).isEnabled)
    }

    private fun setTouchServiceEnabled(enabled: Boolean, baseline: String?) {
        val component = ComponentName(
            instrumentation.targetContext,
            TouchAccessibilityService::class.java,
        ).flattenToString()
        val services = baseline.orEmpty().split(':').filter(String::isNotBlank).toMutableSet()
        if (enabled) {
            services += component
        } else {
            services -= component
        }
        writeSecureSetting(
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.takeIf { it.isNotEmpty() }?.joinToString(":"),
        )
        writeSecureSetting(
            Settings.Secure.ACCESSIBILITY_ENABLED,
            if (services.isEmpty()) "0" else "1",
        )
    }

    private fun writeSecureSetting(name: String, value: String?) {
        val operation = if (value == null) "delete" else "put"
        val suffix = value?.let { " $it" }.orEmpty()
        ParcelFileDescriptor.AutoCloseInputStream(
            uiAutomation.executeShellCommand(
                "settings $operation secure $name$suffix",
            ),
        ).use { it.readBytes() }
    }

    private fun waitForServiceConnection(connected: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + SERVICE_CONNECTION_TIMEOUT_MILLIS
        while ((TouchAccessibilityService.connectedService != null) != connected) {
            assertTrue(
                "Timed out waiting for service connected=$connected",
                SystemClock.elapsedRealtime() < deadline,
            )
            SystemClock.sleep(25)
        }
    }

    private fun requiredId(name: String): Int {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        assertNotEquals("Missing view id: $name", 0, id)
        return id
    }

    private fun pressConfirm(idName: String) {
        instrumentation.runOnMainSync {
            activity.findViewById<View>(requiredId(idName)).requestFocus()
        }
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.waitForIdleSync()
    }

    private companion object {
        const val CALIBRATION_ACTIVITY = "dev.opentvtouchpad.setup.CalibrationActivity"
        const val SERVICE_CONNECTION_TIMEOUT_MILLIS = 5_000L
    }
}

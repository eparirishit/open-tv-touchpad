package dev.opentvtouchpad.setup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opentvtouchpad.input.DisplayBounds
import dev.opentvtouchpad.input.PointF
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GateActivityDpadTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
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
    fun disabledAccessibilityShowsWorkingSettingsGuidance() {
        val status = activity.findViewById<TextView>(requiredId("gate_status"))
        val settingsButton = activity.findViewById<Button>(requiredId("accessibility_settings"))

        assertTrue(status.text.toString().contains("disabled", ignoreCase = true))
        assertEquals(View.VISIBLE, settingsButton.visibility)
        assertTrue(settingsButton.isEnabled)

        val monitor = instrumentation.addMonitor(
            IntentFilter(Settings.ACTION_ACCESSIBILITY_SETTINGS),
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

    private fun requiredId(name: String): Int {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        assertNotEquals("Missing view id: $name", 0, id)
        return id
    }
}

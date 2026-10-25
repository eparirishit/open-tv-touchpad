package dev.opentvtouchpad.setup

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalibrationActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: Activity

    @Before
    fun launchCalibration() {
        val activityClass = Class.forName(CALIBRATION_ACTIVITY) as Class<out Activity>
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        instrumentation.waitForIdleSync()
    }

    @After
    fun finishCalibration() {
        if (::activity.isInitialized) instrumentation.runOnMainSync(activity::finish)
    }

    @Test
    fun calibrationContainsExactlyNineFixedTargetsWithOnlyCenterIntended() {
        val targetGrid = activity.findViewById<ViewGroup>(requiredId("calibration_targets"))
        val targets = (0 until targetGrid.childCount).map(targetGrid::getChildAt)

        assertEquals(9, targets.size)
        assertEquals(9, targets.map(View::getId).toSet().size)
        assertEquals(1, targets.count { it.tag == "intended" })
        assertEquals(requiredId("calibration_target_center"), targets.single { it.tag == "intended" }.id)
        assertEquals(8, targets.count { it.tag == "other" })
    }

    @Test
    fun calibrationRecordsOnlyWhetherTheIntendedTargetReceivedTheTap() {
        val result = activity.findViewById<TextView>(requiredId("calibration_result"))

        instrumentation.runOnMainSync {
            activity.findViewById<View>(requiredId("calibration_target_1")).performClick()
        }
        assertEquals("NOT_ACTIVATED", result.text.toString())

        instrumentation.runOnMainSync {
            activity.findViewById<View>(requiredId("calibration_target_center")).performClick()
        }
        assertEquals("ACTIVATED", result.text.toString())
        assertFalse(result.text.toString().contains(','))
        assertFalse(result.text.toString().contains("x=", ignoreCase = true))
        assertFalse(result.text.toString().contains("y=", ignoreCase = true))
    }

    @Test
    fun calibrationAcceptsNoIntentControlledScreenOrTargetInput() {
        val activityClass = Class.forName(CALIBRATION_ACTIVITY) as Class<out Activity>
        instrumentation.runOnMainSync(activity::finish)
        instrumentation.waitForIdleSync()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("package", "external.example")
                .putExtra("action", "external.action")
                .putExtra("x", 12)
                .putExtra("y", 34),
        )
        instrumentation.waitForIdleSync()
        val targetGrid = activity.findViewById<ViewGroup>(requiredId("calibration_targets"))
        assertEquals(9, targetGrid.childCount)
        assertEquals(
            "NOT_RECORDED",
            activity.findViewById<TextView>(requiredId("calibration_result")).text.toString(),
        )
    }

    @Test
    fun startingAnotherProbeResetsThePreviousCalibrationResult() {
        val result = activity.findViewById<TextView>(requiredId("calibration_result"))
        instrumentation.runOnMainSync {
            activity.findViewById<View>(requiredId("calibration_target_center")).performClick()
        }
        assertEquals("ACTIVATED", result.text.toString())

        instrumentation.runOnMainSync(CalibrationActivity::resetResultForProbe)

        assertEquals("NOT_RECORDED", result.text.toString())
    }

    private fun requiredId(name: String): Int {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        assertNotEquals("Missing view id: $name", 0, id)
        return id
    }

    private companion object {
        const val CALIBRATION_ACTIVITY = "dev.opentvtouchpad.setup.CalibrationActivity"
    }
}

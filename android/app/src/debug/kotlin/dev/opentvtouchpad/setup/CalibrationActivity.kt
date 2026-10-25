package dev.opentvtouchpad.setup

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import dev.opentvtouchpad.R

class CalibrationActivity : Activity() {
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        result = findViewById(R.id.calibration_result)
        val targets = findViewById<ViewGroup>(R.id.calibration_targets)
        repeat(targets.childCount) { index ->
            targets.getChildAt(index).setOnClickListener { target ->
                result.setText(
                    if (target.id == R.id.calibration_target_center) {
                        R.string.probe_manual_activated
                    } else {
                        R.string.probe_manual_not_activated
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activeActivity = this
    }

    override fun onStop() {
        if (activeActivity === this) activeActivity = null
        super.onStop()
    }

    private fun resetResult() {
        result.setText(R.string.probe_manual_not_recorded)
    }

    companion object {
        private var activeActivity: CalibrationActivity? = null

        internal fun resetResultForProbe() {
            activeActivity?.resetResult()
        }
    }
}

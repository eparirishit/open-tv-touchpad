package dev.opentvtouchpad.setup

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import dev.opentvtouchpad.R

class GateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.gate_instructions)
                textSize = 28f
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(48, 48, 48, 48)
                requestFocus()
            },
        )
    }
}

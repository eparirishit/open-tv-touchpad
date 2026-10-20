package dev.opentvtouchpad.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.opentvtouchpad.R
import dev.opentvtouchpad.input.DisplayBounds
import kotlin.math.roundToInt

class CursorOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val displayBounds: () -> DisplayBounds,
) {
    private val cursorSize =
        (CURSOR_SIZE_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private val cursorView = View(context).apply {
        setBackgroundResource(R.drawable.cursor_ring)
        visibility = View.GONE
    }
    private val layoutParams = WindowManager.LayoutParams(
        cursorSize,
        cursorSize,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }
    private var attached = false

    fun showAt(x: Int, y: Int) {
        updatePosition(x, y)
        cursorView.visibility = View.VISIBLE
        if (attached) {
            windowManager.updateViewLayout(cursorView, layoutParams)
        } else {
            windowManager.addView(cursorView, layoutParams)
            attached = true
        }
    }

    fun moveTo(x: Int, y: Int) {
        showAt(x, y)
    }

    fun hide() {
        if (attached) cursorView.visibility = View.GONE
    }

    fun remove() {
        if (!attached) return
        windowManager.removeViewImmediate(cursorView)
        attached = false
    }

    private fun updatePosition(x: Int, y: Int) {
        val bounds = displayBounds()
        val maxX = (bounds.width - cursorSize).coerceAtLeast(0)
        val maxY = (bounds.height - cursorSize).coerceAtLeast(0)
        layoutParams.x = (x - cursorSize / 2).coerceIn(0, maxX)
        layoutParams.y = (y - cursorSize / 2).coerceIn(0, maxY)
    }

    private companion object {
        const val CURSOR_SIZE_DP = 32
    }
}

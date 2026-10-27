package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.BiConsumer;

/** Native event/scheduling adapter. Activity callbacks retain all input admission checks. */
public final class TouchpadView extends View {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final TouchpadGesture gesture;
    private final BiConsumer<Integer, Integer> move;
    private final Runnable tap;
    private Runnable pendingFrame;
    private int fill = Color.rgb(233,236,240), border = Color.rgb(220,224,229),
            dot = Color.rgb(220,224,229), text = Color.rgb(91,99,109);

    public void setPalette(int fill, int border, int dot, int text) {
        this.fill = fill;
        this.border = border;
        this.dot = dot;
        this.text = text;
        invalidate();
    }

    public TouchpadView(Context context, BiConsumer<Integer, Integer> move, Runnable tap) {
        super(context);
        this.move = move;
        this.tap = tap;
        density = getResources().getDisplayMetrics().density;
        gesture = new TouchpadGesture(ViewConfiguration.get(context).getScaledTouchSlop() / density,
                move, this::performClick);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Touchpad");
        setFocusable(true);
        setClickable(true);
        setEnabled(false);
    }

    public void cancelGesture() {
        if (gesture != null) gesture.cancel();
        clearFrame();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    public void setSensitivity(double sensitivity) {
        gesture.setSensitivity(sensitivity);
        cancelGesture();
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (gesture != null) gesture.setEnabled(enabled);
        if (!enabled) cancelGesture();
        invalidate();
    }

    private void clearFrame() {
        if (pendingFrame != null) handler.removeCallbacks(pendingFrame);
        pendingFrame = null;
    }

    private void scheduleFrame() {
        if (!gesture.hasPendingMotion()) { clearFrame(); return; }
        if (pendingFrame != null) return;
        long epoch = gesture.generation();
        pendingFrame = new Runnable() {
            @Override public void run() {
                if (pendingFrame != this || epoch != gesture.generation()) return;
                pendingFrame = null;
                gesture.frame(epoch, SystemClock.uptimeMillis());
                scheduleFrame();
            }
        };
        handler.postDelayed(pendingFrame, gesture.delayMillis(SystemClock.uptimeMillis()));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) { cancelGesture(); return true; }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            // Android DOWN starts a fresh stream, including after a system CANCEL.
            gesture.allUp();
            if (event.getPointerCount() == 1) {
                gesture.down(event.getPointerId(0), event.getX(0) / density,
                        event.getY(0) / density, event.getEventTime());
            } else {
                gesture.multiTouch();
            }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelGesture();
        } else if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP) {
            gesture.multiTouch();
            clearFrame();
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            int index = event.findPointerIndex(gesture.pointerId());
            if (index < 0) {
                gesture.cancel();
                clearFrame();
            } else if (action == MotionEvent.ACTION_MOVE) {
                gesture.move(event.getPointerId(index), event.getX(index) / density,
                        event.getY(index) / density);
                scheduleFrame();
            } else {
                clearFrame();
                gesture.up(event.getPointerId(index), event.getX(index) / density,
                        event.getY(index) / density, event.getEventTime());
            }
            if (action == MotionEvent.ACTION_UP) {
                gesture.allUp();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        return true;
    }

    @Override public boolean performClick() {
        if (!isEnabled()) return false;
        cancelGesture();
        super.performClick();
        tap.run();
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        if (!isEnabled()) return;
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.touchpad_move_left, "Move left"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.touchpad_move_right, "Move right"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.touchpad_move_up, "Move up"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.touchpad_move_down, "Move down"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Click"));
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) return performClick();
        int dx = 0, dy = 0;
        if (action == R.id.touchpad_move_left) dx = -16;
        else if (action == R.id.touchpad_move_right) dx = 16;
        else if (action == R.id.touchpad_move_up) dy = -16;
        else if (action == R.id.touchpad_move_down) dy = 16;
        else return super.performAccessibilityAction(action, arguments);
        if (!isEnabled()) return false;
        cancelGesture();
        move.accept(dx, dy);
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = density;
        float radius = 28 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(border);
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dot);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f - 20 * density, 28 * density, paint);
        paint.setColor(text);
        paint.setAlpha(isEnabled() ? 255 : 160);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        float baseline = getHeight() / 2f + 38 * density;
        canvas.drawText("Slide to move", getWidth() / 2f, baseline, paint);
        canvas.drawText("Tap to click", getWidth() / 2f, baseline + paint.getFontSpacing(), paint);
        paint.setAlpha(255);
    }

    @Override protected void onDetachedFromWindow() {
        cancelGesture();
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) cancelGesture();
        super.onWindowFocusChanged(hasWindowFocus);
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
        if (!gainFocus) cancelGesture();
        super.onFocusChanged(gainFocus, direction, previous);
    }
}

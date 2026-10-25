package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.function.BiConsumer;

/** Native event/scheduling adapter. Activity callbacks retain all input admission checks. */
public final class TouchpadView extends View {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final TouchpadGesture gesture;
    private Runnable pendingFrame;

    public TouchpadView(Context context, BiConsumer<Integer, Integer> move, Runnable tap) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        gesture = new TouchpadGesture(ViewConfiguration.get(context).getScaledTouchSlop() / density,
                move, () -> { performClick(); tap.run(); });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setEnabled(false);
    }

    public void cancelGesture() {
        if (gesture != null) gesture.cancel();
        clearFrame();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
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
        // Accessibility uses the separate directional/Click buttons; this never submits HID.
        super.performClick();
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = density;
        float radius = 28 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isEnabled() ? Color.rgb(32, 32, 32) : Color.rgb(40, 40, 40));
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(isEnabled() ? Color.rgb(145, 145, 145) : Color.rgb(105, 105, 105));
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isEnabled() ? Color.rgb(80, 80, 80) : Color.rgb(64, 64, 64));
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, 24 * density, paint);
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

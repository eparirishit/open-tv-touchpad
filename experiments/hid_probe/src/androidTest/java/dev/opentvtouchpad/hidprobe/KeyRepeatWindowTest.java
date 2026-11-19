package dev.opentvtouchpad.hidprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Holds a real D-pad button in an attached window, through the production KeyRepeat listener. */
@RunWith(AndroidJUnit4.class)
public class KeyRepeatWindowTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<TvCommand> sent = new ArrayList<>();
    private final List<String> holds = new ArrayList<>();
    private RemoteScreen screen;
    private KeyRepeat repeat;

    private Activity launch() {
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ProbeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            screen = new RemoteScreen(activity, (x, y) -> fail("Hold sent movement"), () -> fail("Hold clicked pad"));
            Handler handler = new Handler(Looper.getMainLooper());
            repeat = new KeyRepeat(new KeyRepeat.Scheduler() {
                @Override public void post(Runnable task, long delayMillis) { handler.postDelayed(task, delayMillis); }
                @Override public void cancel(Runnable task) { handler.removeCallbacks(task); }
            }, sent::add, command -> holds.add("start " + command), () -> holds.add("end"));
            for (TvCommand command : TvCommand.values()) {
                Button button = screen.tvButtons.get(command);
                button.setEnabled(true);
                button.setOnClickListener(v -> { if (!repeat.consumeClick()) sent.add(command); });
                repeat.attach(button, command, () -> { });
            }
            activity.setContentView(screen.root);
        });
        instrumentation.waitForIdleSync();
        return activity;
    }

    /** Real window input: the event travels window, root, scroll view, D-pad, button. */
    private void touch(Button button, int action, long downTime) {
        float[] at = new float[2];
        if (action == MotionEvent.ACTION_DOWN) {
            // Short landscape screens scroll; bring the button fully on screen first.
            instrumentation.runOnMainSync(() -> button.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, button.getWidth(), button.getHeight()), true));
            instrumentation.waitForIdleSync();
        }
        instrumentation.runOnMainSync(() -> {
            assertTrue("attached", button.isAttachedToWindow());
            assertTrue("enabled", button.isEnabled());
            assertTrue("laid out", button.getWidth() > 0 && button.isShown());
            int[] screenAt = new int[2];
            button.getLocationOnScreen(screenAt);
            at[0] = screenAt[0] + button.getWidth() / 2f;
            at[1] = screenAt[1] + button.getHeight() / 2f;
        });
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at[0], at[1], 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        instrumentation.sendPointerSync(event);
        event.recycle();
    }

    private int hold(TvCommand command, long millis) {
        Button button = screen.tvButtons.get(command);
        long down = SystemClock.uptimeMillis();
        touch(button, MotionEvent.ACTION_DOWN, down);
        SystemClock.sleep(millis);
        touch(button, MotionEvent.ACTION_UP, down);
        instrumentation.waitForIdleSync();
        int[] count = new int[1];
        instrumentation.runOnMainSync(() -> { count[0] = sent.size(); sent.clear(); });
        return count[0];
    }

    @Test public void heldArrowRepeatsAndHeldOkLongPressesInAnAttachedWindow() {
        Activity activity = launch();
        try {
            assertEquals("A quick tap is one press", 1, hold(TvCommand.DOWN, 50));
            int held = hold(TvCommand.DOWN, 1_100);
            assertTrue("A one-second hold repeats several times, got " + held, held >= 4);
            assertTrue("Repeats stay bounded by the interval, got " + held, held <= 7);
            assertEquals("A quick OK tap is one press", 1, hold(TvCommand.OK, 50));
            assertEquals("A held OK sends no tap", 0, hold(TvCommand.OK, 1_100));
            instrumentation.runOnMainSync(() -> assertEquals("Held OK long-presses once and releases on lift",
                    List.of("start OK", "end"), holds));
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
}

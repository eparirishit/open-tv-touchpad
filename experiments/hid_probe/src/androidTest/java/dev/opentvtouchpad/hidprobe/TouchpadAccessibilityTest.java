package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native views and fake callbacks only; the node test uses an attached Activity, never transport. */
@RunWith(AndroidJUnit4.class)
public class TouchpadAccessibilityTest {
    private static final String[] LABELS = {"Move left", "Move right", "Move up", "Move down", "Click"};
    private static final String[] RESULTS = {"move:-16,0", "move:16,0", "move:0,-16", "move:0,16", "tap"};

    private static final class Pad {
        final List<String> events = new ArrayList<>();
        final TouchpadView view;
        final long downTime = SystemClock.uptimeMillis();

        Pad() {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            view = new TouchpadView(context,
                    (dx, dy) -> events.add("move:" + dx + "," + dy), () -> events.add("tap"));
        }

        void touch(int action, float x, float y) {
            float density = view.getResources().getDisplayMetrics().density;
            MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    x * density, y * density, 0);
            try { assertTrue(view.onTouchEvent(event)); }
            finally { event.recycle(); }
        }
    }

    private static AccessibilityNodeInfo node(TouchpadView view) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        view.onInitializeAccessibilityNodeInfo(info);
        return info;
    }

    private static int action(TouchpadView view, String label) {
        AccessibilityNodeInfo info = node(view);
        try {
            for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
                if (label.contentEquals(action.getLabel() == null ? "" : action.getLabel())) {
                    return action.getId();
                }
            }
            throw new AssertionError("Missing action: " + label);
        } finally { info.recycle(); }
    }

    private static void assertNoInputActions(TouchpadView view, int[] ids) {
        AccessibilityNodeInfo info = node(view);
        try {
            assertFalse(info.isEnabled());
            for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
                for (int id : ids) assertNotEquals(id, action.getId());
            }
        } finally { info.recycle(); }
    }

    @Test public void enabledNodeExposesNamedBoundedActionsThroughExistingCallbacks() {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.app.Activity activity=instrumentation.startActivitySync(new android.content.Intent(
                instrumentation.getTargetContext(),ProbeActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Pad[] attached=new Pad[1];
        try {
            instrumentation.runOnMainSync(() -> {
                attached[0]=new Pad();
                activity.setContentView(attached[0].view);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                Pad pad=attached[0];
                assertTrue("Framework node metadata needs an attached view",pad.view.isAttachedToWindow());
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,pad.view.getImportantForAccessibility());
                assertEquals("Touchpad",pad.view.getContentDescription().toString());
                assertTrue(pad.view.isFocusable());
                assertTrue(pad.view.isClickable());
                assertFalse(pad.view.isEnabled());
                pad.view.setEnabled(true);
                AccessibilityNodeInfo info=node(pad.view);
                try {
                    assertTrue(info.isEnabled());
                    assertTrue(info.isFocusable());
                    assertTrue(info.isClickable());
                } finally { info.recycle(); }
                for(double sensitivity:new double[]{0.5,2}) {
                    pad.view.setSensitivity(sensitivity);
                    pad.events.clear();
                    for(int i=0;i<LABELS.length;i++) {
                        int id=action(pad.view,LABELS[i]);
                        if(i==4)assertEquals(AccessibilityNodeInfo.ACTION_CLICK,id);
                        assertTrue(pad.view.performAccessibilityAction(id,null));
                    }
                    assertEquals(Arrays.asList(RESULTS),pad.events);
                }
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    @Test public void disabledNodeHidesActionsAndRejectsStaleActionsAndDirectClicks() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Pad pad = new Pad();
            assertFalse(pad.view.performClick());
            pad.view.setEnabled(true);
            int[] ids = new int[LABELS.length];
            for (int i = 0; i < ids.length; i++) ids[i] = action(pad.view, LABELS[i]);
            pad.touch(MotionEvent.ACTION_DOWN, 0, 0);
            pad.touch(MotionEvent.ACTION_MOVE, 80, 0);
            pad.view.setEnabled(false);
            assertNoInputActions(pad.view, ids);
            for (int id : ids) assertFalse(pad.view.performAccessibilityAction(id, null));
            assertFalse(pad.view.performClick());
            pad.touch(MotionEvent.ACTION_UP, 80, 0);
            assertTrue(pad.events.isEmpty());
            pad.view.setEnabled(true);
            for (String label : LABELS) action(pad.view, label);
        });
    }

    @Test public void eachActionCancelsQueuedMotionAndTheOldTouchStream() {
        List<Pad> pads = new ArrayList<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String label : LABELS) {
                Pad pad = new Pad();
                pads.add(pad);
                pad.view.setEnabled(true);
                pad.touch(MotionEvent.ACTION_DOWN, 0, 0);
                pad.touch(MotionEvent.ACTION_MOVE, 80, 0);
                assertTrue(pad.view.performAccessibilityAction(action(pad.view, label), null));
            }
        });
        // Allow even a rate-limited frame to run, without injecting any device input.
        SystemClock.sleep(100);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < pads.size(); i++) {
                Pad pad = pads.get(i);
                assertEquals(Collections.singletonList(RESULTS[i]), pad.events);
                pad.touch(MotionEvent.ACTION_MOVE, 120, 0);
                pad.touch(MotionEvent.ACTION_UP, 120, 0);
                assertEquals(Collections.singletonList(RESULTS[i]), pad.events);
                pad.touch(MotionEvent.ACTION_DOWN, 0, 0);
                pad.touch(MotionEvent.ACTION_UP, 0, 0);
                assertEquals(Arrays.asList(RESULTS[i], "tap"), pad.events);
            }
        });
    }

    @Test public void eachActionCancelsAPendingTap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < LABELS.length; i++) {
                Pad pad = new Pad();
                pad.view.setEnabled(true);
                pad.touch(MotionEvent.ACTION_DOWN, 0, 0);
                assertTrue(pad.view.performAccessibilityAction(action(pad.view, LABELS[i]), null));
                pad.touch(MotionEvent.ACTION_UP, 0, 0);
                assertEquals(Collections.singletonList(RESULTS[i]), pad.events);
            }
        });
    }

    @Test public void ordinaryTouchAndNativeClickEachTapExactlyOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Pad pad = new Pad();
            pad.view.setEnabled(true);
            pad.touch(MotionEvent.ACTION_DOWN, 0, 0);
            pad.touch(MotionEvent.ACTION_UP, 0, 0);
            assertEquals(Collections.singletonList("tap"), pad.events);
            assertTrue(pad.view.performClick());
            assertEquals(Arrays.asList("tap", "tap"), pad.events);
            assertTrue(pad.view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null));
            assertEquals(Arrays.asList("tap", "tap", "tap"), pad.events);
        });
    }
}

package dev.opentvtouchpad.hidprobe;

import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real UI/platform focus checks only; never starts or pairs a transport. */
@RunWith(AndroidJUnit4.class)
public class DailyUseScreenTest {
    private void await(BooleanSupplier condition) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        long until = android.os.SystemClock.elapsedRealtime() + 2000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(10);
        } while (android.os.SystemClock.elapsedRealtime() < until);
        fail("UI/platform condition did not become true within the test bound");
    }

    @Test public void connectOnlyRemoteAndSetupFollowActualWindowFocus() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        ProbeActivity activity = (ProbeActivity) instrumentation.startActivitySync(
            new Intent(context, ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        var field = ProbeActivity.class.getDeclaredField("screen");
        field.setAccessible(true);
        RemoteScreen screen = (RemoteScreen) field.get(activity);
        try {
            await(() -> activity.hasWindowFocus() && screen.start.isEnabled());
            var controllerField = ProbeActivity.class.getDeclaredField("controller");
            controllerField.setAccessible(true);
            var bound = (RemoteConnectionService.LocalBinder) controllerField.get(activity);
            assertNotNull(bound);
            instrumentation.runOnMainSync(() -> {
                screen.showSetup(false);
                assertEquals("Normal remote has no diagnostic Start", View.GONE, screen.start.getVisibility());
                assertEquals(View.VISIBLE, screen.connect.getVisibility());
                screen.showSetup(true);
                assertEquals(View.VISIBLE, screen.start.getVisibility());
                assertEquals(View.GONE, screen.connect.getVisibility());
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            });
            await(() -> !activity.hasWindowFocus() && !screen.start.isEnabled());
            await(() -> bound.snapshot(0).destroyed());
            assertNull("Hidden Activity releases binding without waiting for a later render", controllerField.get(activity));
            instrumentation.runOnMainSync(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE));
            await(() -> activity.hasWindowFocus() && screen.start.isEnabled());
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.GONE, screen.stop.getVisibility());
                assertFalse(screen.touchpad.isEnabled());
                for (var button : screen.tvButtons.values()) assertFalse(button.isEnabled());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                activity.finish();
            });
        }
    }
}

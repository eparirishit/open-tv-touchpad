package dev.opentvtouchpad.hidprobe;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real bound component test; never starts a transport, pairs or sends input. */
@RunWith(AndroidJUnit4.class)
public class RemoteConnectionServiceTest {
    @Test public void snapshotRefreshDoesNotErasePermissionResult() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var activity = (ProbeActivity) instrumentation.startActivitySync(new Intent(
            instrumentation.getTargetContext(), ProbeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        var field = ProbeActivity.class.getDeclaredField("status");
        field.setAccessible(true);
        var label = (android.widget.TextView) field.get(activity);
        try {
            instrumentation.waitForIdleSync();
            // Allow the actual service binding/snapshot to settle before the permission callback.
            Thread.sleep(200);
            String[] result = {null};
            instrumentation.runOnMainSync(() -> {
                instrumentation.callActivityOnPause(activity);
                activity.onRequestPermissionsResult(1, new String[0], new int[0]);
                result[0] = label.getText().toString();
                instrumentation.callActivityOnResume(activity);
            });
            boolean granted = activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
            var screenField = ProbeActivity.class.getDeclaredField("screen");
            screenField.setAccessible(true);
            RemoteScreen screen = (RemoteScreen) screenField.get(activity);
            String expected = granted ? (screen.inputSurfaceVisible() ? "Permission granted. Tap Connect."
                : "Permission granted. Tap Start setup.") : "Nearby devices permission is required.";
            assertEquals(expected, result[0]);
            Thread.sleep(200);
            instrumentation.runOnMainSync(() -> assertEquals(result[0], label.getText().toString()));
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static Object runtime(IBinder binder) throws Exception {
        var outer = binder.getClass().getDeclaredField("this$0");
        outer.setAccessible(true);
        Object service = outer.get(binder);
        var field = RemoteConnectionService.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return field.get(service);
    }

    @Test public void replacementAttachmentRejectsOldScreenAndUnbindActuallyDestroysService() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        CountDownLatch[] connected = {new CountDownLatch(1)};
        AtomicReference<RemoteConnectionService.LocalBinder> control = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) {
                control.set((RemoteConnectionService.LocalBinder) binder);
                connected[0].countDown();
            }
            public void onServiceDisconnected(ComponentName name) {}
        };
        boolean[] bound = {false};
        try {
            instrumentation.runOnMainSync(() -> bound[0] = context.bindService(
                new Intent(context, RemoteConnectionService.class), connection, Context.BIND_AUTO_CREATE));
            assertTrue(bound[0]);
            assertTrue(connected[0].await(2, TimeUnit.SECONDS));
            Object previousRuntime = runtime(control.get());
            instrumentation.runOnMainSync(() -> {
                var binder = control.get();
                long old = binder.attach();
                binder.foreground(old, true);
                long current = binder.attach();
                binder.foreground(current, true);
                var before = binder.snapshot(current);
                binder.foreground(old, false);
                binder.detach(old);
                assertFalse(binder.request(old, RemoteRuntime.Request.SAVE_TV_ADDRESS, "invalid"));
                assertTrue(binder.snapshot(current).tvForget());
                assertEquals(before.status(), binder.snapshot(current).status());
                binder.detach(current);
                assertFalse(binder.snapshot(current).tvForget());
                assertFalse(binder.snapshot(current).ready());
                context.unbindService(connection);
                bound[0] = false;
            });
            boolean[] destroyed = {false};
            long end = android.os.SystemClock.elapsedRealtime() + 2000;
            do {
                instrumentation.runOnMainSync(() -> destroyed[0] = control.get().snapshot(0).destroyed());
                if (!destroyed[0]) Thread.sleep(10);
            } while (!destroyed[0] && android.os.SystemClock.elapsedRealtime() < end);
            assertTrue("Actual onDestroy must complete; unbind alone is not completion", destroyed[0]);
            connected[0] = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> bound[0] = context.bindService(
                new Intent(context, RemoteConnectionService.class), connection, Context.BIND_AUTO_CREATE));
            assertTrue(bound[0]);
            assertTrue(connected[0].await(2, TimeUnit.SECONDS));
            assertSame("Service recreation must not replace the process runtime holding late cleanup callbacks/latches",
                previousRuntime, runtime(control.get()));
        } finally {
            if (bound[0]) instrumentation.runOnMainSync(() -> context.unbindService(connection));
        }
    }
}

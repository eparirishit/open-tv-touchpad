package dev.opentvtouchpad.hidprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.function.Consumer;

/** Keeps native HID eligibility through system pairing UI; never sends input. */
public final class PairingGuardService extends Service {
    private static final String STOP = "dev.opentvtouchpad.hidprobe.STOP";
    private static final long WINDOW_MS = 300_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean alive;
    private long deadline;
    private String terminalReason = "Pairing service ended. Start a new run after teardown.";
    private Consumer<String> listener;
    private Runnable destroyed;

    public final class LocalBinder extends Binder {
        PairingGuardService service() { return PairingGuardService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        deadline = SystemClock.elapsedRealtime() + WINDOW_MS;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("hid-probe",
            "Temporary HID diagnostic", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, PairingGuardService.class).setAction(STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, ProbeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Notification notification = new Notification.Builder(this, "hid-probe")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Open TV HID diagnostic")
            .setContentText("Five-minute test. TV and mouse controls require the open probe screen.")
            .setContentIntent(PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(new Notification.Action.Builder(null, "Stop test", stopIntent).build())
            .setOngoing(true).build();
        try {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            alive = true;
            handler.postDelayed(() -> finish("Five-minute diagnostic window expired. Start a new run after teardown."), WINDOW_MS);
        } catch (SecurityException | IllegalStateException error) {
            terminalReason = "Pairing service could not start. No input enabled.";
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) finish("Stopped from notification.");
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return new LocalBinder(); }

    void attach(Consumer<String> listener) {
        this.listener = listener;
        if (!isAlive()) listener.accept(terminalReason);
    }

    boolean isAlive() {
        // Handler timers pause in deep sleep; check elapsed time again before UI/input use.
        if (alive && SystemClock.elapsedRealtime() >= deadline)
            finish("Five-minute diagnostic window expired. Start a new run after teardown.");
        return alive;
    }

    long deadlineMillis() { return deadline; }

    void onDestroyed(Runnable callback) { destroyed = callback; }

    void detach() { listener = null; }

    void finish(String reason) {
        if (!alive) return;
        alive = false;
        terminalReason = reason;
        handler.removeCallbacksAndMessages(null);
        Consumer<String> callback = listener;
        listener = null;
        if (callback != null) callback.accept(reason);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { finish("Probe task closed."); }

    @Override public void onDestroy() {
        finish("Pairing service ended. No input enabled.");
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (destroyed != null) {
            Runnable callback = destroyed;
            destroyed = null;
            callback.run();
        }
    }
}

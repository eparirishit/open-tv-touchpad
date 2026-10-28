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

/** Sole component owner of the diagnostic runtime and its foreground deadline. */
public final class RemoteConnectionService extends Service {
    private static final String STOP = "dev.opentvtouchpad.hidprobe.STOP";
    private static final long WINDOW_MS = 300_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean alive;
    private long deadline;
    private RemoteRuntime runtime;
    private boolean receiverRegistered;

    public final class LocalBinder extends Binder {
        long attach() { return runtime.attach(); }
        void foreground(long token, boolean foreground) { runtime.foreground(token, foreground); }
        void detach(long token) { runtime.detach(token); }
        void connectionLost(long token) { runtime.connectionLost(token); }
        RemoteRuntime.Snapshot snapshot(long token) { return runtime.snapshot(token); }
        boolean request(long token, RemoteRuntime.Request request, String value) { return runtime.request(token, request, value); }
        boolean makeVisible(long token) { return runtime.makeVisible(token); }
        void move(long token, int dx, int dy) { runtime.move(token, dx, dy); }
        void click(long token) { runtime.click(token); }
        void scroll(long token, MouseReport.Direction direction) { runtime.scroll(token, direction); }
        void tvCommand(long token, TvCommand command) { runtime.tvCommand(token, command); }
        void scrollTouchDown(long token) { runtime.scrollTouchDown(token); }
    }

    @Override public void onCreate() {
        super.onCreate();
        runtime = RemoteRuntime.forService(this);
        android.content.IntentFilter filter = new android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(runtime.receiver, filter, RECEIVER_EXPORTED);
        else registerReceiver(runtime.receiver, filter);
        receiverRegistered = true;
    }

    void beginWindow() {
        deadline = SystemClock.elapsedRealtime() + WINDOW_MS;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("hid-probe",
            "Temporary HID diagnostic", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, RemoteConnectionService.class).setAction(STOP);
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
            runtime.serviceStopped("Pairing service could not start. No input enabled.");
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) finish("Stopped from notification.");
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return new LocalBinder(); }

    boolean isAlive() {
        // Handler timers pause in deep sleep; check elapsed time again before UI/input use.
        if (alive && SystemClock.elapsedRealtime() >= deadline)
            finish("Five-minute diagnostic window expired. Start a new run after teardown.");
        return alive;
    }

    long deadlineMillis() { return deadline; }

    void finish(String reason) {
        if (!alive) { stopSelf(); return; }
        alive = false;
        handler.removeCallbacksAndMessages(null);
        runtime.serviceStopped(reason);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { finish("Probe task closed."); }

    @Override public void onDestroy() {
        finish("Pairing service ended. No input enabled.");
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (receiverRegistered) { unregisterReceiver(runtime.receiver); receiverRegistered = false; }
        runtime.destroyed();
    }
}

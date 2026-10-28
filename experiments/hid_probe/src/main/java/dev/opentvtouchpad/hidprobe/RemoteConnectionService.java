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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean alive;
    private long scheduledDeadline = -1;
    private String notificationText;
    private RemoteRuntime runtime;
    private final Runnable deadlineTask = () -> {
        if (alive && !runtime.sessionAlive()) finish("Session window expired. Both channels stopped.");
        else scheduleDeadline();
    };
    private boolean receiverRegistered;

    public final class LocalBinder extends Binder {
        long attach() { return runtime.attach(); }
        void foreground(long token, boolean foreground) { runtime.foreground(token, foreground); }
        void uiState(long token, boolean focused, boolean surfaceVisible) { runtime.uiState(token, focused, surfaceVisible); }
        void detach(long token) { runtime.detach(token); }
        void connectionLost(long token) { runtime.connectionLost(token); }
        RemoteRuntime.Snapshot snapshot(long token) { return runtime.snapshot(token); }
        boolean request(long token, RemoteRuntime.Request request, String value) { return runtime.request(token, request, value); }
        boolean makeVisible(long token) { return runtime.makeVisible(token); }
        void move(long token, long epoch, int dx, int dy) { runtime.move(token, epoch, dx, dy); }
        void click(long token, long epoch) { runtime.click(token, epoch); }
        void scroll(long token, long epoch, MouseReport.Direction direction) { runtime.scroll(token, epoch, direction); }
        void tvCommand(long token, long epoch, TvCommand command) { runtime.tvCommand(token, epoch, command); }
        void scrollTouchDown(long token, long epoch) { runtime.scrollTouchDown(token, epoch); }
    }

    @Override public void onCreate() {
        super.onCreate();
        runtime = RemoteRuntime.forService(this);
        android.content.IntentFilter filter = new android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(runtime.receiver, filter, RECEIVER_EXPORTED);
        else registerReceiver(runtime.receiver, filter);
        receiverRegistered = true;
    }

    void beginWindow() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("hid-probe",
            "Remote connection", NotificationManager.IMPORTANCE_LOW));
        try {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            alive = true;
            scheduleDeadline();
        } catch (SecurityException | IllegalStateException error) {
            runtime.serviceStopped("Connection service could not start. No input enabled.");
            stopSelf();
        }
    }

    private Notification notification() {
        notificationText = runtime.notificationText();
        Intent stop = new Intent(this, RemoteConnectionService.class).setAction(STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, ProbeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return new Notification.Builder(this, "hid-probe")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Open TV")
            .setContentText(notificationText)
            .setContentIntent(PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(new Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true).build();
    }

    void scheduleDeadline() {
        if (!alive) return;
        long deadline = runtime.deadlineMillis();
        if (scheduledDeadline != deadline) {
            scheduledDeadline = deadline;
            handler.removeCallbacks(deadlineTask);
            if (deadline != Long.MAX_VALUE)
                handler.postDelayed(deadlineTask, Math.max(0, deadline - SystemClock.elapsedRealtime()));
        }
        if (!java.util.Objects.equals(notificationText, runtime.notificationText()))
            getSystemService(NotificationManager.class).notify(1, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) finish("Stopped from notification.");
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return new LocalBinder(); }

    boolean isAlive() {
        // Handler timers pause in deep sleep; check elapsed time again before UI/input use.
        if (alive && !runtime.sessionAlive())
            finish("Session window expired. Both channels stopped.");
        return alive;
    }

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

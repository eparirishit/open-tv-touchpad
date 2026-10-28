package dev.opentvtouchpad.hidprobe;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.List;

/** UI attachment only; native lifetime and callbacks belong to the service/runtime. */
public final class ProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RemoteConnectionService.LocalBinder controller;
    private RemoteRuntime.Snapshot renderedSnapshot;
    private String displayedRuntimeStatus;
    private long attachment, codeRevision = -1, gestureRevision = -1;
    private boolean resumed, bound, closed, initialized;
    private List<Button> inputs;
    private TextView status, migrationNotice;
    private Button start, visible, confirm, connect;
    private RemoteScreen screen;
    private TouchpadView touchpad;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new RemoteScreen(this, this::move, this::click);
        status = screen.status;
        migrationNotice = screen.migrationNotice;
        start = screen.start;
        connect = screen.connect;
        visible = screen.visible;
        confirm = screen.confirm;
        inputs = screen.inputs;
        touchpad = screen.touchpad;
        setContentView(screen.root);
        status.setText("Opening remote…");
        bind(start, this::requestStart);
        bind(connect, () -> request(RemoteRuntime.Request.CONNECT, null));
        bind(visible, this::makeVisible);
        bind(confirm, () -> request(RemoteRuntime.Request.CONFIRM, null));
        bind(screen.forget, () -> request(RemoteRuntime.Request.FORGET, null));
        bind(screen.tvSaveAddress, () -> request(RemoteRuntime.Request.SAVE_TV_ADDRESS, screen.tvAddress.getText().toString()));
        bind(screen.tvPair, () -> request(RemoteRuntime.Request.PAIR_TV, screen.tvAddress.getText().toString()));
        bind(screen.tvConfirm, this::confirmTvCode);
        bind(screen.tvForget, () -> request(RemoteRuntime.Request.FORGET_TV, null));
        for (TvCommand command : TvCommand.values()) {
            Button button = screen.tvButtons.get(command);
            bind(button, () -> tvCommand(command));
            button.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchpad.cancelGesture();
                return false;
            });
        }
        for (MouseReport.Direction direction : MouseReport.Direction.values()) {
            Button control = screen.scrollButtons.get(direction);
            bind(control, () -> scroll(direction));
            control.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchpad.cancelGesture();
                    if (controller != null) controller.scrollTouchDown(attachment);
                }
                return false;
            });
        }
        bind(screen.stop, () -> request(RemoteRuntime.Request.STOP, null));
        bindController();
        handler.post(refresh);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) { releaseBinding(); return; }
            controller = (RemoteConnectionService.LocalBinder) binder;
            renderedSnapshot = null;
            attachment = controller.attach();
            codeRevision = gestureRevision = -1;
            controller.foreground(attachment, resumed);
            render();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            touchpad.cancelGesture();
            clearTvCode();
            if (controller != null) controller.connectionLost(attachment);
            controller = null;
            releaseBinding();
            status.setText("Pairing service lost. Native teardown unconfirmed.");
            disableControls();
        }
        @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    };

    // Poll immutable values on this Activity's handler; the service never stores a UI listener.
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (closed) return;
            render();
            handler.postDelayed(this, 100);
        }
    };

    private void bindController() {
        if (bound || closed) return;
        bound = bindService(new Intent(this, RemoteConnectionService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) status.setText("Pairing service unavailable.");
        disableControls();
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        unbindService(connection);
    }

    private void bind(Button button, Runnable action) {
        button.setOnClickListener(view -> {
            try { action.run(); }
            catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
                request(RemoteRuntime.Request.STOP, null);
                status.setText("Bluetooth operation failed. Check Nearby devices permission and Bluetooth.");
            }
        });
    }

    private boolean permissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStart() {
        if (!resumed) return;
        if (!permissions()) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE}, 1);
            return;
        }
        request(RemoteRuntime.Request.START, null);
    }

    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request, names, results);
        if (request == 1) status.setText(permissions() ? "Permission granted. Tap Start." : "Nearby devices permission is required.");
    }

    private void request(RemoteRuntime.Request request, String value) {
        touchpad.cancelGesture();
        if (request == RemoteRuntime.Request.STOP || request == RemoteRuntime.Request.FORGET_TV) clearTvCode();
        if (controller != null) controller.request(attachment, request, value);
        render();
    }

    private void makeVisible() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            request(RemoteRuntime.Request.STOP, null);
            return;
        }
        if (controller == null || !controller.makeVisible(attachment)) return;
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(intent);
    }

    private void confirmTvCode() {
        String code = screen.tvCode.getText().toString();
        clearTvCode();
        request(RemoteRuntime.Request.TV_CODE, code);
    }

    private void clearTvCode() { screen.tvCode.getText().clear(); }
    private void move(int dx, int dy) { if (controller != null) controller.move(attachment, dx, dy); render(); }
    private void click() { if (controller != null) controller.click(attachment); render(); }
    private void scroll(MouseReport.Direction direction) {
        touchpad.cancelGesture();
        if (controller != null) controller.scroll(attachment, direction);
        render();
    }
    private void tvCommand(TvCommand command) {
        touchpad.cancelGesture();
        if (controller != null) controller.tvCommand(attachment, command);
        render();
    }

    private void disableControls() {
        start.setEnabled(false); visible.setEnabled(false); confirm.setEnabled(false); connect.setEnabled(false);
        for (Button button : inputs) button.setEnabled(false);
        touchpad.setEnabled(false);
        screen.stop.setEnabled(false);
        screen.setConnected(false);
        screen.tvAddress.setEnabled(false); screen.tvSaveAddress.setEnabled(false);
        screen.tvPair.setEnabled(false); screen.tvCode.setEnabled(false); screen.tvConfirm.setEnabled(false);
        screen.tvForget.setEnabled(false);
        for (Button button : screen.tvButtons.values()) button.setEnabled(false);
    }

    private void render() {
        if (controller == null) return;
        RemoteRuntime.Snapshot snapshot = controller.snapshot(attachment);
        if (snapshot.equals(renderedSnapshot)) return;
        renderedSnapshot = snapshot;
        if (!java.util.Objects.equals(displayedRuntimeStatus, snapshot.status())) {
            displayedRuntimeStatus = snapshot.status();
            status.setText(displayedRuntimeStatus);
        }
        if (codeRevision != snapshot.codeRevision()) { codeRevision = snapshot.codeRevision(); clearTvCode(); }
        if (gestureRevision != snapshot.gestureRevision()) { gestureRevision = snapshot.gestureRevision(); touchpad.cancelGesture(); }
        if (!initialized) {
            initialized = true;
            screen.showSetup(snapshot.setup());
            if (snapshot.tvAddress() != null) screen.tvAddress.setText(snapshot.tvAddress());
        }
        migrationNotice.setVisibility(snapshot.migration() ? View.VISIBLE : View.GONE);
        start.setEnabled(snapshot.start()); visible.setEnabled(snapshot.visible());
        confirm.setEnabled(snapshot.confirm()); connect.setEnabled(snapshot.connect());
        for (Button button : inputs) button.setEnabled(snapshot.ready());
        touchpad.setEnabled(snapshot.ready());
        screen.stop.setEnabled(snapshot.ready()); screen.setConnected(snapshot.ready());
        screen.tvAddress.setEnabled(snapshot.tvAddressEnabled()); screen.tvSaveAddress.setEnabled(snapshot.tvAddressEnabled());
        screen.tvPair.setEnabled(snapshot.tvPair());
        screen.tvCode.setEnabled(snapshot.tvCode()); screen.tvConfirm.setEnabled(snapshot.tvCode());
        screen.tvForget.setEnabled(snapshot.tvForget());
        for (Button button : screen.tvButtons.values()) button.setEnabled(snapshot.tvReady());
        screen.tvStatus.setText(snapshot.tvStatus()); screen.setTvConnected(snapshot.tvConnected());
        if (snapshot.releaseBinding()) releaseBinding();
        if (snapshot.destroyed()) {
            controller = null;
            releaseBinding();
            bindController();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (controller != null) controller.foreground(attachment, true);
        else bindController();
        render();
    }

    @Override protected void onPause() {
        clearTvCode();
        touchpad.cancelGesture();
        resumed = false;
        if (controller != null) controller.foreground(attachment, false);
        render();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && touchpad != null) touchpad.cancelGesture();
    }

    @Override protected void onDestroy() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        touchpad.cancelGesture();
        clearTvCode();
        if (controller != null) controller.detach(attachment);
        controller = null;
        releaseBinding();
        super.onDestroy();
    }
}

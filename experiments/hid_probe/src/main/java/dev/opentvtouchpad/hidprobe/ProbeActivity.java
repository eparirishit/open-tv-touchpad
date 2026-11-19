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
    private ServiceConnection connection;
    private RemoteConnectionService.LocalBinder controller;
    private RemoteRuntime.Snapshot renderedSnapshot;
    private String displayedRuntimeStatus;
    private long attachment, inputEpoch, codeRevision = -1, gestureRevision = -1;
    private boolean resumed, bound, closed, initialized;
    private List<Button> inputs;
    private TextView status;
    private Button connect;
    private SetupState.Action pendingPermissionAction;
    private long permissionActionAt, discoveryGeneration;
    private boolean permissionActionReady;
    private Integer pendingDiscoveryResult;
    // This marker survives deliberate binder handoff, but the UI can revoke it without a live service.
    private java.util.concurrent.atomic.AtomicBoolean setupVisit = new java.util.concurrent.atomic.AtomicBoolean(true);
    private RemoteScreen screen;
    private TouchpadView touchpad;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new RemoteScreen(this, this::move, this::click);
        status = screen.status;
        connect = screen.connect;
        inputs = screen.inputs;
        touchpad = screen.touchpad;
        setContentView(screen.root);
        screen.setPageListener(this::syncUiState);
        status.setText("Opening remote…");
        bind(connect, this::connectRemote);
        screen.setupView.setActionListener(this::setupAction);
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
                    if (controller != null) controller.scrollTouchDown(attachment, inputEpoch);
                }
                return false;
            });
        }
        bind(screen.stop, () -> request(RemoteRuntime.Request.STOP, null));
        bindController();
        handler.post(refresh);
    }

    private ServiceConnection newConnection() {
        return new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (!bound || connection != this) return;
                if (closed || !resumed || !hasWindowFocus()) { releaseBinding(); return; }
                controller = (RemoteConnectionService.LocalBinder) binder;
                renderedSnapshot = null;
                attachment = controller.attach(setupVisit);
                codeRevision = gestureRevision = -1;
                controller.foreground(attachment, resumed);
                syncUiState();
                render();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if (!bound || connection != this) return;
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
    }

    // Poll immutable values on this Activity's handler; the service never stores a UI listener.
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (closed || !resumed || !hasWindowFocus()) return;
            render();
            handler.postDelayed(this, 100);
        }
    };

    private void bindController() {
        if (bound || closed || !resumed || !hasWindowFocus()) return;
        if (!setupVisit.get()) setupVisit = new java.util.concurrent.atomic.AtomicBoolean(true);
        connection = newConnection();
        bound = bindService(new Intent(this, RemoteConnectionService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) status.setText("Pairing service unavailable.");
        pauseControls();
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        ServiceConnection previous = connection;
        connection = null;
        unbindService(previous);
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

    private void syncUiState() {
        renderedSnapshot = null;
        if (controller != null) controller.uiState(attachment, hasWindowFocus(), screen.inputSurfaceVisible());
    }

    private void connectRemote() {
        if (!resumed) return;
        if (!permissions()) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE}, 1);
            return;
        }
        screen.showSetup(false);
        syncUiState();
        request(RemoteRuntime.Request.REMOTE_CONNECT, null);
    }

    private void setupAction(SetupState.Action action, String value) {
        touchpad.cancelGesture();
        clearTvCodeUnlessSubmitting(action);
        if (action == SetupState.Action.APPEARANCE) { screen.showAppearance(); syncUiState(); return; }
        if (action == SetupState.Action.BATTERY_SETTINGS) {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getPackageName())));
            return;
        }
        if (action == SetupState.Action.OPEN_REMOTE) {
            if (controller != null) controller.setupAction(attachment, action, null);
            screen.showSetup(false); syncUiState(); render(); return;
        }
        if (action == SetupState.Action.BACK && renderedSnapshot != null
                && (renderedSnapshot.wizard().page() == SetupState.Page.OVERVIEW
                    || renderedSnapshot.wizard().page() == SetupState.Page.MANAGE)) {
            screen.showSetup(false); syncUiState(); render(); return;
        }
        if (!resumed || !hasWindowFocus()) return;
        if ((action == SetupState.Action.BT_PAIR || action == SetupState.Action.PAIR_TV) && !permissions()) {
            pendingPermissionAction = action;
            permissionActionAt = android.os.SystemClock.elapsedRealtime();
            permissionActionReady = false;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}, 1);
            return;
        }
        if (controller != null) controller.setupAction(attachment, action, value);
        if (action == SetupState.Action.CONFIRM_TV) clearTvCode();
        render();
    }

    private void clearTvCodeUnlessSubmitting(SetupState.Action action) {
        if (action != SetupState.Action.CONFIRM_TV) clearTvCode();
    }

    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request, names, results);
        if (request == 1) {
            permissionActionReady = permissions() && pendingPermissionAction != null;
            if (!permissions()) pendingPermissionAction = null;
            status.setText(permissions() ? (screen.inputSurfaceVisible() ? "Permission granted. Tap Connect." : "Permission granted. Continue setup.")
                : "Nearby devices permission is required.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 2 && discoveryGeneration != 0) pendingDiscoveryResult = result;
    }

    private void continueSetupUi(SetupState.Snapshot wizard) {
        if (controller == null || !resumed || !hasWindowFocus() || screen.inputSurfaceVisible()) return;
        if (permissionActionReady && pendingPermissionAction != null) {
            SetupState.Action pending = pendingPermissionAction;
            pendingPermissionAction = null; permissionActionReady = false;
            if (permissions() && android.os.SystemClock.elapsedRealtime() - permissionActionAt <= 300000
                    && ((pending == SetupState.Action.BT_PAIR && wizard.page() == SetupState.Page.BT_START)
                        || (pending == SetupState.Action.PAIR_TV && wizard.page() == SetupState.Page.TV_START)))
                controller.setupAction(attachment, pending, null);
        }
        if (pendingDiscoveryResult != null) {
            int result = pendingDiscoveryResult; pendingDiscoveryResult = null;
            long generation = discoveryGeneration; discoveryGeneration = 0;
            controller.discoveryResult(attachment, generation, result);
        }
        if (wizard.requestDiscoverable() && controller.takeDiscoverabilityRequest(attachment, wizard.generation())) {
            discoveryGeneration = wizard.generation();
            try {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
                startActivityForResult(intent, 2);
            } catch (SecurityException | IllegalStateException | android.content.ActivityNotFoundException failure) {
                controller.discoveryResult(attachment, discoveryGeneration, 0);
                discoveryGeneration = 0;
            }
        }
    }

    @Override protected void onStop() {
        pendingPermissionAction = null;
        permissionActionReady = false;
        super.onStop();
    }

    private void request(RemoteRuntime.Request request, String value) {
        touchpad.cancelGesture();
        if (request == RemoteRuntime.Request.STOP || request == RemoteRuntime.Request.FORGET_TV) {
            inputEpoch = 0;
            clearTvCode();
        }
        if (controller != null) controller.request(attachment, request, value);
        render();
    }

    private void clearTvCode() { screen.setupView.clearCode(); }
    private void move(int dx, int dy) { if (controller != null) controller.move(attachment, inputEpoch, dx, dy); render(); }
    private void click() { if (controller != null) controller.click(attachment, inputEpoch); render(); }
    private void scroll(MouseReport.Direction direction) {
        touchpad.cancelGesture();
        if (controller != null) controller.scroll(attachment, inputEpoch, direction);
        render();
    }
    private void tvCommand(TvCommand command) {
        touchpad.cancelGesture();
        if (controller != null) controller.tvCommand(attachment, inputEpoch, command);
        render();
    }

    private void disableControls() {
        pauseControls();
        screen.stop.setEnabled(false);
        screen.setConnected(false);
    }

    private void pauseControls() {
        inputEpoch = 0;
        connect.setEnabled(false);
        for (Button button : inputs) button.setEnabled(false);
        touchpad.setEnabled(false);
        screen.setupView.pauseControls();
        for (Button button : screen.tvButtons.values()) button.setEnabled(false);
    }

    private void render() {
        if (controller == null || !resumed || !hasWindowFocus()) return;
        RemoteRuntime.Snapshot snapshot = controller.snapshot(attachment);
        if (snapshot.equals(renderedSnapshot)) { continueSetupUi(snapshot.wizard()); return; }
        renderedSnapshot = snapshot;
        inputEpoch = snapshot.inputEpoch();
        if (!java.util.Objects.equals(displayedRuntimeStatus, snapshot.status())) {
            displayedRuntimeStatus = snapshot.status();
            status.setText(displayedRuntimeStatus);
        }
        if (codeRevision != snapshot.codeRevision()) { codeRevision = snapshot.codeRevision(); clearTvCode(); }
        if (gestureRevision != snapshot.gestureRevision()) { gestureRevision = snapshot.gestureRevision(); touchpad.cancelGesture(); }
        if (!initialized) {
            initialized = true;
            screen.showSetup(snapshot.setup());
        }
        connect.setEnabled(snapshot.connect());
        for (Button button : inputs) button.setEnabled(snapshot.ready());
        touchpad.setEnabled(snapshot.ready());
        screen.stop.setEnabled(snapshot.connected()); screen.setConnected(snapshot.connected());
        screen.setupView.render(snapshot.wizard());
        for (Button button : screen.tvButtons.values()) button.setEnabled(snapshot.tvReady());
        screen.tvStatus.setText(snapshot.tvStatus()); screen.setTvConnected(snapshot.tvConnected());
        continueSetupUi(snapshot.wizard());
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
        touchpad.cancelGesture();
        if (controller != null) controller.foreground(attachment, true);
        else bindController();
        syncUiState();
        render();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        setupVisit.set(false);
        resumed = false;
        inputEpoch = 0;
        if (controller != null) controller.foreground(attachment, false);
        clearTvCode();
        touchpad.cancelGesture();
        screen.root.cancelPendingInputEvents();
        handler.removeCallbacks(refresh);
        pauseControls();
        releaseBinding();
        controller = null;
        attachment = 0;
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) { setupVisit.set(false); inputEpoch = 0; }
        if (screen != null && (!hasFocus || resumed)) syncUiState();
        if (!hasFocus) {
            handler.removeCallbacks(refresh);
            if (touchpad != null) {
                touchpad.cancelGesture();
                screen.root.cancelPendingInputEvents();
                clearTvCode();
                pauseControls();
            }
            releaseBinding();
            controller = null;
            attachment = 0;
        } else if (resumed) {
            if (controller == null) bindController();
            render();
            handler.removeCallbacks(refresh);
            handler.post(refresh);
        }
    }

    @Override protected void onDestroy() {
        setupVisit.set(false);
        closed = true;
        handler.removeCallbacksAndMessages(null);
        inputEpoch = 0;
        if (controller != null) controller.detach(attachment);
        touchpad.cancelGesture();
        screen.root.cancelPendingInputEvents();
        clearTvCode();
        controller = null;
        releaseBinding();
        super.onDestroy();
    }
}

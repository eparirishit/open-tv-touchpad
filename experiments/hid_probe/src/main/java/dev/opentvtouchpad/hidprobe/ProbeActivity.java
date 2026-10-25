package dev.opentvtouchpad.hidprobe;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Foreground-only diagnostic. No network, automatic pairing, or persisted trust. */
public final class ProbeActivity extends Activity {
    // Report 1: three buttons plus signed relative X/Y; all reports are three bytes.
    private static final byte[] DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x02, (byte) 0xa1, 0x01, (byte) 0x85, 0x01,
        0x09, 0x01, (byte) 0xa1, 0x00, 0x05, 0x09, 0x19, 0x01,
        0x29, 0x03, 0x15, 0x00, 0x25, 0x01, (byte) 0x95, 0x03,
        0x75, 0x01, (byte) 0x81, 0x02, (byte) 0x95, 0x01, 0x75, 0x05,
        (byte) 0x81, 0x03, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31,
        0x15, (byte) 0x81, 0x25, 0x7f, 0x75, 0x08, (byte) 0x95, 0x02,
        (byte) 0x81, 0x06, (byte) 0xc0, (byte) 0xc0
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ProbeSession session = new ProbeSession();
    private final List<Button> inputs = new ArrayList<>();
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice candidate;
    private BluetoothDevice connected;
    private long generation;
    private boolean started;
    private boolean resumed;
    private boolean guardBound;
    private PairingGuardService guard;
    private boolean receiverRegistered;
    private boolean registrationRequested;
    private boolean cleanupRequested;
    private TextView status;
    private Button start;
    private Button visible;
    private Button confirm;
    private LinearLayout layout;
    private final Runnable registrationDeadline = () -> {
        if (session.active(generation)) stop("Registration timed out. Close and reopen for another test.");
    };
    private final Runnable connectionDeadline = () -> {
        if (session.active(generation) && connected == null) stop("Connection timed out. No input enabled.");
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            layout.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return insets;
        });
        text("Open TV HID Probe", 24);
        text("Five-minute diagnostic. Pairing prompts may cover this screen during setup. Use the physical TV remote to pair. "
            + "Compare the same digits on BOTH screens. A generic Pair prompt is not enough.", 16);
        status = text("Ready. Bluetooth must already be on.", 18);
        start = button("1. Start HID registration", this::requestStart);
        visible = button("2. Make visible for 120 seconds", this::makeVisible);
        confirm = button("3. I compared matching codes on BOTH screens — connect", this::confirmAndConnect);
        text("Only confirm after checking the intended XGIMI and Nord display the same code. "
            + "No code is entered here. Existing bonds alone cannot enable input.", 16);
        inputs.add(button("Move left", () -> move(-16, 0)));
        inputs.add(button("Move right", () -> move(16, 0)));
        inputs.add(button("Move up", () -> move(0, -16)));
        inputs.add(button("Move down", () -> move(0, 16)));
        inputs.add(button("Click once", this::click));
        button("STOP / Disconnect", () -> stop("Stopped. Close and reopen for another test."));
        button("Close probe", () -> { stop("Stopped."); finish(); });
        updateControls();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        layout.addView(view);
        return view;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(view -> {
            try { action.run(); }
            catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
                stop("Bluetooth operation failed. Check Nearby devices permission and Bluetooth.");
            }
        });
        layout.addView(button);
        return button;
    }

    private void requestStart() {
        if (started) return;
        if (!permissions()) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE}, 1);
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Turn Bluetooth on first, then Start.");
            return;
        }
        started = true;
        generation = session.start();
        session.foreground(generation, resumed);
        cleanupRequested = false;
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        receiverRegistered = true;
        status.setText("Starting timed pairing service…");
        updateControls();
        Intent intent = new Intent(this, PairingGuardService.class);
        startForegroundService(intent);
        guardBound = bindService(intent, guardConnection, Context.BIND_AUTO_CREATE);
        if (!guardBound) { stop("Pairing service unavailable."); return; }
        handler.postDelayed(registrationDeadline, 5000);
    }

    private final ServiceConnection guardConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            PairingGuardService incoming = ((PairingGuardService.LocalBinder) binder).service();
            if (cleanupRequested || !session.active(generation)) {
                incoming.finish("Probe already stopped."); return;
            }
            guard = incoming;
            incoming.attach(ProbeActivity.this::stop);
            if (!active()) return;
            try {
                status.setText("Requesting HID service…");
                if (!adapter.getProfileProxy(ProbeActivity.this, service, BluetoothProfile.HID_DEVICE))
                    stop("HID profile unavailable.");
            } catch (SecurityException | IllegalStateException error) { stop("HID service request failed."); }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            guard = null;
            stop("Pairing service lost. No input enabled.");
        }
        @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    };

    private boolean active() {
        return session.active(generation) && guard != null && guard.isAlive();
    }

    private boolean permissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request, names, results);
        if (request == 1) status.setText(permissions() ? "Permission granted. Tap Start." : "Nearby devices permission is required.");
    }

    private final BluetoothProfile.ServiceListener service = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (!active()) { adapter.closeProfileProxy(profile, proxy); return; }
            hid = (BluetoothHidDevice) proxy;
            try {
                registrationRequested = hid.registerApp(new BluetoothHidDeviceAppSdpSettings(
                    "Open TV HID Probe", "Disposable manual mouse test", "Open TV Touchpad",
                    BluetoothHidDevice.SUBCLASS1_MOUSE, DESCRIPTOR), null, null,
                    getMainExecutor(), callback);
                if (!registrationRequested) stop("HID registration request rejected. Another HID app may be active.");
            } catch (SecurityException | IllegalStateException error) { stop("HID registration failed."); }
        }
        @Override public void onServiceDisconnected(int profile) {
            hid = null;
            stop("HID service lost. No input enabled.");
        }
    };

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice plugged, boolean registered) {
            if (!active()) {
                // Registration can finish after Stop; unregister that late registration too.
                if (registered && hid != null) {
                    try { hid.unregisterApp(); } catch (SecurityException | IllegalStateException ignored) { }
                }
                return;
            }
            session.registered(generation, registered);
            handler.removeCallbacks(registrationDeadline);
            if (!registered) { stop("HID registration ended. No input enabled."); return; }
            status.setText("HID registered. Make visible, then pair from the XGIMI.");
            updateControls();
        }
        @Override public void onConnectionStateChanged(BluetoothDevice peer, int state) {
            try {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (!active() || !session.connected(generation, peer.getAddress(), true)) {
                        if (hid != null) hid.disconnect(peer);
                        return;
                    }
                    connected = peer;
                    handler.removeCallbacks(connectionDeadline);
                    if (!hid.sendReport(peer, 1, new byte[3])) { stop("Neutral report failed."); return; }
                    status.setText("Mouse connected. Try one movement. Report delivery does not prove TV action.");
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    // Includes failure before the first accepted CONNECTED callback.
                    if (session.connected(generation, peer.getAddress(), false))
                        stop("Projector disconnected. No input enabled.");
                }
                updateControls();
            } catch (SecurityException | IllegalStateException error) { stop("Bluetooth connection callback failed."); }
        }
        @Override public void onGetReport(BluetoothDevice peer, byte type, byte id, int bufferSize) {
            try {
                if (!ready() || !connected.equals(peer)) return;
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id == 1 && (bufferSize == 0 || bufferSize >= 3)) {
                    if (!hid.replyReport(peer, type, id, new byte[3])) stop("Report reply failed.");
                } else hid.reportError(peer, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
            } catch (SecurityException | IllegalStateException error) { stop("Report request failed."); }
        }
        @Override public void onSetProtocol(BluetoothDevice peer, byte protocol) {
            if (connected != null && connected.equals(peer) && protocol != BluetoothHidDevice.PROTOCOL_REPORT_MODE)
                stop("Host requested unsupported boot protocol. No input enabled.");
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice peer) {
            if (connected != null && connected.equals(peer)) stop("Host unplugged mouse. No input enabled.");
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!active()) return;
            try {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON)
                        stop("Bluetooth turned off. No input enabled.");
                    return;
                }
                BluetoothDevice peer = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (peer == null) return;
                if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                    int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                    if (!session.observePairing(generation, peer.getAddress(), variant)) {
                        if (!session.active(generation)) { stop("Pairing method changed. No input enabled."); return; }
                        status.setText("Pairing method/peer not eligible. Cancel if both screens do not show matching numbers.");
                    } else {
                        candidate = peer;
                        status.setText("Numeric Comparison requested. Compare digits on BOTH screens before accepting. "
                            + "Then confirm below. Peer label: " + peer.getName());
                    }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())
                        && candidate != null && candidate.equals(peer)) {
                    int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    if (bond == BluetoothDevice.BOND_BONDED) {
                        session.bonded(generation, peer.getAddress());
                        status.setText("Bond created. Confirm below ONLY if you compared matching codes on both screens.");
                        updateControls();
                    } else if (bond == BluetoothDevice.BOND_NONE) stop("Pairing cancelled or bond removed. No input enabled.");
                }
            } catch (SecurityException | IllegalStateException error) { stop("Bluetooth pairing check failed."); }
        }
    };

    private void makeVisible() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable."); return;
        }
        if (!active() || !resumed || !session.isRegistered(generation)) return;
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(intent);
    }

    private void confirmAndConnect() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable. No input enabled."); return;
        }
        if (!active() || candidate == null || hid == null || !session.confirmComparison(generation)
                || !candidate.getAddress().equals(session.eligiblePeer())) return;
        confirm.setEnabled(false);
        visible.setEnabled(false);
        status.setText("Connecting to the compared projector…");
        if (!hid.connect(candidate)) { stop("Mouse connection request rejected."); return; }
        handler.postDelayed(connectionDeadline, 10000);
    }

    private void move(int dx, int dy) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable. No input enabled."); return;
        }
        if (!ready()) return;
        if (!hid.sendReport(connected, 1, new byte[] {0, (byte) dx, (byte) dy})) stop("Mouse movement report failed.");
    }

    private void click() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable. No input enabled."); return;
        }
        if (!ready()) return;
        // No held gesture or delayed callback: release immediately in the same UI action.
        boolean pressed = false;
        boolean released;
        try {
            pressed = hid.sendReport(connected, 1, new byte[] {1, 0, 0});
        } finally {
            released = hid.sendReport(connected, 1, new byte[3]);
        }
        if (!pressed || !released) stop("Click report failed. Disconnect requested.");
    }

    private boolean ready() {
        return active() && resumed && hid != null && connected != null
            && session.canSend(generation, connected.getAddress());
    }

    private void updateControls() {
        start.setEnabled(!started);
        visible.setEnabled(resumed && session.isRegistered(generation) && session.eligiblePeer() == null
            && hid != null && connected == null);
        confirm.setEnabled(session.canConfirm(generation));
        boolean ready = false;
        try { ready = ready(); } catch (SecurityException ignored) { }
        for (Button button : inputs) button.setEnabled(ready);
    }

    private void stop(String message) {
        // Preserve the first terminal reason; later lifecycle callbacks must not hide it.
        if (cleanupRequested) return;
        cleanupRequested = true;
        session.stop();
        handler.removeCallbacksAndMessages(null);
        if (status != null) { status.setText(message); updateControls(); }
        if (hid != null) {
            if (connected != null) {
                try { hid.sendReport(connected, 1, new byte[3]); } catch (SecurityException | IllegalStateException ignored) { }
                try { hid.disconnect(connected); } catch (SecurityException | IllegalStateException ignored) { }
            }
            try { if (registrationRequested) hid.unregisterApp(); } catch (SecurityException | IllegalStateException ignored) { }
        }
        connected = null;
        candidate = null;
        if (guard != null) {
            PairingGuardService old = guard;
            guard = null;
            old.detach();
            old.finish(message);
        }
        if (guardBound) { guardBound = false; unbindService(guardConnection); }
        stopService(new Intent(this, PairingGuardService.class));
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        session.foreground(generation, true);
        if (guard != null) guard.isAlive();
        updateControls();
    }

    @Override protected void onPause() {
        resumed = false;
        session.foreground(generation, false);
        if (started && !session.active(generation))
            stop("App left foreground after confirmation. Stopped. Close and reopen.");
        updateControls();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stop("Closed.");
        if (receiverRegistered) unregisterReceiver(receiver);
        if (adapter != null && hid != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        super.onDestroy();
    }
}

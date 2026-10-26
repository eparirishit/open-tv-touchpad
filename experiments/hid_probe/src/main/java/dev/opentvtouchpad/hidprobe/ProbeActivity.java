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
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

import static dev.opentvtouchpad.hidprobe.NativeRunGate.Resource.*;
import static dev.opentvtouchpad.hidprobe.ProbeTrace.Event.*;

/** Foreground-only diagnostic; the saved selector is not input authorization. */
public final class ProbeActivity extends Activity {
    private static final NativeRunGate NATIVE_RUNS = new NativeRunGate();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<Button> inputs;
    private static EnrollmentStore enrollment;
    private static TvEnrollment tvEnrollment;
    private boolean pendingTvForget;
    private ProbeSession session;
    private BluetoothAdapter adapter;
    private Run run;
    private boolean resumed;
    private boolean receiverRegistered;
    private TextView status;
    private TextView migrationNotice;
    private Button start;
    private Button visible;
    private Button confirm;
    private Button connect;
    private RemoteScreen screen;
    private TouchpadView touchpad;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Keep a storage-failure latch across configuration recreation; the store holds no Context.
        if (enrollment == null) enrollment = new EnrollmentStore(new AtomicEnrollmentFile(getNoBackupFilesDir()));
        if (tvEnrollment == null) tvEnrollment = new TvEnrollment(new TvEnrollmentFile(getNoBackupFilesDir()),
            new TvEnrollment.Identity() {
                public byte[] fingerprint(String alias) throws Exception {
                    return java.security.MessageDigest.getInstance("SHA-256")
                        .digest(TvIdentity.load(alias).getCertificate().getEncoded());
                }
                public void delete(String alias) throws Exception { TvIdentity.delete(alias); }
            });
        session = new ProbeSession(enrollment);
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
        status.setText(enrollment.failed() ? storageFailure() : savedStatus());
        screen.showSetup(session.savedPeer() == null);
        bind(start, this::requestStart);
        bind(connect, this::connectSaved);
        bind(visible, this::makeVisible);
        bind(confirm, this::confirmAndConnect);
        bind(screen.forget, this::forget);
        if (tvEnrollment.record() != null) screen.tvAddress.setText(tvEnrollment.record().address());
        bind(screen.tvSaveAddress, this::saveTvAddress);
        bind(screen.tvPair, this::pairTv);
        bind(screen.tvConfirm, this::confirmTvCode);
        bind(screen.tvForget, this::forgetTv);
        for (TvCommand command : TvCommand.values()) {
            Button button = screen.tvButtons.get(command);
            bind(button, () -> tvCommand(command));
            button.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchpad.cancelGesture();
                return false;
            });
        }
        bind(screen.moveLeft, () -> move(-16, 0));
        bind(screen.moveRight, () -> move(16, 0));
        bind(screen.moveUp, () -> move(0, -16));
        bind(screen.moveDown, () -> move(0, 16));
        bind(screen.click, this::click);
        for (MouseReport.Direction direction : MouseReport.Direction.values()) {
            Button control = screen.scrollButtons.get(direction);
            bind(control, () -> scroll(direction));
            control.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (run != null) run.scrolling.touchDown();
                    else touchpad.cancelGesture();
                }
                return false;
            });
        }
        bind(screen.stop, () -> stop("Stopped. Enrollment retained."));
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        receiverRegistered = true;
        updateControls();
    }

    private String savedStatus() {
        return session.savedPeer() == null ? "No saved projector. Start for fresh comparison."
            : "Saved projector — disconnected. Start, then Connect.";
    }

    private String storageFailure() {
        return "Enrollment storage failed. Input disabled; disk may still contain the old selector. "
            + "Retry Forget successfully or clear app storage in Android settings before re-enrolling.";
    }

    private void bind(Button button, Runnable action) {
        button.setOnClickListener(view -> {
            try { action.run(); }
            catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
                stop("Bluetooth operation failed. Check Nearby devices permission and Bluetooth.");
            }
        });
    }

    private boolean permissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStart() {
        if (!resumed || enrollment.failed() || pendingTvForget) return;
        if (tvEnrollment.state() == TvEnrollment.State.UNUSABLE) {
            status.setText("TV enrollment is unusable. Forget TV controls successfully before another run.");
            return;
        }
        if (!NATIVE_RUNS.available()) {
            status.setText("Previous native teardown is pending. Tap Start again to recheck. "
                + "If it remains blocked, force stop the probe in Android settings, then reopen.");
            updateControls();
            return;
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
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
        if (session.savedPeer() != null && adapter.getRemoteDevice(session.savedPeer()).getBondState()
                != BluetoothDevice.BOND_BONDED) {
            forget();
            status.setText(enrollment.failed() ? storageFailure() : "Saved bond missing. Fresh comparison required.");
            return;
        }
        NativeRunGate.Run ownership = NATIVE_RUNS.start();
        if (ownership == null) return;
        run = new Run(ownership, session.start(tvEnrollment.state()));
        session.foreground(run.generation, true);
        status.setText("Starting timed pairing service…");
        run.begin();
        updateControls();
    }

    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request, names, results);
        if (request == 1) status.setText(permissions() ? "Permission granted. Tap Start." : "Nearby devices permission is required.");
    }

    /** All native callbacks capture this immutable owner, never the Activity's next run. */
    private final class Run {
        final NativeRunGate.Run ownership;
        final long generation;
        final TvEnrollment.State tvMode = tvEnrollment.state();
        TvRemoteClient tv;
        final ProbeTrace trace = new ProbeTrace(SystemClock.elapsedRealtime());
        BluetoothHidDevice hid;
        BluetoothDevice candidate;
        BluetoothDevice target;
        BluetoothDevice connected;
        PairingGuardService guard;
        boolean guardBound;
        boolean registrationRequested;
        boolean unregistered;
        boolean profileLostWhileStopping;
        boolean selectedDisconnected;
        boolean foreignLinkObserved;
        boolean stopping;
        boolean teardownTraced;
        String terminalReason;
        final Runnable registrationDeadline;
        final Runnable connectionDeadline;
        final Runnable teardownDeadline = this::teardownTimedOut;

        void teardownTimedOut() {
            if (ownership.owns() && stopping) {
                trace(TEARDOWN_DEADLINE);
                status.setText(terminalReason + " Native teardown is unconfirmed. Input disabled. "
                    + "Force stop the probe in Android settings, then reopen before another Start.");
                updateControls();
            }
        }

        Run(NativeRunGate.Run ownership, long generation) {
            this.ownership = ownership;
            this.generation = generation;
            registrationDeadline = () -> {
                if (ownership.owns()) trace(REGISTRATION_DEADLINE);
                stop("Registration timed out.");
            };
            connectionDeadline = () -> {
                if (ownership.owns()) trace(CONNECTION_DEADLINE);
                stop("Connection timed out. No input enabled.");
            };
            trace(session.savedPeer() == null ? RUN_EMPTY : RUN_SAVED);
        }

        final ScrollInput scrolling = new ScrollInput(() -> touchpad.cancelGesture(), () -> canAttempt() && ready(),
            direction -> {
                try {
                    if (!hid.sendReport(connected, MouseReport.ID, MouseReport.scroll(direction))) {
                        stop("Scroll report failed. No retry.");
                    }
                } catch (SecurityException error) {
                    stop("Nearby devices permission unavailable.");
                }
            });

        void trace(ProbeTrace.Event event) {
            String line = trace.line(event, SystemClock.elapsedRealtime());
            if (line != null) Log.i("OpenTvHidTrace", line);
        }

        boolean active() {
            return ownership.active() && session.active(generation) && guard != null && guard.isAlive();
        }

        void begin() {
            ownership.await(SERVICE);
            Intent intent = new Intent(ProbeActivity.this, PairingGuardService.class);
            try {
                startForegroundService(intent);
                guardBound = bindService(intent, guardConnection, Context.BIND_AUTO_CREATE);
                if (!guardBound) { ownership.fail(); stop("Pairing service unavailable."); return; }
                handler.postDelayed(registrationDeadline, 5000);
            } catch (SecurityException | IllegalStateException error) {
                ownership.fail();
                stop("Pairing service start failed.");
            }
        }

        final ServiceConnection guardConnection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (!ownership.owns()) return;
                guard = ((PairingGuardService.LocalBinder) binder).service();
                guard.onDestroyed(() -> {
                    if (!ownership.owns()) return;
                    ownership.completed(SERVICE);
                    cleanupProgress();
                });
                if (stopping) { finishGuard(); return; }
                guard.attach(message -> {
                    // Observe the service's fixed reason without changing its deadline or lifecycle.
                    if (ownership.owns()) trace("Five-minute diagnostic window expired. Start a new run after teardown.".equals(message)
                        ? RUN_DEADLINE : SERVICE_STOP);
                    stop(message);
                });
                if (!active()) return;
                try {
                    ownership.await(PROFILE);
                    status.setText("Requesting HID service…");
                    if (!adapter.getProfileProxy(ProbeActivity.this, service, BluetoothProfile.HID_DEVICE)) {
                        ownership.completed(PROFILE);
                        stop("HID profile unavailable.");
                    }
                } catch (SecurityException | IllegalStateException error) {
                    ownership.fail();
                    stop("HID service request failed.");
                }
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if (!ownership.owns()) return;
                trace(SERVICE_LOST);
                ownership.fail();
                stop("Pairing service lost. Native teardown unconfirmed.");
            }
            @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
        };

        final BluetoothProfile.ServiceListener service = new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (!ownership.owns()) return;
                hid = (BluetoothHidDevice) proxy;
                if (stopping) { finishProfile(); return; }
                if (!active()) { stop("Pairing service ended."); return; }
                try {
                    ownership.await(REGISTRATION);
                    trace(REGISTER_REQUEST);
                    registrationRequested = hid.registerApp(new BluetoothHidDeviceAppSdpSettings(
                        "Open TV HID Probe", "Disposable manual mouse test", "Open TV Touchpad",
                        BluetoothHidDevice.SUBCLASS1_MOUSE, MouseReport.descriptor()), null, null,
                        getMainExecutor(), callback);
                    trace(registrationRequested ? REGISTER_ACCEPTED : REGISTER_REJECTED);
                    if (!registrationRequested) {
                        ownership.completed(REGISTRATION);
                        stop("HID registration request rejected. Another HID app may be active.");
                    }
                } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
                    trace(REGISTER_FAILED);
                    ownership.fail();
                    stop("HID registration failed; native state unconfirmed.");
                }
            }
            @Override public void onServiceDisconnected(int profile) {
                if (!ownership.owns()) return;
                trace(PROFILE_LOST);
                if (stopping) {
                    profileLostWhileStopping = true;
                    finishProfile();
                    return;
                }
                ownership.fail();
                stop("HID service lost. Native teardown unconfirmed.");
            }
        };

        final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
            @Override public void onAppStatusChanged(BluetoothDevice plugged, boolean registered) {
                if (!ownership.owns()) return;
                trace(registered ? REGISTERED : UNREGISTERED);
                if (stopping) {
                    try {
                        if (registered) {
                            unregistered = false;
                            if (hid == null || !hid.unregisterApp()) ownership.fail();
                        } else {
                            unregistered = true;
                            finishProfile();
                        }
                    } catch (SecurityException | IllegalStateException error) { ownership.fail(); }
                    cleanupProgress();
                    return;
                }
                if (!active()) { stop("Pairing service ended."); return; }
                session.registered(generation, registered);
                handler.removeCallbacks(registrationDeadline);
                if (!registered) {
                    unregistered = true;
                    stop("HID registration ended. No input enabled.");
                    return;
                }
                status.setText(session.savedPeer() == null ? "HID registered. Make visible, then pair from the XGIMI."
                    : "Saved projector — disconnected. Tap Connect saved projector.");
                updateControls();
            }

            @Override public void onConnectionStateChanged(BluetoothDevice peer, int state) {
                if (!ownership.owns()) return;
                try {
                    boolean selected = peer.equals(target) || (session.savedPeer() != null
                        && session.savedPeer().equals(peer.getAddress()));
                    if (selected) selectedDisconnected = state == BluetoothProfile.STATE_DISCONNECTED;
                    else if (state != BluetoothProfile.STATE_DISCONNECTED) foreignLinkObserved = true;
                    switch (state) {
                        case BluetoothProfile.STATE_CONNECTING -> trace(selected ? SELECTED_CONNECTING : OTHER_CONNECTING);
                        case BluetoothProfile.STATE_CONNECTED -> trace(selected ? SELECTED_CONNECTED : OTHER_CONNECTED);
                        case BluetoothProfile.STATE_DISCONNECTING -> trace(selected ? SELECTED_DISCONNECTING : OTHER_DISCONNECTING);
                        case BluetoothProfile.STATE_DISCONNECTED -> trace(selected ? SELECTED_DISCONNECTED : OTHER_DISCONNECTED);
                        default -> trace(selected ? SELECTED_STATE_OTHER : OTHER_STATE_OTHER);
                    }
                    if (stopping) {
                        if (state != BluetoothProfile.STATE_DISCONNECTED && hid == null) ownership.fail();
                        if (state == BluetoothProfile.STATE_CONNECTED && hid != null && !hid.disconnect(peer)) ownership.fail();
                        finishProfile();
                        return;
                    }
                    if (!active()) { stop("Pairing service ended."); return; }
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        if (peer.getBondState() != BluetoothDevice.BOND_BONDED) {
                            trace(CONNECTION_NOT_BONDED);
                            session.revoke(peer.getAddress());
                            stop("Connected peer is not bonded. No input enabled.");
                            return;
                        }
                        if (!session.connected(generation, peer.getAddress(), true)) {
                            trace(CONNECTION_NOT_ADMITTED);
                            // No neutral or other report is sent to unsolicited connections.
                            if (!session.active(generation)) { stop("Ambiguous connection callback. No input enabled."); return; }
                            if (!hid.disconnect(peer)) stop("Unsolicited connection could not be disconnected.");
                            updateControls();
                            return;
                        }
                        connected = peer;
                        handler.removeCallbacks(connectionDeadline);
                        boolean neutral = hid.sendReport(peer, MouseReport.ID, MouseReport.neutral());
                        trace(neutral ? NEUTRAL_ACCEPTED : NEUTRAL_REJECTED);
                        if (!session.neutralSubmitted(generation, peer.getAddress(), neutral)) {
                            stop("Neutral report failed. No input enabled.");
                            return;
                        }
                        status.setText(tvMode == TvEnrollment.State.VALID
                            ? "Mouse and TV connected. Both controls are ready."
                            : "Mouse connected. TV controls are not paired.");
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (session.connected(generation, peer.getAddress(), false)) stop("Projector disconnected. No input enabled.");
                    }
                    updateControls();
                } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
                    trace(CONNECTION_CALLBACK_FAILED);
                    stop("Bluetooth connection callback failed.");
                }
            }

            @Override public void onGetReport(BluetoothDevice peer, byte type, byte id, int bufferSize) {
                if (!ownership.active()) return;
                try {
                    if (!ready() || !connected.equals(peer)) return;
                    if (MouseReport.acceptsGetReport(type, id, bufferSize)) {
                        if (!hid.replyReport(peer, type, id, MouseReport.neutral())) stop("Report reply failed.");
                    } else if (!hid.reportError(peer, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)) stop("Report error reply failed.");
                } catch (SecurityException | IllegalStateException | IllegalArgumentException error) { stop("Report request failed."); }
            }
            @Override public void onSetProtocol(BluetoothDevice peer, byte protocol) {
                if (ownership.active() && target != null && target.equals(peer)) {
                    trace(protocol == BluetoothHidDevice.PROTOCOL_REPORT_MODE ? PROTOCOL_REPORT
                        : protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE ? PROTOCOL_BOOT : PROTOCOL_OTHER);
                    if (protocol != BluetoothHidDevice.PROTOCOL_REPORT_MODE) stop("Host requested unsupported boot protocol.");
                }
            }
            @Override public void onVirtualCableUnplug(BluetoothDevice peer) {
                if (ownership.active() && target != null && target.equals(peer)) stop("Host unplugged mouse.");
            }
        };

        boolean ready() {
            if (!active() || !resumed || hid == null || connected == null) return false;
            if (tvMode == TvEnrollment.State.UNUSABLE
                    || (tvMode == TvEnrollment.State.VALID && (tv == null || !tv.ready()))
                    || (tvMode == TvEnrollment.State.ABSENT && tv != null)) return false;
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    || !adapter.isEnabled()) { stop("Bluetooth permission or adapter lost."); return false; }
            if (connected.getBondState() != BluetoothDevice.BOND_BONDED) {
                session.revoke(connected.getAddress());
                stop(enrollment.failed() ? storageFailure() : "Selected bond lost. Fresh comparison required.");
                return false;
            }
            return session.canSend(generation, connected.getAddress());
        }

        TvRemoteClient createTv() {
            ownership.await(TV);
            return new TvRemoteClient(SystemClock::elapsedRealtime, guard.deadlineMillis(), new TvRemoteClient.Listener() {
                public void needCode() { handler.post(() -> {
                    if (!active() || !resumed || stopping) return;
                    status.setText("Enter the six-character code shown on the intended projector.");
                    updateControls();
                }); }
                public void connected() { handler.post(() -> {
                    if (!active() || !resumed || stopping || tv == null || !tv.ready()) return;
                    if (!session.tvConnected(generation)) { stop("TV connection state changed. No input enabled."); return; }
                    connect(target);
                }); }
                public void enrolled(TvEnrollment.Record record) { handler.post(() -> {
                    // Main-thread finalization serializes Stop/Forget with durable trust publication.
                    if (!active() || !resumed || stopping || tv == null || !tv.ready()) return;
                    if (!tvEnrollment.save(record)) {
                        stop("TV enrollment could not be saved. Forget TV controls before retrying.");
                        return;
                    }
                    stop("TV controls paired. Start a new run, then Connect both channels.");
                }); }
                public void failed(TvRemoteClient.Stage stage) { handler.post(() -> {
                    if (ownership.owns() && !stopping)
                        stop("TV connection or pairing failed at " + stage.name() + ". Both channels stopped.");
                }); }
                public void closed() { handler.post(() -> {
                    if (!ownership.owns()) return;
                    if (!stopping) stop("TV connection ended. Both channels stopped.");
                    ownership.completed(TV);
                    cleanupProgress();
                }); }
            });
        }

        void connectSelected(BluetoothDevice peer) {
            if (tvMode == TvEnrollment.State.ABSENT) { connect(peer); return; }
            if (tvMode != TvEnrollment.State.VALID || tvEnrollment.record() == null) {
                stop("TV enrollment unavailable. Forget TV controls before retrying."); return;
            }
            target = peer;
            try {
                tv = createTv();
                status.setText("Verifying TV connection before connecting the mouse…");
                tv.connect(tvEnrollment.record());
            } catch (RuntimeException failure) {
                ownership.fail();
                stop("TV connection setup failed. Native teardown unconfirmed.");
            }
            updateControls();
        }

        void connect(BluetoothDevice peer) {
            if (!active() || !resumed) { stop("Run ended before connect."); return; }
            target = peer;
            updateControls();
            status.setText("Connecting to the selected projector…");
            try {
                selectedDisconnected = false;
                if (hid.getConnectionState(peer) != BluetoothProfile.STATE_DISCONNECTED) {
                    trace(CONNECT_NATIVE_PENDING);
                    stop("A native connection is still pending. No overlapping attempt allowed.");
                    return;
                }
                trace(CONNECT_REQUEST);
                boolean accepted = hid.connect(peer);
                trace(accepted ? CONNECT_ACCEPTED : CONNECT_REJECTED);
                if (!accepted) { stop("Mouse connection request rejected."); return; }
                handler.postDelayed(connectionDeadline, 10000);
            } catch (SecurityException | IllegalStateException error) {
                trace(CONNECT_FAILED);
                stop("Mouse connection request failed.");
            }
        }

        void stop(String message) {
            if (!ownership.owns() || stopping) return;
            touchpad.cancelGesture();
            stopping = true;
            clearTvCode();
            if (tv != null) {
                try { tv.stop(); }
                catch (RuntimeException failure) { ownership.fail(); }
            }
            trace(STOP);
            terminalReason = enrollment.failed() ? storageFailure() : message;
            session.stop();
            ownership.stop();
            handler.removeCallbacks(registrationDeadline);
            handler.removeCallbacks(connectionDeadline);
            status.setText(terminalReason + " Waiting for native teardown…");
            updateControls();
            handler.postDelayed(teardownDeadline, 5000);
            if (hid != null) {
                if (connected != null) {
                    try { hid.sendReport(connected, MouseReport.ID, MouseReport.neutral()); }
                    catch (SecurityException | IllegalStateException | IllegalArgumentException ignored) { /* Best-effort release, never delivery proof. */ }
                }
                try {
                    if (target != null && hid.getConnectionState(target) != BluetoothProfile.STATE_DISCONNECTED) {
                        pendingLink(target);
                        if (!hid.disconnect(target)) ownership.fail();
                    }
                } catch (SecurityException | IllegalStateException | IllegalArgumentException error) { ownership.fail(); }
                try {
                    for (BluetoothDevice peer : hid.getConnectedDevices()) {
                        pendingLink(peer);
                        try {
                            if (!peer.equals(target) && !hid.disconnect(peer)) ownership.fail();
                        } catch (SecurityException | IllegalStateException | IllegalArgumentException error) { ownership.fail(); }
                    }
                } catch (SecurityException | IllegalStateException | IllegalArgumentException error) { ownership.fail(); }
                try {
                    if (registrationRequested && !unregistered && !hid.unregisterApp()) ownership.fail();
                } catch (SecurityException | IllegalStateException | IllegalArgumentException error) { ownership.fail(); }
                finishProfile();
            }
            finishGuard();
            cleanupProgress();
        }

        void finishGuard() {
            if (guard != null) {
                guard.detach();
                guard.finish("Probe stopped.");
            }
            if (guardBound && guard != null) {
                guardBound = false;
                unbindService(guardConnection);
            }
            stopService(new Intent(ProbeActivity.this, PairingGuardService.class));
        }

        void pendingLink(BluetoothDevice peer) {
            if (peer.equals(target)) selectedDisconnected = false;
            else foreignLinkObserved = true;
        }

        void finishProfile() {
            if (!ownership.owns() || !stopping || hid == null || (registrationRequested && !unregistered)) return;
            try {
                if (profileLostWhileStopping || !adapter.isEnabled()) {
                    // Detached proxies return empty/disconnected defaults; require this owner's callbacks.
                    if (target == null || !selectedDisconnected || foreignLinkObserved) return;
                } else {
                    List<BluetoothDevice> peers = hid.getConnectedDevices();
                    for (BluetoothDevice peer : peers) pendingLink(peer);
                    if (!peers.isEmpty()) return;
                    if (target != null && hid.getConnectionState(target) != BluetoothProfile.STATE_DISCONNECTED) {
                        pendingLink(target);
                        return;
                    }
                }
                // Complete only after native unregister acknowledgement AND no remaining link.
                ownership.completed(REGISTRATION);
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
                hid = null;
                ownership.completed(PROFILE);
            } catch (SecurityException | IllegalStateException | IllegalArgumentException error) { ownership.fail(); }
            cleanupProgress();
        }

        void cleanupProgress() {
            if (!ownership.owns()) {
                if (!teardownTraced) {
                    teardownTraced = true;
                    trace(TEARDOWN_COMPLETE);
                }
                handler.removeCallbacks(teardownDeadline);
                status.setText(terminalReason + " Native teardown completed. Tap Start for a new run.");
            }
            completePendingTvForget();
            updateControls();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON)
                        stop("Bluetooth turned off. No input enabled.");
                    return;
                }
                BluetoothDevice peer = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (peer == null) return;
                String address = peer.getAddress();
                boolean pairing = BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction());
                int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                boolean bondChange = BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction());
                // Observe selected-peer revocation throughout this Activity's lifetime, even after Stop.
                if ((pairing || (bondChange && bond != BluetoothDevice.BOND_BONDED)) && session.revoke(address)) {
                    stop(enrollment.failed() ? storageFailure() : "Saved projector revoked. Fresh comparison required.");
                    updateControls();
                    return;
                }
                if (run == null || !run.active()) return;
                if (pairing) {
                    int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                    if (!session.observePairing(run.generation, address, variant)) {
                        if (!session.active(run.generation)) { stop("Pairing changed. No input enabled."); return; }
                        status.setText("Pairing method/peer not eligible. Cancel unless both screens show matching numbers.");
                    } else {
                        run.candidate = peer;
                        status.setText("Compare digits on BOTH intended screens before accepting. Then confirm below.");
                    }
                } else if (bondChange && run.candidate != null && run.candidate.equals(peer)) {
                    if (bond == BluetoothDevice.BOND_BONDED) {
                        session.bonded(run.generation, address);
                        status.setText("Bond created. Confirm ONLY if you compared matching codes on both screens.");
                    } else if (bond != BluetoothDevice.BOND_BONDING) stop("Pairing cancelled or bond removed.");
                }
                updateControls();
            } catch (SecurityException | IllegalStateException error) { stop("Bluetooth pairing check failed."); }
        }
    };

    private boolean canAttempt() {
        if (!permissions() || adapter == null || !adapter.isEnabled()) {
            stop("Nearby devices permission or Bluetooth unavailable.");
            return false;
        }
        return run != null && run.active() && resumed && run.hid != null;
    }

    private void makeVisible() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable."); return;
        }
        if (!canAttempt() || !session.isRegistered(run.generation) || session.savedPeer() != null) return;
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(intent);
    }

    private void confirmAndConnect() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable."); return;
        }
        if (!canAttempt() || run.candidate == null || !session.canConfirm(run.generation)) return;
        if (run.candidate.getBondState() != BluetoothDevice.BOND_BONDED) { stop("Fresh bond missing."); return; }
        if (!session.confirmComparison(run.generation)) {
            stop(enrollment.failed() ? storageFailure() : "Comparison no longer eligible.");
            return;
        }
        run.connectSelected(run.candidate);
    }

    private void connectSaved() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stop("Nearby devices permission unavailable."); return;
        }
        if (!canAttempt() || !session.canConnect(run.generation)) return;
        BluetoothDevice selected = adapter.getRemoteDevice(session.savedPeer());
        if (!session.connectSaved(run.generation, selected.getBondState() == BluetoothDevice.BOND_BONDED)) {
            stop(enrollment.failed() ? storageFailure() : "Saved bond missing. Fresh comparison required.");
            return;
        }
        run.connectSelected(selected);
    }

    private void forget() {
        stop("Stopping to forget the Bluetooth projector.");
        boolean cleared = session.forget();
        stop(cleared ? "Projector forgotten. Remove its bond in OS settings before fresh comparison." : storageFailure());
        status.setText(cleared ? "App record cleared. Remove the old bond in OS settings before fresh comparison."
            : storageFailure());
        updateControls();
    }

    private void clearTvCode() { if (screen != null) screen.tvCode.getText().clear(); }

    private void pairTv() {
        if (!canAttempt() || !session.canPairTv(run.generation)) return;
        String address = screen.tvAddress.getText().toString();
        if (!TvEnrollment.validAddress(address)) {
            status.setText("Enter a private Wi-Fi IPv4 address in Setup before pairing."); return;
        }
        if (!session.beginTvPair(run.generation)) return;
        clearTvCode();
        try {
            run.tv = run.createTv();
            status.setText("Starting TV pairing. No input is enabled during this run.");
            run.tv.beginPair(address);
        } catch (RuntimeException failure) {
            run.ownership.fail();
            stop("TV pairing setup failed. Native teardown unconfirmed.");
        }
        updateControls();
    }

    private void confirmTvCode() {
        if (!canAttempt() || run.tv == null || !run.tv.needsCode()) return;
        String code = screen.tvCode.getText().toString();
        clearTvCode();
        try { run.tv.finishPair(code); }
        catch (RuntimeException failure) { stop("TV code submission failed. Start a new pairing run."); }
        updateControls();
    }

    private void saveTvAddress() {
        if (!resumed || !NATIVE_RUNS.available() || pendingTvForget) return;
        String address = screen.tvAddress.getText().toString();
        if (!TvEnrollment.validAddress(address)) {
            status.setText("Use a private IPv4 address, with no spaces or port."); return;
        }
        if (tvEnrollment.state() == TvEnrollment.State.VALID) {
            status.setText(tvEnrollment.changeLocator(address)
                ? "TV address saved. Certificate identity is unchanged; Start then Connect."
                : "TV address could not be saved. Forget TV controls successfully before retrying.");
        } else if (tvEnrollment.state() == TvEnrollment.State.ABSENT) {
            status.setText("Address ready. Start a new run, then Pair TV in Setup.");
        }
        updateControls();
    }

    private void forgetTv() {
        if (!NATIVE_RUNS.available() && (run == null || !run.ownership.owns())) {
            status.setText("Previous native teardown is pending. Retry Forget TV controls after it completes.");
            return;
        }
        pendingTvForget = true;
        clearTvCode();
        stop("Stopping both channels before forgetting TV controls.");
        completePendingTvForget();
    }

    private void completePendingTvForget() {
        if (!pendingTvForget || !NATIVE_RUNS.available()) return;
        pendingTvForget = false;
        boolean cleared = tvEnrollment.forget();
        status.setText(cleared ? "TV controls forgotten. Bluetooth enrollment is retained."
            : "TV removal is unconfirmed. Input blocked; retry Forget TV controls successfully.");
        updateControls();
    }

    private void tvCommand(TvCommand command) {
        touchpad.cancelGesture();
        if (!canAttempt() || !run.ready() || run.tv == null) return;
        run.tv.submit(command);
    }

    private void move(int dx, int dy) {
        InputSubmission.guard(() -> {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                stop("Nearby devices permission unavailable."); return;
            }
            if (!canAttempt() || !run.ready()) return;
            boolean accepted = run.hid.sendReport(run.connected, MouseReport.ID, MouseReport.move(dx, dy));
            run.trace(accepted ? MOVE_ACCEPTED : MOVE_REJECTED);
            if (!accepted) stop("Mouse movement report failed.");
        }, () -> stop("Mouse input failed. Check Nearby devices permission and Bluetooth."));
    }

    private void click() {
        InputSubmission.guard(() -> {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                stop("Nearby devices permission unavailable."); return;
            }
            if (!canAttempt() || !run.ready()) return;
            Run current = run;
            boolean accepted = InputSubmission.click(
                () -> current.hid.sendReport(current.connected, MouseReport.ID, MouseReport.clickDown()),
                () -> current.hid.sendReport(current.connected, MouseReport.ID, MouseReport.neutral()));
            if (!accepted) stop("Click report failed. Disconnect requested.");
        }, () -> stop("Mouse input failed. Check Nearby devices permission and Bluetooth."));
    }

    private void updateControls() {
        migrationNotice.setVisibility(enrollment.requiresReenrollment() ? View.VISIBLE : View.GONE);
        // An idle/recreated screen can explicitly retry after another owner's teardown finishes.
        start.setEnabled(resumed && !enrollment.failed() && !pendingTvForget
            && tvEnrollment.state() != TvEnrollment.State.UNUSABLE && (run == null || run.stopping));
        boolean registered = run != null && run.ownership.active() && session.isRegistered(run.generation);
        visible.setEnabled(resumed && registered && session.savedPeer() == null && run.hid != null);
        confirm.setEnabled(registered && session.canConfirm(run.generation));
        connect.setEnabled(registered && session.canConnect(run.generation));
        boolean ready = false;
        try { ready = run != null && run.ready(); } catch (SecurityException | IllegalStateException ignored) { }
        for (Button button : inputs) button.setEnabled(ready);
        touchpad.setEnabled(ready);
        screen.setConnected(ready);
        boolean stopped = resumed && NATIVE_RUNS.available() && !pendingTvForget;
        screen.tvAddress.setEnabled(stopped && tvEnrollment.state() != TvEnrollment.State.UNUSABLE);
        screen.tvSaveAddress.setEnabled(screen.tvAddress.isEnabled());
        screen.tvPair.setEnabled(registered && !pendingTvForget && session.canPairTv(run.generation));
        boolean code = resumed && registered && run.tv != null && run.tv.needsCode();
        screen.tvCode.setEnabled(code);
        screen.tvConfirm.setEnabled(code);
        screen.tvForget.setEnabled(resumed && !pendingTvForget);
        boolean tvReady = ready && run.tvMode == TvEnrollment.State.VALID && run.tv != null;
        for (Button button : screen.tvButtons.values()) button.setEnabled(tvReady);
        screen.tvStatus.setText(tvEnrollment.state() == TvEnrollment.State.UNUSABLE ? "TV: enrollment unusable"
            : run != null && run.ownership.active() && run.tv != null
                ? run.tv.ready() ? "TV: connected" : code ? "TV: code required" : "TV: connecting"
                : tvEnrollment.state() == TvEnrollment.State.VALID ? "TV: paired, disconnected" : "TV: not paired");
    }

    private void stop(String message) {
        if (touchpad != null) touchpad.cancelGesture();
        clearTvCode();
        if (run != null && !run.stopping && run.ownership.owns()) run.stop(message);
        else {
            session.stop();
            status.setText(message);
            updateControls();
        }
    }

    private void scroll(MouseReport.Direction direction) {
        InputSubmission.guard(() -> {
            if (run != null) run.scrolling.click(direction, SystemClock.uptimeMillis());
        }, () -> stop("Scroll input failed. Check Nearby devices permission and Bluetooth."));
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (run != null) session.foreground(run.generation, true);
        updateControls();
    }

    @Override protected void onPause() {
        clearTvCode();
        if (touchpad != null) touchpad.cancelGesture();
        resumed = false;
        if (run != null) {
            if (run.ownership.owns()) run.trace(PAUSE);
            session.foreground(run.generation, false);
            if (!run.stopping && !session.active(run.generation)) stop("App left foreground after Connect. Stopped.");
        }
        updateControls();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && touchpad != null) touchpad.cancelGesture();
    }

    @Override protected void onDestroy() {
        stop("Closed.");
        if (receiverRegistered) unregisterReceiver(receiver);
        super.onDestroy();
    }
}

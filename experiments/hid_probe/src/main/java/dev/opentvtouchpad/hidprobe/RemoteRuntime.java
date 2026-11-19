package dev.opentvtouchpad.hidprobe;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.List;

import static dev.opentvtouchpad.hidprobe.NativeRunGate.Resource.*;
import static dev.opentvtouchpad.hidprobe.ProbeTrace.Event.*;

/** Service-owned diagnostic state. No Activity, View, UI listener or UI callback is retained. */
final class RemoteRuntime {
    private static RemoteRuntime processRuntime;
    private static final NativeRunGate NATIVE_RUNS = new NativeRunGate();
    // Stores hold no Context; keep storage-failure latches across service recreation.
    private static EnrollmentStore enrollment;
    private static TvEnrollment tvEnrollment;
    private final Context context;
    private RemoteConnectionService connectionService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable visibilityRefresh = this::refreshEligibility;
    private final ProbeSession session;
    private final SetupAddressStore setupAddressStore;
    private enum SetupIntent { NONE, BLUETOOTH, TV }
    private SetupIntent pendingSetupIntent = SetupIntent.NONE;
    private long pendingSetupGeneration;
    private java.util.concurrent.atomic.AtomicBoolean setupVisit, pendingSetupVisit;
    private SetupState.Page setupPage = SetupState.Page.OVERVIEW, setupAfterCleanup;
    private String setupMessage = "", setupDiagnostic = "";
    private BluetoothAdapter adapter;
    private Run run;
    private boolean resumed, focused, surfaceVisible, pendingTvForget, serviceDestroyed, windowStarted;
    private final InputVisibilityGate inputVisibility = new InputVisibilityGate();
    private final SessionLifetime lifetime = new SessionLifetime(SystemClock::elapsedRealtime);
    private long attachment, nextAttachment, codeRevision, gestureRevision;
    private String status;

    enum Request { START, REMOTE_CONNECT, CONNECT, CONFIRM, FORGET, PAIR_TV, TV_CODE, SAVE_TV_ADDRESS, FORGET_TV, STOP }

    record Snapshot(String status, String tvAddress, boolean setup, boolean migration,
            boolean start, boolean visible, boolean confirm, boolean connect, boolean ready,
            boolean tvAddressEnabled, boolean tvPair, boolean tvCode, boolean tvForget,
            boolean tvReady, boolean tvConnected, String tvStatus, long codeRevision,
            long gestureRevision, boolean releaseBinding, boolean destroyed, long inputEpoch, boolean connected, SetupState.Snapshot wizard) {}

    static RemoteRuntime forService(RemoteConnectionService service) {
        // Late native completion and failure latches belong to this process's sole runtime.
        if (processRuntime == null) processRuntime = new RemoteRuntime(service);
        processRuntime.connectionService = service;
        processRuntime.serviceDestroyed = false;
        return processRuntime;
    }

    private RemoteRuntime(RemoteConnectionService service) {
        context = service.getApplicationContext();
        connectionService = service;
        if (enrollment == null) enrollment = new EnrollmentStore(new AtomicEnrollmentFile(service.getNoBackupFilesDir()));
        if (tvEnrollment == null) tvEnrollment = new TvEnrollment(new TvEnrollmentFile(service.getNoBackupFilesDir()), tvIdentity());
        session = new ProbeSession(enrollment);
        setupAddressStore = new SetupAddressStore(new SetupAddressFile(service.getNoBackupFilesDir()));
        status = enrollment.failed() ? storageFailure() : savedStatus();
    }

    private static TvEnrollment.Identity tvIdentity() {
        return new TvEnrollment.Identity() {
            public byte[] fingerprint(String alias) throws Exception {
                return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(TvIdentity.load(alias).getCertificate().getEncoded());
            }
            public void delete(String alias) throws Exception { TvIdentity.delete(alias); }
        };
    }

    private boolean interactiveUnlocked() {
        android.os.PowerManager power = context.getSystemService(android.os.PowerManager.class);
        android.app.KeyguardManager keyguard = context.getSystemService(android.app.KeyguardManager.class);
        return power != null && keyguard != null && power.isInteractive() && !keyguard.isKeyguardLocked();
    }

    private boolean attended() {
        return resumed && focused && interactiveUnlocked();
    }

    private void setInputEligible(boolean eligible) {
        long previous = inputVisibility.epoch();
        long current = inputVisibility.update(eligible);
        if (!eligible) {
            if (lifetime.control()) lifetime.visibility(false);
            else if (run != null && !run.stopping && run.connecting && run.setupIntent == SetupIntent.NONE) {
                // Cancel even before the first input epoch; a quick return must not revive this run.
                run.connectOnRegister = false;
                session.stop();
                if (run.tv != null) run.tv.stop();
            }
        }
        if (previous == current) return;
        cancelGesture();
        if (current == 0) {
            if (lifetime.control()) setStatus("Connected — input paused. Return within 30 minutes to resume.");
            clearTvCode();
            if (run != null && run.tv != null) run.tv.inputRevoked();
        } else setStatus("Connected. Input is enabled while the remote is visible.");
    }

    private void refreshEligibility() {
        if (run == null || run.stopping || !run.ownership.active()) {
            setInputEligible(false);
            return;
        }
        if (!lifetime.alive()) { stop("Session window expired. Both channels stopped."); return; }
        if (run.setupIntent != SetupIntent.NONE) { refreshSetupEligibility(); return; }
        boolean visible = attended();
        boolean eligible = visible && surfaceVisible;
        if (!eligible) setInputEligible(false);
        session.foreground(run.generation, visible);
        if (!session.active(run.generation)) { stop("Remote hidden during pairing or connection. Stopped."); return; }
        boolean established;
        try { established = run.transportReady(); }
        catch (SecurityException | IllegalStateException failure) { stop("Connection permission or state lost."); return; }
        if (!lifetime.control() && established) {
            if (!eligible || !lifetime.connected()) { stop("Remote hidden before connection completed. Stopped."); return; }
            run.connecting = false;
        }
        if (lifetime.control()) {
            if (!established) { stop("Connection lost. Both channels stopped."); return; }
            if (!lifetime.visibility(eligible)) { stop("Idle connection expired. Both channels stopped."); return; }
        } else if (run.connecting && !eligible) {
            stop("Remote hidden during connection. Stopped."); return;
        }
        if (lifetime.control() && established && eligible) setInputEligible(true);
        if (connectionService != null) connectionService.scheduleDeadline();
    }

    private void deferVisibilityRefresh() {
        handler.removeCallbacks(visibilityRefresh);
        handler.post(visibilityRefresh);
    }

    long attach() { return attach(new java.util.concurrent.atomic.AtomicBoolean(true)); }

    long attach(java.util.concurrent.atomic.AtomicBoolean visit) {
        if (attachment != 0) foreground(attachment, false);
        attachment = Math.incrementExact(nextAttachment);
        nextAttachment = attachment;
        setupVisit = java.util.Objects.requireNonNull(visit);
        resumed = focused = surfaceVisible = false;
        setInputEligible(false);
        return attachment;
    }

    boolean attached(long token) {
        return token != 0 && token == attachment && !serviceDestroyed;
    }

    void foreground(long token, boolean foreground) {
        if (!attached(token)) return;
        boolean wasResumed = resumed;
        resumed = foreground;
        if (!foreground) {
            pendingSetupIntent = SetupIntent.NONE;
            focused = false;
            setInputEligible(false);
            clearTvCode(); cancelGesture();
            if (run != null) session.foreground(run.generation, false);
            if (wasResumed && run != null && run.ownership.owns()) run.trace(PAUSE);
            deferVisibilityRefresh();
        } else refreshEligibility();
    }

    void uiState(long token, boolean focused, boolean surfaceVisible) {
        if (!attached(token)) return;
        this.focused = focused;
        this.surfaceVisible = surfaceVisible;
        if (surfaceVisible) pendingSetupIntent = SetupIntent.NONE;
        if (!focused || !surfaceVisible) setInputEligible(false);
        if (!focused) {
            clearTvCode(); cancelGesture();
            if (run != null) session.foreground(run.generation, false);
        }
        if (!focused || !surfaceVisible) deferVisibilityRefresh();
        else refreshEligibility();
    }

    void detach(long token) {
        if (!attached(token)) return;
        foreground(token, false);
        if (!lifetime.control()) session.stop();
        attachment = 0;
        deferVisibilityRefresh();
    }

    void connectionLost(long token) {
        if (!attached(token) || run == null || !run.ownership.owns()) return;
        run.trace(SERVICE_LOST);
        run.ownership.fail();
        stop("Pairing service lost. Native teardown unconfirmed.");
    }

    void serviceStopped(String reason) {
        if (run != null && run.ownership.owns() && !run.stopping)
            run.trace("Session window expired. Both channels stopped.".equals(reason)
                ? RUN_DEADLINE : SERVICE_STOP);
        stop(reason);
    }

    void destroyed() {
        if (run != null) stop("Pairing service ended. No input enabled.");
        serviceDestroyed = true;
        connectionService = null;
        windowStarted = false;
        resumed = false;
        attachment = 0;
        if (run != null) {
            run.ownership.completed(SERVICE);
            run.cleanupProgress();
        }
    }

    boolean request(long token, Request request, String value) {
        if (!attached(token)) return false;
        try {
            if (request == Request.STOP) { stop("Stopped. Enrollment retained."); return true; }
            if (!attended()) return false;
            refreshEligibility();
            switch (request) {
                case START -> requestStart(false);
                case REMOTE_CONNECT -> connectRemote();
                case CONNECT -> connectSaved();
                case CONFIRM -> confirmAndConnect();
                case FORGET -> forget();
                case PAIR_TV -> pairTv(value);
                case TV_CODE -> confirmTvCode(value);
                case SAVE_TV_ADDRESS -> saveTvAddress(value);
                case FORGET_TV -> forgetTv();
            }
            return true;
        } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
            stop("Bluetooth operation failed. Check Nearby devices permission and Bluetooth.");
            return false;
        }
    }

    boolean makeVisible(long token) {
        return attached(token) && canAttempt() && session.isRegistered(run.generation) && session.savedPeer() == null;
    }

    private boolean acceptsInput(long token, long epoch) {
        if (!attached(token)) return false;
        refreshEligibility();
        return inputVisibility.accepts(epoch);
    }

    void move(long token, long epoch, int dx, int dy) { if (acceptsInput(token, epoch)) move(dx, dy); }
    void click(long token, long epoch) { if (acceptsInput(token, epoch)) click(); }
    void scroll(long token, long epoch, MouseReport.Direction direction) { if (acceptsInput(token, epoch)) scroll(direction); }
    void tvCommand(long token, long epoch, TvCommand command) { if (acceptsInput(token, epoch)) tvCommand(command, epoch); }
    void scrollTouchDown(long token, long epoch) { if (acceptsInput(token, epoch) && run != null) run.scrolling.touchDown(); }
    private void cancelGesture() { gestureRevision++; }
    private void clearTvCode() { codeRevision++; }
    private void setStatus(String message) { status = message; }
    private int checkSelfPermission(String permission) { return context.checkSelfPermission(permission); }

    Snapshot snapshot(long token) {
        refreshEligibility();
        boolean attached = attached(token);
        boolean registered = attached && run != null && run.ownership.active() && session.isRegistered(run.generation);
        boolean ready = attached && inputVisibility.epoch() != 0;
        boolean connected = false;
        try { connected = run != null && run.transportReady(); } catch (SecurityException | IllegalStateException ignored) { }
        boolean code = attended() && registered && run.tv != null && run.tv.needsCode();
        boolean tvActive = run != null && run.ownership.active() && run.tv != null;
        boolean tvConnected = tvActive && run.tv.ready();
        String tvStatus = tvEnrollment.state() == TvEnrollment.State.UNUSABLE ? "TV: enrollment unusable"
            : tvActive ? tvConnected ? "TV: connected" : code ? "TV: code required" : "TV: connecting"
                : tvEnrollment.state() == TvEnrollment.State.VALID ? "TV: paired, disconnected" : "TV: not paired";
        return new Snapshot(status, tvEnrollment.record() == null ? null : tvEnrollment.record().address(),
            session.savedPeer() == null, enrollment.requiresReenrollment(),
            attached && attended() && !enrollment.failed() && !pendingTvForget
                && tvEnrollment.state() != TvEnrollment.State.UNUSABLE && (run == null || run.stopping),
            attended() && registered && session.savedPeer() == null && run.hid != null,
            registered && session.canConfirm(run.generation),
            attached && attended() && session.savedPeer() != null && !enrollment.failed() && !pendingTvForget
                && tvEnrollment.state() != TvEnrollment.State.UNUSABLE && (run == null || run.stopping), ready,
            attached && resumed && NATIVE_RUNS.available() && !pendingTvForget && tvEnrollment.state() != TvEnrollment.State.UNUSABLE,
            registered && !pendingTvForget && session.canPairTv(run.generation), code,
            attached && resumed && !pendingTvForget,
            ready && run.tvMode == TvEnrollment.State.VALID && run.tv != null,
            tvConnected, tvStatus, codeRevision, gestureRevision, windowStarted && run != null && run.stopping, serviceDestroyed,
            attached ? inputVisibility.epoch() : 0, connected, wizardSnapshot());
    }

    long deadlineMillis() { return lifetime.deadlineMillis(); }
    boolean sessionAlive() { return lifetime.alive(); }
    String notificationText() {
        if (run == null || run.stopping) return "Stopping. Input disabled.";
        if (!lifetime.control()) return "Attended setup. Input disabled until connected.";
        return inputVisibility.epoch() == 0 ? "Connected, input paused. Open remote or Stop."
            : "Connected. Input enabled only on the visible remote.";
    }

    private void connectRemote() {
        if (session.savedPeer() == null) { setStatus("Set up the Bluetooth projector in Settings first."); return; }
        if (run == null || run.stopping) requestStart(true);
        if (run == null || !run.active() || lifetime.control()) return;
        run.connecting = true;
        if (session.isRegistered(run.generation)) connectSaved();
        else run.connectOnRegister = true;
    }

    private String savedStatus() {
        return session.savedPeer() == null ? "No saved projector. Open Settings to set up the projector."
            : "Saved projector — disconnected. Tap Connect.";
    }

    private String storageFailure() {
        return "Enrollment storage failed. Input disabled; disk may still contain the old selector. "
            + "Retry Forget successfully or clear app storage in Android settings before re-enrolling.";
    }

    private boolean permissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStart(boolean controlAttempt) {
        requestStart(controlAttempt, SetupIntent.NONE);
    }

    private void requestStart(boolean controlAttempt, SetupIntent setupIntent) {
        if (!resumed || enrollment.failed() || pendingTvForget) return;
        if (tvEnrollment.state() == TvEnrollment.State.UNUSABLE) {
            setStatus("TV enrollment is unusable. Forget TV controls successfully before another run.");
            return;
        }
        if (!NATIVE_RUNS.available()) {
            setStatus("Previous native teardown is pending. Try again after it completes. "
                + "If it remains blocked, force stop Open TV in Android settings, then reopen.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Nearby devices permission is required.");
            return;
        }
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            setStatus("Turn Bluetooth on, then try again.");
            return;
        }
        if (session.savedPeer() != null && adapter.getRemoteDevice(session.savedPeer()).getBondState()
                != BluetoothDevice.BOND_BONDED) {
            forget();
            setStatus(enrollment.failed() ? storageFailure() : "Saved bond missing. Fresh comparison required.");
            return;
        }
        NativeRunGate.Run ownership = NATIVE_RUNS.start();
        if (ownership == null) return;
        lifetime.beginSetup();
        setInputEligible(false);
        run = new Run(ownership, session.start(setupIntent == SetupIntent.BLUETOOTH ? TvEnrollment.State.ABSENT : tvEnrollment.state()), setupIntent);
        run.connecting = controlAttempt;
        session.foreground(run.generation, attended());
        setStatus("Starting connection…");
        run.begin();
    }

    private String setupAddress() {
        if (tvEnrollment.state() == TvEnrollment.State.VALID && tvEnrollment.record() != null)
            return tvEnrollment.record().address();
        String saved = setupAddressStore.address();
        return saved == null ? "" : saved;
    }

    private SetupState.Snapshot wizardSnapshot() {
        advanceSetupCleanup();
        boolean bluetooth = session.savedPeer() != null && !enrollment.failed();
        boolean address = TvEnrollment.validAddress(setupAddress());
        boolean tvPaired = tvEnrollment.state() == TvEnrollment.State.VALID;
        long remaining = 0, generation = run == null ? 0 : run.generation;
        boolean discovery = false;
        if (run != null && run.active()) {
            if (run.setupIntent == SetupIntent.BLUETOOTH) {
                if (session.canConfirm(run.generation) && setupPage == SetupState.Page.BT_WAIT)
                    setupPage = SetupState.Page.BT_COMPARE;
                if (setupPage == SetupState.Page.BT_WAIT && run.discoverableUntil > 0)
                    remaining = Math.max(0, Math.min(run.discoverableUntil, lifetime.deadlineMillis()) - SystemClock.elapsedRealtime());
                discovery = run.requestDiscoverable;
            } else if (run.setupIntent == SetupIntent.TV && run.tv != null && run.tv.needsCode()) {
                setupPage = SetupState.Page.TV_CODE;
                remaining = Math.max(0, run.tv.codeDeadlineMillis() - SystemClock.elapsedRealtime());
            }
        }
        boolean closing = setupAfterCleanup != null;
        SetupState.Page page = closing && run != null && run.teardownUnconfirmed ? SetupState.Page.RECOVERY : setupPage;
        boolean enabled = attended() && !surfaceVisible && !closing;
        if (page == SetupState.Page.BT_COMPARE)
            enabled &= run != null && session.canConfirm(run.generation);
        if (page == SetupState.Page.ADDRESS || page == SetupState.Page.BT_START || page == SetupState.Page.TV_START)
            enabled &= NATIVE_RUNS.available();
        if (page == SetupState.Page.TV_CODE)
            enabled &= run != null && run.tv != null && run.tv.needsCode();
        if (enrollment.failed() || tvEnrollment.state() == TvEnrollment.State.UNUSABLE) {
            if (page != SetupState.Page.MANAGE && page != SetupState.Page.OVERVIEW) enabled = false;
        }
        String message = page == SetupState.Page.RECOVERY ? status : setupMessage;
        if (page == SetupState.Page.OVERVIEW && (enrollment.failed() || tvEnrollment.state() == TvEnrollment.State.UNUSABLE))
            message = "Saved setup could not be verified. Review setup before changing or resetting the affected item.";
        return new SetupState.Snapshot(page, bluetooth, address, tvPaired, setupAddress(), message,
            setupDiagnostic, remaining, closing || setupPage == SetupState.Page.TV_CONNECTING,
            enabled, generation, discovery);
    }

    void setupAction(long token, SetupState.Action action, String value) {
        if (!attached(token)) return;
        pendingSetupIntent = SetupIntent.NONE;
        if (action == SetupState.Action.CANCEL || action == SetupState.Action.BACK) {
            clearTvCode(); cancelGesture();
            if (run != null && run.setupIntent != SetupIntent.NONE && !run.stopping)
                closeSetup(SetupState.Page.OVERVIEW, "Setup cancelled. Saved pairings retained.");
            else if (setupAfterCleanup != null) setupAfterCleanup = SetupState.Page.OVERVIEW;
            else setupPage = SetupState.Page.OVERVIEW;
            return;
        }
        if (!attended() || surfaceVisible || setupAfterCleanup != null) return;
        try {
            switch (action) {
                case START, NEXT -> {
                    SetupState.Snapshot state = wizardSnapshot();
                    SetupState.Page target = enrollment.failed() || tvEnrollment.state() == TvEnrollment.State.UNUSABLE
                        ? SetupState.Page.MANAGE : state.complete() ? SetupState.Page.DONE : state.firstMissing();
                    setupMessage = ""; setupDiagnostic = "";
                    if (NATIVE_RUNS.available()) setupPage = target;
                    else closeSetup(target, "Closing the connection safely before continuing setup.");
                }
                case MANAGE -> setupPage = SetupState.Page.MANAGE;
                case OPEN_REMOTE -> setupPage = SetupState.Page.OVERVIEW;
                case BT_PAIR -> startWizardRun(SetupIntent.BLUETOOTH);
                case BT_CONFIRM -> confirmAndConnect();
                case BT_MISMATCH -> closeSetup(SetupState.Page.BT_START,
                    "Pairing cancelled. If you already accepted the system prompt, remove only this test bond on both devices before trying again.");
                case SAVE_ADDRESS -> {
                    if (!NATIVE_RUNS.available()) { closeSetup(SetupState.Page.ADDRESS, "Closing the connection before changing its address."); break; }
                    if (!TvEnrollment.validAddress(value)) { setupMessage = "Enter the projector’s private Wi-Fi IPv4 address, with no port or spaces."; break; }
                    if (tvEnrollment.state() == TvEnrollment.State.UNUSABLE) { setupMessage = "TV pairing could not be verified. Review the TV controls item before changing it."; break; }
                    if (tvEnrollment.state() == TvEnrollment.State.VALID) {
                        if (!tvEnrollment.changeLocator(value)) { setupMessage = "Address could not be saved. TV enrollment is unavailable; review TV controls."; break; }
                        setupAddressStore.save(value);
                    } else if (!setupAddressStore.save(value)) { setupMessage = "Address could not be saved. Try saving it again."; break; }
                    setupPage = tvEnrollment.state() == TvEnrollment.State.VALID ? SetupState.Page.MANAGE : SetupState.Page.TV_START;
                    setupMessage = "Address saved. Saving does not test the connection.";
                }
                case PAIR_TV, RETRY -> startWizardRun(SetupIntent.TV);
                case CONFIRM_TV -> { if (value != null && value.matches("[0-9A-Fa-f]{6}")) confirmTvCode(value); }
                case CHANGE_ADDRESS -> {
                    if (NATIVE_RUNS.available()) setupPage = SetupState.Page.ADDRESS;
                    else closeSetup(SetupState.Page.ADDRESS, "Closing the connection before changing its address.");
                }
                case FORGET_BT, REPAIR_BT -> {
                    setupPage = SetupState.Page.CLOSING;
                    setupAfterCleanup = action == SetupState.Action.REPAIR_BT ? SetupState.Page.BT_START : SetupState.Page.MANAGE;
                    forget();
                    setupMessage = enrollment.failed() ? "Bluetooth setup could not be cleared. Retry Reset successfully before pairing." :
                        "Bluetooth app setup cleared. Remove the old bond on both devices before a fresh comparison. TV pairing is kept.";
                    advanceSetupCleanup();
                }
                case FORGET_TV, REPAIR_TV -> {
                    String address = setupAddress();
                    if (TvEnrollment.validAddress(address)) setupAddressStore.save(address);
                    setupPage = SetupState.Page.CLOSING;
                    setupAfterCleanup = action == SetupState.Action.REPAIR_TV ? SetupState.Page.TV_START : SetupState.Page.MANAGE;
                    setupMessage = "Closing and clearing this app’s TV pairing. Bluetooth pairing is kept.";
                    forgetTv();
                    advanceSetupCleanup();
                }
                default -> { }
            }
        } catch (SecurityException | IllegalStateException | IllegalArgumentException failure) {
            closeSetup(SetupState.Page.OVERVIEW, "Setup operation failed. Check Bluetooth and Nearby devices permission.");
        }
    }

    private void startWizardRun(SetupIntent intent) {
        if (!NATIVE_RUNS.available()) {
            if (run == null || !run.ownership.owns()) return;
            pendingSetupIntent = intent;
            pendingSetupVisit = setupVisit;
            pendingSetupGeneration = run.generation;
            closeSetup(intent == SetupIntent.BLUETOOTH ? SetupState.Page.BT_START : SetupState.Page.TV_START,
                "Closing the previous connection safely before pairing…");
            return;
        }
        if (intent == SetupIntent.TV && (session.savedPeer() == null || !TvEnrollment.validAddress(setupAddress()))) {
            setupPage = wizardSnapshot().firstMissing(); return;
        }
        if (intent == SetupIntent.TV && tvEnrollment.state() != TvEnrollment.State.ABSENT) {
            setupPage = SetupState.Page.MANAGE; setupMessage = "TV setup already exists or requires review. Re-pair replaces it only after confirmation."; return;
        }
        setupDiagnostic = "";
        setupMessage = intent == SetupIntent.BLUETOOTH ? "Preparing the phone for Bluetooth pairing…" : "Requesting a code from the projector…";
        setupPage = intent == SetupIntent.BLUETOOTH ? SetupState.Page.BT_WAIT : SetupState.Page.TV_CONNECTING;
        requestStart(false, intent);
        if (run == null || !run.active() || run.setupIntent != intent) {
            setupPage = intent == SetupIntent.BLUETOOTH ? SetupState.Page.BT_START : SetupState.Page.TV_ERROR;
            setupMessage = status;
        }
    }

    private void closeSetup(SetupState.Page target, String message) {
        setupAfterCleanup = target;
        setupPage = SetupState.Page.CLOSING;
        setupMessage = message;
        if (run != null) { run.requestDiscoverable = false; run.discoveryRequested = false; }
        stop(message);
        advanceSetupCleanup();
    }

    private void advanceSetupCleanup() {
        if (setupAfterCleanup == null || !NATIVE_RUNS.available() || pendingTvForget) return;
        if (pendingSetupIntent != SetupIntent.NONE && (pendingSetupVisit == null
                || !pendingSetupVisit.get() || setupVisit != pendingSetupVisit)) pendingSetupIntent = SetupIntent.NONE;
        if (pendingSetupIntent != SetupIntent.NONE) {
            if (serviceDestroyed || !attended() || surfaceVisible) return;
            SetupIntent next = pendingSetupIntent;
            pendingSetupIntent = SetupIntent.NONE;
            if (run != null && run.generation == pendingSetupGeneration) {
                setupAfterCleanup = null;
                startWizardRun(next);
                return;
            }
        }
        setupPage = setupAfterCleanup;
        setupAfterCleanup = null;
        if (setupPage == SetupState.Page.BT_DONE) setupMessage = "Bluetooth paired. The link closed safely.";
        if (setupPage == SetupState.Page.DONE) setupMessage = "Setup saved. Open the remote and tap Connect when you want to use it.";
    }

    private void setupRegistered(long generation) {
        if (run == null || run.generation != generation || !run.active() || !attended() || surfaceVisible) return;
        if (run.setupIntent == SetupIntent.BLUETOOTH) {
            if (session.savedPeer() != null) { connectSaved(); return; }
            if (run.requestDiscoverable || run.discoveryRequested || run.discoverableUntil > 0) return;
            setupPage = SetupState.Page.BT_WAIT;
            run.requestDiscoverable = true;
            setupMessage = "Approve Android’s visibility request, then choose this phone from the projector.";
        } else if (run.setupIntent == SetupIntent.TV) pairTv(setupAddress());
    }

    boolean takeDiscoverabilityRequest(long token, long generation) {
        if (!attached(token) || !attended() || surfaceVisible || run == null || run.generation != generation
                || !run.active() || run.setupIntent != SetupIntent.BLUETOOTH || !run.requestDiscoverable) return false;
        run.requestDiscoverable = false;
        run.discoveryRequested = true;
        return true;
    }

    void discoveryResult(long token, long generation, int grantedSeconds) {
        if (!attached(token) || !attended() || surfaceVisible || run == null || run.generation != generation
                || !run.active() || run.setupIntent != SetupIntent.BLUETOOTH || !run.discoveryRequested) return;
        run.discoveryRequested = false;
        if (grantedSeconds <= 0) { closeSetup(SetupState.Page.BT_START, "Visibility was not approved. Start pairing to try again."); return; }
        run.discoverableUntil = Math.min(lifetime.deadlineMillis(), SystemClock.elapsedRealtime() + Math.min(120, grantedSeconds) * 1000L);
        setupMessage = "Select this phone from the projector’s Bluetooth list, then compare the numbers on both screens.";
    }

    private void bluetoothSetupConnected(long generation) {
        if (run == null || run.generation != generation || !run.active() || run.setupIntent != SetupIntent.BLUETOOTH) return;
        setupPage = SetupState.Page.BT_CLOSING;
        setupAfterCleanup = SetupState.Page.BT_DONE;
        setupMessage = "Bluetooth saved. Closing the link safely…";
        stop(setupMessage);
    }

    private void refreshSetupEligibility() {
        setInputEligible(false);
        if (surfaceVisible) { pendingSetupIntent = SetupIntent.NONE; stop("Setup closed. Saved pairings retained; retry explicitly."); return; }
        session.foreground(run.generation, attended());
        if (!session.active(run.generation)) { stop("Setup left the foreground. Saved pairings retained."); return; }
        if (run.setupIntent == SetupIntent.TV && !attended()) { stop("TV pairing left the foreground. Code cleared."); return; }
        if (run.setupIntent == SetupIntent.BLUETOOTH && !attended() && !run.discoveryRequested && run.discoverableUntil == 0) {
            run.requestDiscoverable = false;
            stop("Setup left the foreground before visibility was requested."); return;
        }
        if (run.setupIntent == SetupIntent.BLUETOOTH && setupPage == SetupState.Page.BT_WAIT
                && run.discoverableUntil > 0 && SystemClock.elapsedRealtime() >= run.discoverableUntil && run.candidate == null)
            closeSetup(SetupState.Page.BT_START, "Visibility window expired. Start pairing to try again.");
        if (connectionService != null) connectionService.scheduleDeadline();
    }

    private boolean canAttempt() {
        if (!permissions() || adapter == null || !adapter.isEnabled()) {
            stop("Nearby devices permission or Bluetooth unavailable.");
            return false;
        }
        return run != null && run.active() && attended() && run.hid != null;
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
        setStatus(cleared ? "App record cleared. Remove the old bond in OS settings before fresh comparison."
            : storageFailure());
    }

    private void pairTv(String address) {
        if (!canAttempt() || !session.canPairTv(run.generation)) return;

        if (!TvEnrollment.validAddress(address)) {
            setStatus("Enter a private Wi-Fi IPv4 address in Setup before pairing."); return;
        }
        if (!session.beginTvPair(run.generation)) return;
        clearTvCode();
        try {
            run.tv = run.createTv();
            setStatus("Starting TV pairing. No input is enabled during this run.");
            run.tv.beginPair(address);
        } catch (RuntimeException failure) {
            run.ownership.fail();
            stop("TV pairing setup failed. Native teardown unconfirmed.");
        }
    }

    private void confirmTvCode(String code) {
        if (!canAttempt() || run.tv == null || !run.tv.needsCode()) return;
        clearTvCode();
        try { run.tv.finishPair(code); }
        catch (RuntimeException failure) { stop("TV code submission failed. Start a new pairing run."); }
    }

    private void saveTvAddress(String address) {
        if (!resumed || !NATIVE_RUNS.available() || pendingTvForget) return;

        if (!TvEnrollment.validAddress(address)) {
            setStatus("Use a private IPv4 address, with no spaces or port."); return;
        }
        if (tvEnrollment.state() == TvEnrollment.State.VALID) {
            setStatus(tvEnrollment.changeLocator(address)
                ? "TV address saved. Certificate identity is unchanged; tap Connect on Remote."
                : "TV address could not be saved. Forget TV controls successfully before retrying.");
        } else if (tvEnrollment.state() == TvEnrollment.State.ABSENT) {
            setStatus("Address ready. Tap Start setup, then Pair TV.");
        }
    }

    private void forgetTv() {
        if (!NATIVE_RUNS.available() && (run == null || !run.ownership.owns())) {
            setStatus("Previous native teardown is pending. Retry Forget TV controls after it completes.");
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
        String message = cleared ? "TV controls forgotten. Bluetooth enrollment is retained."
            : "TV removal is unconfirmed. Input blocked; retry Forget TV controls successfully.";
        setStatus(message);
        if (setupAfterCleanup != null) {
            setupMessage = message;
            if (!cleared) setupAfterCleanup = SetupState.Page.MANAGE;
        }
    }

    private void tvCommand(TvCommand command, long epoch) {
        cancelGesture();
        if (!canAttempt() || !run.ready() || run.tv == null) return;
        run.tv.submit(command, epoch);
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

    void stop(String message) {
        setInputEligible(false);
        lifetime.end();
        cancelGesture();
        clearTvCode();
        if (run != null && !run.stopping && run.ownership.owns()) run.stop(message);
        else if (run == null || !run.stopping) {
            session.stop();
            setStatus(message);
        }
    }

    private void scroll(MouseReport.Direction direction) {
        InputSubmission.guard(() -> {
            if (run != null) run.scrolling.click(direction, SystemClock.uptimeMillis());
        }, () -> stop("Scroll input failed. Check Nearby devices permission and Bluetooth."));
    }

    /** All native callbacks capture this immutable owner, never a later run or UI attachment. */
    private final class Run {
        final NativeRunGate.Run ownership;
        final long generation;
        final TvEnrollment.State tvMode;
        final SetupIntent setupIntent;
        boolean requestDiscoverable, discoveryRequested, teardownUnconfirmed;
        long discoverableUntil;
        TvRemoteClient tv;
        final ProbeTrace trace = new ProbeTrace(SystemClock.elapsedRealtime());
        BluetoothHidDevice hid;
        BluetoothDevice candidate;
        BluetoothDevice target;
        BluetoothDevice connected;
        boolean registrationRequested;
        boolean connecting, connectOnRegister;
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
                teardownUnconfirmed = true;
                setStatus(terminalReason + " Native teardown is unconfirmed. Input disabled. "
                    + "Force stop Open TV in Android settings, then reopen before connecting again.");
            }
        }

        Run(NativeRunGate.Run ownership, long generation, SetupIntent setupIntent) {
            this.ownership = ownership;
            this.generation = generation;
            this.setupIntent = setupIntent;
            tvMode = setupIntent == SetupIntent.BLUETOOTH ? TvEnrollment.State.ABSENT : tvEnrollment.state();
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

        final ScrollInput scrolling = new ScrollInput(() -> cancelGesture(), () -> canAttempt() && ready(),
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
            return ownership.active() && session.active(generation) && connectionService != null && connectionService.isAlive();
        }

        void begin() {
            ownership.await(SERVICE);
            windowStarted = true;
            try {
                connectionService.startForegroundService(new Intent(connectionService, RemoteConnectionService.class));
                connectionService.beginWindow();
                if (!active()) { stop("Pairing service unavailable."); return; }
                handler.postDelayed(registrationDeadline, 5000);
                ownership.await(PROFILE);
                setStatus("Requesting HID service…");
                if (!adapter.getProfileProxy(context, service, BluetoothProfile.HID_DEVICE)) {
                    ownership.completed(PROFILE);
                    stop("HID profile unavailable.");
                }
            } catch (SecurityException | IllegalStateException error) {
                ownership.fail();
                stop("Pairing service start failed.");
            }
        }

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
                        connectionService.getMainExecutor(), callback);
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
                setStatus(session.savedPeer() == null ? "HID registered. Make visible, then pair from the XGIMI."
                    : "Saved projector — disconnected. Tap Connect.");
                if (setupIntent != SetupIntent.NONE) { setupRegistered(generation); return; }
                if (connectOnRegister) { connectOnRegister = false; connectSaved(); }
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
                        if (setupIntent == SetupIntent.BLUETOOTH) { bluetoothSetupConnected(generation); return; }
                        setStatus(tvMode == TvEnrollment.State.VALID
                            ? "Mouse and TV connected. Both controls are ready."
                            : "Mouse connected. TV controls are not paired.");
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (session.connected(generation, peer.getAddress(), false)) stop("Projector disconnected. No input enabled.");
                    }
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
            return inputVisibility.epoch() != 0 && transportReady() && session.canSend(generation, connected.getAddress());
        }

        boolean transportReady() {
            if (!active() || hid == null || connected == null) return false;
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
            return session.transportReady(generation, connected.getAddress());
        }

        TvRemoteClient createTv() {
            ownership.await(TV);
            return new TvRemoteClient(SystemClock::elapsedRealtime, lifetime::deadlineMillis, inputVisibility, new TvRemoteClient.Listener() {
                public void needCode() { handler.post(() -> {
                    if (!active() || !resumed || stopping) return;
                    setStatus("Enter the six-character code shown on the intended projector.");
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
                    if (setupIntent == SetupIntent.TV) {
                        setupAfterCleanup = SetupState.Page.DONE;
                        setupPage = SetupState.Page.TV_CLOSING;
                        setupMessage = "TV controls paired. Closing safely…";
                    }
                    stop("TV controls paired. Open Remote and tap Connect.");
                }); }
                public void failed(TvRemoteClient.Stage stage) { handler.post(() -> {
                    if (!ownership.owns() || stopping) return;
                    if (setupIntent == SetupIntent.TV) {
                        setupDiagnostic = stage.name();
                        String explanation = switch (stage) {
                            case PAIRING_CONNECT, CONTROL_CONNECT -> "Can’t reach the projector. Check its own address and Wi-Fi network. Bluetooth pairing is kept.";
                            case PAIRING_TLS, SESSION_VALIDATION, CONTROL_TLS, IDENTITY -> "TV identity or secure connection could not be verified. Don’t reset pairing to fix a network problem.";
                            case CODE -> "Code entry did not finish. Request a new code and check the intended projector. Bluetooth pairing is kept.";
                            default -> "TV pairing did not finish. Try again after cleanup; Bluetooth pairing is kept.";
                        };
                        closeSetup(SetupState.Page.TV_ERROR, explanation);
                    } else stop("TV connection or pairing failed at " + stage.name() + ". Both channels stopped.");
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
            connecting = true;
            if (tvMode == TvEnrollment.State.ABSENT) { connect(peer); return; }
            if (tvMode != TvEnrollment.State.VALID || tvEnrollment.record() == null) {
                stop("TV enrollment unavailable. Forget TV controls before retrying."); return;
            }
            target = peer;
            try {
                tv = createTv();
                setStatus("Verifying TV connection before connecting the mouse…");
                tv.connect(tvEnrollment.record());
            } catch (RuntimeException failure) {
                ownership.fail();
                stop("TV connection setup failed. Native teardown unconfirmed.");
            }
        }

        void connect(BluetoothDevice peer) {
            if (!active() || !resumed) { stop("Run ended before connect."); return; }
            target = peer;
            setStatus("Connecting to the selected projector…");
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
            if (setupIntent != SetupIntent.NONE && setupAfterCleanup == null) {
                setupAfterCleanup = setupIntent == SetupIntent.BLUETOOTH ? SetupState.Page.BT_START : SetupState.Page.TV_ERROR;
                setupPage = setupIntent == SetupIntent.BLUETOOTH ? SetupState.Page.BT_CLOSING : SetupState.Page.TV_CLOSING;
                setupMessage = message;
            }
            requestDiscoverable = false;
            discoveryRequested = false;
            setInputEligible(false);
            lifetime.end();
            cancelGesture();
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
            setStatus(terminalReason + " Waiting for native teardown…");
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
            if (connectionService != null) connectionService.finish("Probe stopped.");
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
                setStatus(terminalReason + " Native teardown completed. Tap Connect when ready.");
            }
            completePendingTvForget();
        }
    }

    final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || Intent.ACTION_SCREEN_ON.equals(intent.getAction())
                        || Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        focused = false;
                        setInputEligible(false);
                    }
                    deferVisibilityRefresh(); return;
                }
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
                // Observe selected-peer revocation throughout this service's lifetime, even after Stop.
                if ((pairing || (bondChange && bond != BluetoothDevice.BOND_BONDED)) && session.revoke(address)) {
                    stop(enrollment.failed() ? storageFailure() : "Saved projector revoked. Fresh comparison required.");
                    return;
                }
                if (run == null || !run.active()) return;
                if (pairing) {
                    int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                    if (!session.observePairing(run.generation, address, variant)) {
                        if (!session.active(run.generation)) { stop("Pairing changed. No input enabled."); return; }
                        setStatus("Pairing method/peer not eligible. Cancel unless both screens show matching numbers.");
                    } else {
                        run.candidate = peer;
                        setStatus("Compare digits on BOTH intended screens before accepting. Then confirm below.");
                    }
                } else if (bondChange && run.candidate != null && run.candidate.equals(peer)) {
                    if (bond == BluetoothDevice.BOND_BONDED) {
                        session.bonded(run.generation, address);
                        setStatus("Bond created. Confirm ONLY if you compared matching codes on both screens.");
                    } else if (bond != BluetoothDevice.BOND_BONDING) stop("Pairing cancelled or bond removed.");
                }
            } catch (SecurityException | IllegalStateException error) { stop("Bluetooth pairing check failed."); }
        }
    };

}

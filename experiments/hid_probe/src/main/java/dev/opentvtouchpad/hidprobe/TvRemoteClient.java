package dev.opentvtouchpad.hidprobe;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/** A single transport run's attempt. Callbacks must only post bounded owner-tagged work. */
final class TvRemoteClient {
    // Closed diagnostic vocabulary: no peer data or exception text crosses the callback.
    enum Stage {
        IDENTITY, PAIRING_CONNECT, PAIRING_TLS, SESSION_VALIDATION,
        PAIRING_EXCHANGE, CODE, CONTROL_CONNECT, CONTROL_TLS, CONTROL_EXCHANGE
    }
    interface Listener {
        void needCode();
        void connected();
        void enrolled(TvEnrollment.Record record);
        void failed(Stage stage);
        void closed();
    }

    interface NativeIo {
        KeyStore.PrivateKeyEntry identity(boolean fresh) throws Exception;
        SSLSocket pairingSocket(KeyStore.PrivateKeyEntry identity) throws Exception;
        SSLSocket clientSocket(KeyStore.PrivateKeyEntry identity, X509Certificate peer) throws Exception;
    }

    private final Object gate = new Object();
    private final LongSupplier clock;
    private final LongSupplier deadline;
    private final InputVisibilityGate visibility;
    private final Listener listener;
    private final NativeIo nativeIo;
    private final ArrayDeque<Frame> replies = new ArrayDeque<>();
    private final Thread operation, writer, coordinator;
    private boolean launched, attempted, stopped, failed, cleanupFailed, pairing, ready, needsCode;
    private Stage stage = Stage.IDENTITY, failureStage;
    private String address;
    private TvEnrollment.Record enrollment;
    private SSLSocket socket;
    private char[] code;
    private Frame pending;
    private TvControlProtocol.ImeField imeField;
    private boolean imeSupported;
    /** The TV may hold OK while START_LONG is being written or after it completes. */
    private enum Hold { NONE, QUEUED, WRITING_START, SENT }
    static final long MIN_HOLD_MILLIS = 800, MAX_HOLD_MILLIS = 1500;
    private Hold hold = Hold.NONE;
    private boolean releaseRequested, releasePending;
    private long holdEpoch, releaseAfter, holdUntil;
    private Frame inFlightUser;
    private long nextUserAt, phaseUntil, writeUntil, readAt;
    private long readCredit = 2000;
    private int outstandingReplies;

    TvRemoteClient(LongSupplier monotonicMillis, LongSupplier deadline, InputVisibilityGate visibility, Listener listener) {
        this(monotonicMillis, deadline, visibility, listener, new NativeIo() {
            public KeyStore.PrivateKeyEntry identity(boolean fresh) throws Exception {
                if (fresh) {
                    TvIdentity.delete(TvEnrollment.KEY_ALIAS);
                    return TvIdentity.create(TvEnrollment.KEY_ALIAS);
                }
                return TvIdentity.load(TvEnrollment.KEY_ALIAS);
            }
            public SSLSocket pairingSocket(KeyStore.PrivateKeyEntry identity) throws Exception {
                return TvTls.pairingSocket(identity);
            }
            public SSLSocket clientSocket(KeyStore.PrivateKeyEntry identity, X509Certificate peer) throws Exception {
                return TvTls.clientSocket(identity, peer);
            }
        });
    }

    TvRemoteClient(LongSupplier monotonicMillis, LongSupplier deadline, InputVisibilityGate visibility, Listener listener, NativeIo nativeIo) {
        this.clock = Objects.requireNonNull(monotonicMillis);
        this.deadline = Objects.requireNonNull(deadline);
        this.visibility = Objects.requireNonNull(visibility);
        this.listener = Objects.requireNonNull(listener);
        this.nativeIo = Objects.requireNonNull(nativeIo);
        phaseUntil = Long.MAX_VALUE;
        operation = thread("tv-operation", this::operate);
        writer = thread("tv-writer", this::writeLoop);
        coordinator = thread("tv-watchdog", this::supervise);
    }

    void beginPair(String locator) { begin(locator, null, true); }
    void connect(TvEnrollment.Record record) { begin(record == null ? null : record.address(), record, false); }

    private void begin(String locator, TvEnrollment.Record record, boolean pair) {
        synchronized (gate) {
            if (attempted || stopped) return;
            attempted = true;
            pairing = pair;
            address = locator;
            enrollment = record;
            readAt = clock.getAsLong();
            phaseUntil = readAt + 12000;
            launch();
        }
    }

    void finishPair(String value) {
        boolean invalid = false;
        synchronized (gate) {
            if (stopped) return;
            if (!needsCode || value == null || !value.matches("[0-9A-Fa-f]{6}") || !live()) invalid = true;
            else {
                code = value.toCharArray();
                needsCode = false;
                gate.notifyAll();
            }
        }
        if (invalid) terminate(true);
    }

    boolean submit(TvCommand command, long epoch) {
        return queue(command, epoch, dev.opentvtouchpad.hidprobe.wire.RemoteProto.RemoteDirection.SHORT);
    }

    TvControlProtocol.ImeField imeField() {
        synchronized (gate) { return live() && ready && !pairing ? imeField : null; }
    }

    boolean imeSupported() {
        synchronized (gate) { return live() && ready && !pairing && imeSupported; }
    }

    boolean submitText(TvControlProtocol.ImeField field, String text, long epoch) {
        if (field == null || !TvControlProtocol.ImeField.accepts(text)) return false;
        synchronized (gate) {
            long now = clock.getAsLong();
            if (field != imeField || !canQueue(epoch, now)) return false;
            queueFrame(field.insert(text), epoch, now, field);
            return true;
        }
    }

    /** Starts an OK long press; the TV holds OK for MIN_HOLD..MAX_HOLD after START_LONG is written. */
    boolean startHold(TvCommand command, long epoch) {
        if (command != TvCommand.OK) return false;
        synchronized (gate) {
            if (hold != Hold.NONE) return false;
            if (!queue(command, epoch, dev.opentvtouchpad.hidprobe.wire.RemoteProto.RemoteDirection.START_LONG)) return false;
            hold = Hold.QUEUED; holdEpoch = epoch; releaseRequested = false; releasePending = false;
            return true;
        }
    }

    /** Finger lifted or input ended: release once the minimum hold has elapsed. Idempotent. */
    void endHold() {
        synchronized (gate) {
            if (hold == Hold.QUEUED) { discardPending(); hold = Hold.NONE; }
            else if (hold != Hold.NONE && !releasePending) releaseRequested = true;
            gate.notifyAll();
        }
    }

    boolean holding() { synchronized (gate) { return hold != Hold.NONE; } }

    private boolean queue(TvCommand command, long epoch, dev.opentvtouchpad.hidprobe.wire.RemoteProto.RemoteDirection direction) {
        synchronized (gate) {
            long now = clock.getAsLong();
            if (command == null || !canQueue(epoch, now)) return false;
            queueFrame(command.message(direction).toByteArray(), epoch, now, null);
            return true;
        }
    }

    private boolean canQueue(long epoch, long now) {
        return visibility.accepts(epoch) && live() && ready && !pairing
            && pending == null && hold == Hold.NONE && now >= nextUserAt;
    }

    private void queueFrame(byte[] bytes, long epoch, long now, TvControlProtocol.ImeField field) {
        pending = new Frame(socket, bytes, true, now, epoch, field);
        nextUserAt = now + 100;
        gate.notifyAll();
    }

    private void discardPending() {
        if (pending != null) Arrays.fill(pending.bytes, (byte)0);
        pending = null;
    }

    boolean ready() { synchronized (gate) { return live() && ready; } }
    long codeDeadlineMillis() {
        synchronized (gate) { return live() && needsCode ? Math.min(phaseUntil, deadline.getAsLong()) : 0; }
    }
    boolean needsCode() { synchronized (gate) { return live() && needsCode; } }
    void stop() { terminate(false); }
    void inputRevoked() {
        synchronized (gate) {
            discardPending();
            if (hold == Hold.QUEUED) hold = Hold.NONE;
            // Close if START_LONG may have reached the TV and release is no longer admitted.
            if (inFlightUser != null || hold != Hold.NONE) terminate(true);
            gate.notifyAll();
        }
    }

    private void launch() {
        if (launched) return;
        launched = true;
        operation.start(); writer.start(); coordinator.start();
    }

    private static Thread thread(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    // gate guards admission/state only; no socket or key operation holds it.
    private boolean live() {
        long now = clock.getAsLong();
        return !stopped && now < deadline.getAsLong() && now < phaseUntil;
    }
    private void requireLive() throws IOException { if (!live()) throw new IOException("TV operation ended"); }
    private void phase(long milliseconds) throws IOException {
        synchronized (gate) { requireLive(); phaseUntil = clock.getAsLong() + milliseconds; gate.notifyAll(); }
    }

    private void stage(Stage next) {
        synchronized (gate) { if (!stopped) stage = next; }
    }

    private void operate() {
        try {
            synchronized (gate) {
                if (stopped) return;
                requireLive();
                if (!TvEnrollment.validAddress(address) || (!pairing && enrollment == null)) throw new IOException("Invalid TV locator");
            }
            KeyStore.PrivateKeyEntry identity = nativeIo.identity(pairing);
            synchronized (gate) { requireLive(); }
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(identity.getCertificate().getEncoded());
            if (!pairing && !MessageDigest.isEqual(fingerprint, enrollment.clientFingerprint()))
                throw new IOException("TV identity changed");
            X509Certificate peer;
            if (pairing) peer = pair(identity);
            else peer = TvTls.certificate(enrollment.peerCertificate());
            synchronized (gate) { requireLive(); }
            phase(12000);
            SSLSocket control = open(identity, peer);
            TvEnrollment.Record candidate = pairing ? new TvEnrollment.Record(address, fingerprint, peer.getEncoded()) : null;
            control(control, candidate);
        } catch (Exception failure) {
            terminate(true);
        } finally {
            synchronized (gate) { if (code != null) Arrays.fill(code, '\0'); code = null; }
        }
    }

    private X509Certificate pair(KeyStore.PrivateKeyEntry identity) throws Exception {
        SSLSocket pair = open(identity, null);
        stage(Stage.SESSION_VALIDATION);
        SSLSession session = pair.getSession();
        Certificate[] local = session.getLocalCertificates();
        Certificate[] remote = session.getPeerCertificates();
        if (local == null || local.length == 0 || remote.length == 0
                || !(local[0].getPublicKey() instanceof RSAPublicKey)
                || !(remote[0] instanceof X509Certificate)
                || !(remote[0].getPublicKey() instanceof RSAPublicKey)
                || !MessageDigest.isEqual(local[0].getEncoded(), identity.getCertificate().getEncoded()))
            throw new IOException("Invalid TV pairing session");
        X509Certificate peer = (X509Certificate) remote[0];
        stage(Stage.PAIRING_EXCHANGE);
        TvPairing protocol = new TvPairing();
        phase(10000);
        sendAndWait(pair, protocol.begin());
        while (!protocol.needsCode()) {
            byte[] reply = protocol.receive(read(pair));
            if (reply != null) { phase(10000); sendAndWait(pair, reply); }
        }
        stage(Stage.CODE);
        phase(120000);
        char[] entered;
        synchronized (gate) {
            requireLive(); needsCode = true;
            listener.needCode();
            while (code == null) { requireLive(); gate.wait(); }
            requireLive(); entered = code; code = null; needsCode = false;
        }
        byte[] secret;
        try { secret = protocol.code(new String(entered), (RSAPublicKey)local[0].getPublicKey(), (RSAPublicKey)peer.getPublicKey()); }
        finally { Arrays.fill(entered, '\0'); }
        stage(Stage.PAIRING_EXCHANGE);
        phase(10000);
        sendAndWait(pair, secret);
        protocol.receive(read(pair));
        if (!protocol.done()) throw new IOException("TV pairing incomplete");
        closeSocket(pair);
        synchronized (gate) {
            requireLive();
            if (!pair.isClosed()) throw new IOException("TV pairing socket remains open");
            if (socket == pair) socket = null;
        }
        return peer;
    }

    private SSLSocket open(KeyStore.PrivateKeyEntry identity, X509Certificate peer) throws Exception {
        stage(peer == null ? Stage.PAIRING_CONNECT : Stage.CONTROL_CONNECT);
        synchronized (gate) { requireLive(); }
        SSLSocket created = peer == null ? nativeIo.pairingSocket(identity) : nativeIo.clientSocket(identity, peer);
        if (created == null) throw new IOException("TV socket unavailable");
        boolean cancelled;
        synchronized (gate) {
            if (socket != null) throw new IOException("TV socket already owned");
            socket = created; cancelled = !live();
        }
        if (cancelled) { closeSocket(created); throw new IOException("TV operation ended"); }
        String[] parts = address.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) bytes[i] = (byte)Integer.parseInt(parts[i]);
        int remaining;
        synchronized (gate) { requireLive(); remaining = (int)Math.max(1, Math.min(phaseUntil, deadline.getAsLong())-clock.getAsLong()); }
        created.connect(new InetSocketAddress(InetAddress.getByAddress(bytes), peer == null ? 6467 : 6466), remaining);
        synchronized (gate) { requireLive(); remaining = (int)Math.max(1, Math.min(phaseUntil, deadline.getAsLong())-clock.getAsLong()); }
        stage(peer == null ? Stage.PAIRING_TLS : Stage.CONTROL_TLS);
        created.setSoTimeout(remaining);
        created.startHandshake();
        synchronized (gate) { requireLive(); }
        return created;
    }

    private byte[] read(SSLSocket current) throws Exception {
        int remaining;
        synchronized (gate) { requireLive(); remaining = (int)Math.max(1, Math.min(phaseUntil, deadline.getAsLong())-clock.getAsLong()); }
        current.setSoTimeout(remaining);
        byte[] frame = TvWire.read(current.getInputStream());
        synchronized (gate) {
            requireLive();
            long now = clock.getAsLong();
            readCredit = Math.min(2000, readCredit + Math.max(0, now-readAt));
            readAt = now;
            if (readCredit < 10) throw new IOException("TV read rate exceeded");
            readCredit -= 10;
        }
        return frame;
    }

    private void control(SSLSocket current, TvEnrollment.Record candidate) throws Exception {
        stage(Stage.CONTROL_EXCHANGE);
        TvControlProtocol protocol = new TvControlProtocol(candidate == null);
        boolean announced = false;
        while (true) {
            byte[] reply = protocol.receive(read(current));
            synchronized (gate) {
                requireLive();
                imeField = protocol.imeField();
                if (pending != null && pending.field != null && pending.field != imeField) discardPending();
            }
            if (reply != null) enqueue(current, reply);
            if (!announced && protocol.ready()) {
                synchronized (gate) {
                    while (outstandingReplies != 0) { requireLive(); gate.wait(); }
                    requireLive(); ready = true; announced = true;
                    imeSupported = protocol.imeSupported();
                    phaseUntil = clock.getAsLong()+30000;
                    if (candidate == null) listener.connected();
                    else listener.enrolled(candidate);
                }
            } else if (announced) phase(30000);
        }
    }

    private Frame enqueue(SSLSocket current, byte[] bytes) throws IOException {
        synchronized (gate) {
            try {
                requireLive();
                if (current != socket || outstandingReplies >= 8) throw new IOException("TV reply backlog exceeded");
                Frame frame = new Frame(current, bytes, false, 0, 0);
                replies.addLast(frame); outstandingReplies++; gate.notifyAll(); return frame;
            } catch (IOException failure) { Arrays.fill(bytes, (byte)0); throw failure; }
        }
    }

    private void sendAndWait(SSLSocket current, byte[] bytes) throws Exception {
        Frame frame = enqueue(current, bytes);
        synchronized (gate) { while (!frame.done) { requireLive(); gate.wait(); } requireLive(); }
    }

    private void writeLoop() {
        try {
            while (true) {
                Frame frame;
                synchronized (gate) {
                    while (!stopped && replies.isEmpty() && pending == null && !releasePending) gate.wait();
                    if (stopped) return;
                    requireLive();
                    if (!replies.isEmpty()) frame = replies.removeFirst();
                    else if (releasePending) {
                        if (!visibility.accepts(holdEpoch)) throw new IOException("TV release without admission");
                        frame = new Frame(socket, TvCommand.OK.message(
                                dev.opentvtouchpad.hidprobe.wire.RemoteProto.RemoteDirection.END_LONG).toByteArray(),
                                true, clock.getAsLong(), holdEpoch);
                        frame.release = true;
                    } else {
                        boolean start = hold == Hold.QUEUED;
                        if (clock.getAsLong()-pending.at > 250 || !visibility.accepts(pending.epoch)) {
                            discardPending();
                            if (start) hold = Hold.NONE;
                            continue;
                        }
                        frame = pending;
                        pending = null;
                        if (start) hold = Hold.WRITING_START;
                    }
                    if (frame.socket != socket) throw new IOException("Stale TV writer");
                    writeUntil = clock.getAsLong()+2000;
                }
                try {
                    OutputStream output = frame.socket.getOutputStream();
                    TvWire.write(new OutputStream() {
                        private boolean admit(boolean sending) throws IOException {
                            synchronized (gate) {
                                requireLive();
                                if (frame.socket != socket || clock.getAsLong() >= writeUntil)
                                    throw new IOException("TV write ended");
                                if (frame.user) {
                                    if (frame.field != null && frame.field != imeField) {
                                        if (!frame.started) return false;
                                        if (sending) throw new IOException("TV text field changed during write");
                                    }
                                    if (!visibility.accepts(frame.epoch)) {
                                        if (!frame.started) return false;
                                        throw new IOException("TV input revoked during write");
                                    }
                                    if (sending && (!ready || pairing || clock.getAsLong()-frame.at > 250))
                                        throw new IOException("TV command expired");
                                    if (sending) {
                                        frame.started = true;
                                        inFlightUser = frame;
                                    }
                                }
                                return true;
                            }
                        }
                        public void write(int value) throws IOException { if (admit(true)) output.write(value); }
                        public void write(byte[] bytes, int offset, int length) throws IOException { if (admit(true)) output.write(bytes, offset, length); }
                        public void flush() throws IOException { if (admit(false)) output.flush(); }
                    }, frame.bytes);
                } catch (Exception failure) {
                    // Fence failure before completion can wake pairing/readiness waiters.
                    terminate(true);
                    return;
                } finally {
                    Arrays.fill(frame.bytes, (byte)0);
                    synchronized (gate) {
                        // Epoch loss during the final native flush is still an uncertain user frame.
                        if (frame.started && !visibility.accepts(frame.epoch)) terminate(true);
                        writeUntil = 0; frame.done = true;
                        if (inFlightUser == frame) inFlightUser = null;
                        if (hold == Hold.WRITING_START) {
                            hold = frame.started ? Hold.SENT : Hold.NONE;
                            if (frame.started) {
                                long sentAt = clock.getAsLong();
                                releaseAfter = sentAt + MIN_HOLD_MILLIS;
                                holdUntil = sentAt + MAX_HOLD_MILLIS;
                            }
                        }
                        if (frame.release && !stopped) {
                            hold = Hold.NONE;
                            releasePending = false;
                        }
                        if (!frame.user) outstandingReplies--;
                        gate.notifyAll();
                    }
                }
            }
        } catch (Exception failure) { terminate(true); }
    }

    private void terminate(boolean error) {
        synchronized (gate) {
            if (!stopped) {
                if (error) failureStage = stage;
                stopped = true; failed = error; ready = false; needsCode = false; imeField = null;
                discardPending();
                hold = Hold.NONE; releaseRequested = false; releasePending = false;
                if (code != null) Arrays.fill(code, '\0'); code = null;
                while (!replies.isEmpty()) { Arrays.fill(replies.removeFirst().bytes, (byte)0); outstandingReplies--; }
                gate.notifyAll();
            }
            launch();
        }
    }

    private void closeSocket(SSLSocket owned) {
        try { owned.close(); }
        catch (IOException | RuntimeException failure) {
            synchronized (gate) { if (!owned.isClosed()) cleanupFailed = true; }
        }
    }

    private void supervise() {
        try {
            while (true) {
                boolean expired;
                synchronized (gate) {
                    if (stopped) break;
                    expired = !live() || (writeUntil != 0 && clock.getAsLong() >= writeUntil);
                    if (!expired && hold == Hold.SENT && !releasePending) {
                        long now = clock.getAsLong();
                        // Release on lift after the minimum hold, and always by the maximum hold.
                        if (releaseRequested && now >= releaseAfter || now >= holdUntil) {
                            releasePending = true; releaseRequested = false;
                            gate.notifyAll();
                        }
                    }
                    if (!expired) gate.wait(20);
                }
                if (expired) terminate(true);
            }
            Stage reportFailure;
            synchronized (gate) { reportFailure = failed ? failureStage : null; }
            if (reportFailure != null) listener.failed(reportFailure);
        } catch (InterruptedException interrupted) {
            terminate(true);
        } finally {
            SSLSocket owned;
            synchronized (gate) { owned = socket; }
            if (owned != null) closeSocket(owned);
            join(operation); join(writer);
            synchronized (gate) { owned = socket; }
            if (owned != null && !owned.isClosed()) closeSocket(owned);
            synchronized (gate) {
                if (!cleanupFailed && (socket == null || socket.isClosed())) listener.closed();
            }
        }
    }

    private static void join(Thread thread) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            try { thread.join(); }
            catch (InterruptedException retry) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static final class Frame {
        final SSLSocket socket;
        final byte[] bytes;
        final boolean user;
        final long at, epoch;
        final TvControlProtocol.ImeField field;
        boolean done, started, release;
        Frame(SSLSocket socket, byte[] bytes, boolean user, long at, long epoch) {
            this(socket, bytes, user, at, epoch, null);
        }
        Frame(SSLSocket socket, byte[] bytes, boolean user, long at, long epoch, TvControlProtocol.ImeField field) {
            this.socket=socket; this.bytes=bytes; this.user=user; this.at=at; this.epoch=epoch;
            this.field = field;
        }
    }
}

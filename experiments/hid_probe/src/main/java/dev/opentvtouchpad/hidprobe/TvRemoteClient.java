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

/** A single foreground run's attempt. Callbacks must only post bounded owner-tagged work. */
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
    private final long deadline;
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
    private TvCommand pending;
    private long pendingAt, nextUserAt, phaseUntil, writeUntil, readAt;
    private long readCredit = 2000;
    private int outstandingReplies;

    TvRemoteClient(LongSupplier monotonicMillis, long absoluteDeadlineMillis, Listener listener) {
        this(monotonicMillis, absoluteDeadlineMillis, listener, new NativeIo() {
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

    TvRemoteClient(LongSupplier monotonicMillis, long absoluteDeadlineMillis, Listener listener, NativeIo nativeIo) {
        this.clock = Objects.requireNonNull(monotonicMillis);
        this.deadline = absoluteDeadlineMillis;
        this.listener = Objects.requireNonNull(listener);
        this.nativeIo = Objects.requireNonNull(nativeIo);
        phaseUntil = deadline;
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
            phaseUntil = Math.min(deadline, readAt + 12000);
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

    boolean submit(TvCommand command) {
        synchronized (gate) {
            long now = clock.getAsLong();
            if (command == null || !live() || !ready || pairing || pending != null || now < nextUserAt) return false;
            pending = command;
            pendingAt = now;
            nextUserAt = now + 100;
            gate.notifyAll();
            return true;
        }
    }

    boolean ready() { synchronized (gate) { return live() && ready; } }
    boolean needsCode() { synchronized (gate) { return live() && needsCode; } }
    void stop() { terminate(false); }

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
    private boolean live() { return !stopped && clock.getAsLong() < deadline && clock.getAsLong() < phaseUntil; }
    private void requireLive() throws IOException { if (!live()) throw new IOException("TV operation ended"); }
    private void phase(long milliseconds) throws IOException {
        synchronized (gate) { requireLive(); phaseUntil = Math.min(deadline, clock.getAsLong() + milliseconds); gate.notifyAll(); }
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
        synchronized (gate) { requireLive(); remaining = (int)Math.max(1, phaseUntil-clock.getAsLong()); }
        created.connect(new InetSocketAddress(InetAddress.getByAddress(bytes), peer == null ? 6467 : 6466), remaining);
        synchronized (gate) { requireLive(); remaining = (int)Math.max(1, phaseUntil-clock.getAsLong()); }
        stage(peer == null ? Stage.PAIRING_TLS : Stage.CONTROL_TLS);
        created.setSoTimeout(remaining);
        created.startHandshake();
        synchronized (gate) { requireLive(); }
        return created;
    }

    private byte[] read(SSLSocket current) throws Exception {
        int remaining;
        synchronized (gate) { requireLive(); remaining = (int)Math.max(1, phaseUntil-clock.getAsLong()); }
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
        TvControlProtocol protocol = new TvControlProtocol();
        boolean announced = false;
        while (true) {
            byte[] reply = protocol.receive(read(current));
            if (reply != null) enqueue(current, reply);
            if (!announced && protocol.ready()) {
                synchronized (gate) {
                    while (outstandingReplies != 0) { requireLive(); gate.wait(); }
                    requireLive(); ready = true; announced = true;
                    phaseUntil = Math.min(deadline, clock.getAsLong()+30000);
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
                Frame frame = new Frame(current, bytes, false, 0);
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
                    while (!stopped && replies.isEmpty() && pending == null) gate.wait();
                    if (stopped) return;
                    requireLive();
                    if (!replies.isEmpty()) frame = replies.removeFirst();
                    else {
                        TvCommand command = pending; pending = null;
                        if (clock.getAsLong()-pendingAt > 250) continue;
                        frame = new Frame(socket, command.message().toByteArray(), true, pendingAt);
                    }
                    if (frame.socket != socket) throw new IOException("Stale TV writer");
                    writeUntil = Math.min(deadline, clock.getAsLong()+2000);
                }
                try {
                    OutputStream output = frame.socket.getOutputStream();
                    TvWire.write(new OutputStream() {
                        private void admit(boolean sending) throws IOException {
                            synchronized (gate) {
                                requireLive();
                                if (frame.socket != socket || clock.getAsLong() >= writeUntil)
                                    throw new IOException("TV write ended");
                                if (sending && frame.user && (!ready || pairing || clock.getAsLong()-frame.at > 250))
                                    throw new IOException("TV command expired");
                            }
                        }
                        public void write(int value) throws IOException { admit(true); output.write(value); }
                        public void write(byte[] bytes, int offset, int length) throws IOException { admit(true); output.write(bytes, offset, length); }
                        public void flush() throws IOException { admit(false); output.flush(); }
                    }, frame.bytes);
                } catch (Exception failure) {
                    // Fence failure before completion can wake pairing/readiness waiters.
                    terminate(true);
                    return;
                } finally {
                    Arrays.fill(frame.bytes, (byte)0);
                    synchronized (gate) {
                        writeUntil = 0; frame.done = true;
                        if (!frame.user) outstandingReplies--;
                        gate.notifyAll();
                    }
                }
            }
        } catch (Exception failure) { terminate(true); }
    }

    private void terminate(boolean error) {
        SSLSocket owned;
        synchronized (gate) {
            if (!stopped) {
                if (error) failureStage = stage;
                stopped = true; failed = error; ready = false; needsCode = false; pending = null;
                if (code != null) Arrays.fill(code, '\0'); code = null;
                while (!replies.isEmpty()) { Arrays.fill(replies.removeFirst().bytes, (byte)0); outstandingReplies--; }
                gate.notifyAll();
            }
            owned = socket;
            launch();
        }
        if (owned != null) closeSocket(owned);
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
            join(operation); join(writer);
            SSLSocket owned;
            synchronized (gate) { owned = socket; }
            if (owned != null) closeSocket(owned);
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
        final long at;
        boolean done;
        Frame(SSLSocket socket, byte[] bytes, boolean user, long at) {
            this.socket=socket; this.bytes=bytes; this.user=user; this.at=at;
        }
    }
}

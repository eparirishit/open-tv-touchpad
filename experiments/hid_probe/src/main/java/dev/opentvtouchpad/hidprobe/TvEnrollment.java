package dev.opentvtouchpad.hidprobe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Objects;

/** Single-owner durable identity, not authorization. Run owns cancellation/Forget serialization. */
final class TvEnrollment {
    enum State { ABSENT, VALID, UNUSABLE }
    static final String KEY_ALIAS = "open-tv-remote-client-v1";
    static final int MAX_RECORD_BYTES = 16 * 1024;

    /** Missing storage is an empty array; an unknown/unreadable state must throw. */
    interface Disk {
        byte[] read() throws IOException;
        void write(byte[] record) throws IOException;
    }
    interface Identity {
        /** Return the native client certificate SHA-256 only for a usable key; otherwise null/throw. */
        byte[] fingerprint(String alias) throws Exception;
        void delete(String alias) throws Exception;
    }

    static final class Record {
        private final String address;
        private final byte[] clientFingerprint, peerCertificate;
        Record(String address, byte[] clientFingerprint, byte[] peerCertificate) {
            if (!validAddress(address) || clientFingerprint == null || clientFingerprint.length != 32
                    || peerCertificate == null || peerCertificate.length == 0
                    // Three ints, two UTF lengths, and the fingerprint precede the certificate.
                    || peerCertificate.length > MAX_RECORD_BYTES - 48 - address.length() - KEY_ALIAS.length())
                throw new IllegalArgumentException("Invalid TV enrollment");
            this.address = address;
            this.clientFingerprint = clientFingerprint.clone();
            this.peerCertificate = peerCertificate.clone();
            try { TvTls.certificate(this.peerCertificate); }
            catch (CertificateException e) { throw new IllegalArgumentException("Invalid TV certificate", e); }
        }
        String address() { return address; }
        byte[] clientFingerprint() { return clientFingerprint.clone(); }
        byte[] peerCertificate() { return peerCertificate.clone(); }
    }

    private final Disk disk;
    private final Identity identity;
    private State state = State.UNUSABLE;
    private Record record;

    TvEnrollment(Disk disk, Identity identity) {
        this.disk = Objects.requireNonNull(disk);
        this.identity = Objects.requireNonNull(identity);
        try {
            Record loaded = decode(disk.read());
            if (loaded != null) checkIdentity(loaded);
            record = loaded;
            state = loaded == null ? State.ABSENT : State.VALID;
        } catch (Exception failure) { invalidate(); }
    }

    State state() { return state; }
    Record record() { return record; }

    boolean save(Record candidate) {
        if (state != State.ABSENT || candidate == null) return false;
        return replace(candidate);
    }

    boolean forget() {
        invalidate();
        try {
            writeVerified(new byte[0]);
            identity.delete(KEY_ALIAS);
            state = State.ABSENT;
            return true;
        } catch (Exception failure) { return false; }
    }

    boolean changeLocator(String address) {
        if (state != State.VALID || !validAddress(address)) return false;
        try { return replace(new Record(address, record.clientFingerprint, record.peerCertificate)); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private boolean replace(Record candidate) {
        invalidate();
        try {
            checkIdentity(candidate);
            writeVerified(encode(candidate));
            checkIdentity(candidate);
            record = candidate;
            state = State.VALID;
            return true;
        } catch (Exception failure) { return false; }
    }

    private void invalidate() {
        record = null;
        state = State.UNUSABLE;
    }

    private void checkIdentity(Record candidate) throws Exception {
        byte[] actual = identity.fingerprint(KEY_ALIAS);
        if (actual == null || !MessageDigest.isEqual(candidate.clientFingerprint, actual))
            throw new IOException("TV client identity unavailable");
    }

    private void writeVerified(byte[] bytes) throws IOException {
        disk.write(bytes);
        // AtomicFile may log a sync/rename failure without throwing it.
        if (!Arrays.equals(bytes, disk.read())) throw new IOException("TV enrollment write not confirmed");
    }

    private static byte[] encode(Record record) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(1);
        out.writeUTF(record.address);
        out.writeUTF(KEY_ALIAS);
        out.writeInt(record.clientFingerprint.length);
        out.write(record.clientFingerprint);
        out.writeInt(record.peerCertificate.length);
        out.write(record.peerCertificate);
        return bytes.toByteArray();
    }

    private static Record decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_RECORD_BYTES) throw new IOException("Invalid TV record size");
        if (bytes.length == 0) return null;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 1) throw new IOException("Unsupported TV record");
        String address = readAscii(in, 15);
        if (!validAddress(address) || !KEY_ALIAS.equals(readAscii(in, KEY_ALIAS.length())) || in.readInt() != 32)
            throw new IOException("Invalid TV record schema");
        byte[] fingerprint = new byte[32];
        in.readFully(fingerprint);
        int length = in.readInt();
        if (length <= 0 || length != in.available()) throw new IOException("Invalid TV certificate length");
        byte[] certificate = new byte[length];
        in.readFully(certificate);
        Record record = new Record(address, fingerprint, certificate);
        if (!Arrays.equals(bytes, encode(record))) throw new IOException("Noncanonical TV record");
        return record;
    }

    private static String readAscii(DataInputStream in, int maximum) throws IOException {
        int length = in.readUnsignedShort();
        if (length == 0 || length > maximum || length > in.available())
            throw new IOException("Invalid TV record string length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    static boolean validAddress(String address) {
        if (address == null || address.length() > 15
                || !address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = address.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 1 && parts[i].charAt(0) == '0') return false;
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }
        return octets[0] == 10
            || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
            || (octets[0] == 192 && octets[1] == 168);
    }
}

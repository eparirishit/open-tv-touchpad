package dev.opentvtouchpad.hidprobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** One selector, never runtime authorization. Disk failures latch until a verified Forget. */
final class EnrollmentStore {
    interface Disk {
        byte[] read() throws IOException;
        void write(byte[] record) throws IOException;
    }

    private final Disk disk;
    private String peer;
    private boolean failed;
    private boolean requiresReenrollment;

    EnrollmentStore(Disk disk) {
        this.disk = disk;
        try {
            byte[] record = disk.read();
            requiresReenrollment = decode(record, '1') != null;
            peer = decode(record, '2');
        }
        catch (IOException | RuntimeException error) { failed = true; }
    }

    static boolean validPeer(String peer) {
        return peer != null && peer.length() == 17 && peer.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}");
    }

    private static String decode(byte[] record, char version) {
        if (record == null || record.length != 19 || record[0] != version || record[1] != '\n') return null;
        String peer = new String(record, 2, 17, StandardCharsets.US_ASCII);
        return validPeer(peer) ? peer : null;
    }

    String peer() { return failed ? null : peer; }
    boolean failed() { return failed; }
    boolean requiresReenrollment() { return requiresReenrollment; }

    boolean save(String peer) {
        if (failed || !validPeer(peer) || this.peer != null) return false;
        return replace(("2\n" + peer).getBytes(StandardCharsets.US_ASCII), peer);
    }

    boolean forget() { return replace(new byte[0], null); }

    private boolean replace(byte[] record, String selected) {
        peer = null;
        try {
            disk.write(record);
            // AtomicFile reports some sync/rename failures only in platform logs.
            if (!Arrays.equals(record, disk.read())) throw new IOException("Enrollment write not confirmed");
            peer = selected;
            requiresReenrollment = false;
            failed = false;
            return true;
        } catch (IOException | RuntimeException error) {
            failed = true;
            return false;
        }
    }
}

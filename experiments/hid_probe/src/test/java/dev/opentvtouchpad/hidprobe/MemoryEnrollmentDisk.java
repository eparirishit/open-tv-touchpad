package dev.opentvtouchpad.hidprobe;

import java.io.IOException;

/** Only the native AtomicFile boundary is substituted; codec and policy stay real. */
final class MemoryEnrollmentDisk implements EnrollmentStore.Disk {
    byte[] bytes = new byte[0];
    boolean failRead;
    boolean failWrite;
    boolean loseWrite;
    @Override public byte[] read() throws IOException {
        if (failRead) throw new IOException("synthetic read failure");
        return bytes.clone();
    }
    @Override public void write(byte[] record) throws IOException {
        if (failWrite) throw new IOException("synthetic write failure");
        if (!loseWrite) bytes = record.clone();
    }
}

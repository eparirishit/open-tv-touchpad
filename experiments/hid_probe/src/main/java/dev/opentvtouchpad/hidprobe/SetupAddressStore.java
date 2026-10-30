package dev.opentvtouchpad.hidprobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Single-owner non-authoritative locator draft; explicit save may retry a storage failure. */
final class SetupAddressStore {
    private final EnrollmentStore.Disk disk;
    private String address;
    private boolean failed;

    SetupAddressStore(EnrollmentStore.Disk disk) {
        this.disk = disk;
        try {
            byte[] record = disk.read();
            if (record == null || record.length > 17) throw new IOException("Invalid setup address size");
            if (record.length == 0) return;
            if (record.length < 3 || record[0] != '1' || record[1] != '\n')
                throw new IOException("Invalid setup address record");
            String saved = new String(record, 2, record.length - 2, StandardCharsets.US_ASCII);
            if (!TvEnrollment.validAddress(saved)) throw new IOException("Invalid setup address");
            address = saved;
        } catch (IOException | RuntimeException failure) { failed = true; }
    }

    String address() { return failed ? null : address; }
    boolean failed() { return failed; }

    boolean save(String candidate) {
        if (!TvEnrollment.validAddress(candidate)) return false;
        byte[] record = ("1\n" + candidate).getBytes(StandardCharsets.US_ASCII);
        address = null;
        try {
            disk.write(record);
            // AtomicFile can log a sync/rename failure without throwing it.
            if (!Arrays.equals(record, disk.read())) throw new IOException("Setup address write not confirmed");
            address = candidate;
            failed = false;
            return true;
        } catch (IOException | RuntimeException failure) {
            failed = true;
            return false;
        }
    }
}

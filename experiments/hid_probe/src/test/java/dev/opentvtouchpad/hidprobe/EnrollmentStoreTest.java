package dev.opentvtouchpad.hidprobe;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnrollmentStoreTest {
    private static final String PEER = "02:00:00:00:00:01";

    @Test public void onlyVersionAndSelectorSurviveReopen() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        EnrollmentStore store = new EnrollmentStore(disk);
        assertNull(store.peer());
        assertFalse(store.requiresReenrollment());
        assertTrue(store.save(PEER));
        assertArrayEquals("2\n02:00:00:00:00:01".getBytes(StandardCharsets.US_ASCII), disk.bytes);
        assertEquals(PEER, new EnrollmentStore(disk).peer());
        assertFalse(new EnrollmentStore(disk).requiresReenrollment());
        assertTrue(store.forget());
        assertNull(new EnrollmentStore(disk).peer());
    }

    @Test public void missingCorruptUnsupportedAndOversizedRecordsNeverSelectPeer() {
        for (String record : new String[] {"", "1", "3\n02:00:00:00:00:01", "1\ninvalid",
                "1\n02:00:00:00:00:01\n", "1\n02:00:00:00:00:01extra",
                "1\n02:00:00:00:00:0G", "1\n02:00:00:00:00:0a", "1\r02:00:00:00:00:01",
                "2\n02:00:00:00:00:0G", "2\n02:00:00:00:00:01\n", "x".repeat(1000)}) {
            MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
            disk.bytes = record.getBytes(StandardCharsets.US_ASCII);
            EnrollmentStore store = new EnrollmentStore(disk);
            assertNull(record, store.peer());
            assertFalse(record, store.requiresReenrollment());
            assertFalse(record, store.failed());
            assertArrayEquals(record.getBytes(StandardCharsets.US_ASCII), disk.bytes);
        }
    }

    @Test public void invalidSelectorsAndUnknownPeerCannotReplaceEnrollment() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        EnrollmentStore store = new EnrollmentStore(disk);
        for (String peer : new String[] {null, "", "02:00:00:00:00:0G", PEER + "\n"}) {
            assertFalse(store.save(peer));
            assertNull(store.peer());
        }
        assertTrue(store.save(PEER));
        assertFalse(store.save("02:00:00:00:00:02"));
        assertEquals(PEER, store.peer());
    }

    @Test public void failedSaveReadOrUnacknowledgedWriteBlocksEnrollment() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        EnrollmentStore store = new EnrollmentStore(disk);
        disk.failWrite = true;
        assertFalse(store.save(PEER));
        assertTrue(store.failed());
        assertNull(store.peer());
        disk.failWrite = false;
        assertFalse(store.save(PEER));
        assertTrue(store.forget());
        disk.loseWrite = true;
        assertFalse(store.save(PEER));
        assertTrue(store.failed());
        disk.failRead = true;
        store = new EnrollmentStore(disk);
        assertTrue(store.failed());
        assertNull(store.peer());
    }

    @Test public void failedForgetDoesNotClaimDiskWasClearedAndCannotAuthorize() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        EnrollmentStore store = new EnrollmentStore(disk);
        assertTrue(store.save(PEER));
        disk.failWrite = true;
        assertFalse(store.forget());
        assertTrue(store.failed());
        assertNull(store.peer());
        assertFalse(store.save(PEER));
        // Failure is explicit: disk may still retain the selector; recovery must clear it.
        assertEquals(PEER, new EnrollmentStore(disk).peer());
        disk.failWrite = false;
        assertTrue(store.forget());
        assertFalse(store.failed());
        assertNull(new EnrollmentStore(disk).peer());
    }
}

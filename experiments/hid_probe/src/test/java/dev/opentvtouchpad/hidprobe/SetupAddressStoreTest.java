package dev.opentvtouchpad.hidprobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class SetupAddressStoreTest {
    private static final String ADDRESS = "192.168.50.20";

    @Test public void missingDraftIsEmptyWithoutWritingOrClaimingStorageFailure() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        SetupAddressStore store = new SetupAddressStore(disk);
        assertNull(store.address());
        assertFalse(store.failed());
        assertArrayEquals(new byte[0], disk.bytes);
    }

    @Test public void saveReopenAndReplaceUseOnlyTheVersionedCanonicalAddress() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        SetupAddressStore store = new SetupAddressStore(disk);
        for (String address : new String[]{ADDRESS, "10.0.0.0", "10.255.255.255",
                "172.16.0.0", "172.31.255.255", "192.168.0.0", "192.168.255.255"}) {
            assertTrue(store.save(address));
            assertEquals(address, store.address());
            assertFalse(store.failed());
            assertArrayEquals(bytes("1\n" + address), disk.bytes);
            SetupAddressStore reopened = new SetupAddressStore(disk);
            assertEquals(address, reopened.address());
            assertFalse(reopened.failed());
        }
    }

    @Test public void invalidInputCannotReplaceTheLastVerifiedDraft() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        SetupAddressStore store = new SetupAddressStore(disk);
        assertTrue(store.save(ADDRESS));
        for (String address : new String[]{null, "", "8.8.8.8", "127.0.0.1", "169.254.1.1",
                "172.15.255.255", "172.32.0.0", "192.169.0.1", "example.test", "::1",
                "192.168.01.1", "10.1.1.256", "10.1.1", "10.1.1.1:6466",
                " 10.1.1.1", "10.1.1.1\n", "http://10.1.1.1", "１０.1.1.1"}) {
            assertFalse(store.save(address));
            assertEquals(ADDRESS, store.address());
            assertFalse(store.failed());
            assertArrayEquals(bytes("1\n192.168.50.20"), disk.bytes);
        }
    }

    @Test public void corruptUnsupportedAndOversizedDraftsAreUnusableUntilExplicitSave() {
        for (byte[] record : new byte[][]{null, bytes("1"), bytes("1\n"), bytes("2\n10.0.0.1"),
                bytes("1\r10.0.0.1"), bytes("1\n8.8.8.8"), bytes("1\n10.01.1.1"),
                bytes("1\n10.0.0.1:6466"), bytes("1\n10.0.0.1\n"),
                bytes("1\n192.168.255.255\n"), new byte[1000],
                new byte[]{'1', '\n', '1', '0', '.', '0', '.', '0', '.', (byte) 0xff}}) {
            final byte[][] contents = {record};
            EnrollmentStore.Disk disk = new EnrollmentStore.Disk() {
                public byte[] read() { return contents[0]; }
                public void write(byte[] replacement) { contents[0] = replacement.clone(); }
            };
            SetupAddressStore store = new SetupAddressStore(disk);
            assertNull(store.address());
            assertTrue(store.failed());
            assertArrayEquals(record, contents[0]);
            assertTrue(store.save(ADDRESS));
            assertEquals(ADDRESS, store.address());
            assertFalse(store.failed());
        }
    }

    @Test public void failedInitialReadIsDistinctFromAbsenceAndAllowsExplicitRetry() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        disk.failRead = true;
        SetupAddressStore store = new SetupAddressStore(disk);
        assertNull(store.address());
        assertTrue(store.failed());
        disk.failRead = false;
        assertTrue(store.failed());
        assertTrue(store.save(ADDRESS));
        assertEquals(ADDRESS, store.address());
        assertFalse(store.failed());
    }

    @Test public void writeFailureReadbackFailureAndLostWriteNeverPublishAnUnverifiedDraft() {
        for (int mode = 0; mode < 3; mode++) {
            MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
            SetupAddressStore store = new SetupAddressStore(disk);
            assertTrue(store.save(ADDRESS));
            disk.failWrite = mode == 0;
            disk.failRead = mode == 1;
            disk.loseWrite = mode == 2;
            assertFalse(store.save("10.1.1.1"));
            assertNull(store.address());
            assertTrue(store.failed());
            disk.failWrite = disk.failRead = disk.loseWrite = false;
            assertNull(store.address());
            assertTrue(store.failed());
            assertTrue(store.save("10.1.1.1"));
            assertEquals("10.1.1.1", new SetupAddressStore(disk).address());
            assertEquals("10.1.1.1", store.address());
            assertFalse(store.failed());
        }
    }

    @Test public void uncheckedDiskFailuresAreReportedAndCanBeRetriedExplicitly() {
        final boolean[] broken = {true};
        MemoryEnrollmentDisk memory = new MemoryEnrollmentDisk();
        SetupAddressStore store = new SetupAddressStore(new EnrollmentStore.Disk() {
            public byte[] read() throws IOException {
                if (broken[0]) throw new IllegalStateException("synthetic read failure");
                return memory.read();
            }
            public void write(byte[] record) throws IOException {
                if (broken[0]) throw new IllegalStateException("synthetic write failure");
                memory.write(record);
            }
        });
        assertTrue(store.failed());
        assertFalse(store.save(ADDRESS));
        assertNull(store.address());
        broken[0] = false;
        assertTrue(store.save(ADDRESS));
        assertFalse(store.failed());
    }

    private static byte[] bytes(String record) { return record.getBytes(StandardCharsets.US_ASCII); }
}

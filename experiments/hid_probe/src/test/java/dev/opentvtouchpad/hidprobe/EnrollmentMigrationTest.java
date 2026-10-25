package dev.opentvtouchpad.hidprobe;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnrollmentMigrationTest {
    private static final String PEER = "02:00:00:00:00:01";
    private final MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();

    private EnrollmentStore legacyStore() {
        disk.bytes = "1\n02:00:00:00:00:01".getBytes(StandardCharsets.US_ASCII);
        return new EnrollmentStore(disk);
    }

    private long start(ProbeSession session) {
        long run = session.start();
        session.foreground(run, true);
        session.registered(run, true);
        return run;
    }

    @Test public void legacyRecordStaysUnselectedAndByteIdenticalAcrossLoads() {
        EnrollmentStore store = legacyStore();
        byte[] original = disk.bytes.clone();
        assertNull(store.peer());
        assertTrue(store.requiresReenrollment());
        assertFalse(store.failed());
        assertArrayEquals(original, disk.bytes);
        assertNull(new EnrollmentStore(disk).peer());
        assertTrue(new EnrollmentStore(disk).requiresReenrollment());
        assertArrayEquals(original, disk.bytes);
    }

    @Test public void oldBondCannotConnectConfirmOrSendWithoutNewComparison() {
        EnrollmentStore store = legacyStore();
        byte[] original = disk.bytes.clone();
        ProbeSession session = new ProbeSession(store);
        long run = start(session);
        assertFalse(session.canConnect(run));
        assertFalse(session.connectSaved(run, true));
        session.bonded(run, PEER);
        assertFalse(session.canConfirm(run));
        assertFalse(session.confirmComparison(run));
        assertNull(session.savedPeer());
        assertNull(session.candidate());
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.neutralSubmitted(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertTrue(store.requiresReenrollment());
        assertArrayEquals(original, disk.bytes);
    }

    @Test public void freshComparisonAndNewBondReplaceLegacyAndRestartRequiresExplicitConnect() {
        EnrollmentStore store = legacyStore();
        byte[] original = disk.bytes.clone();
        ProbeSession session = new ProbeSession(store);
        long run = start(session);
        session.bonded(run, PEER);
        assertTrue(session.observePairing(run, PEER, 2));
        assertFalse(session.confirmComparison(run));
        assertTrue(store.requiresReenrollment());
        assertArrayEquals(original, disk.bytes);
        session.bonded(run, PEER);
        assertTrue(session.confirmComparison(run));
        assertFalse(store.requiresReenrollment());
        assertArrayEquals("2\n02:00:00:00:00:01".getBytes(StandardCharsets.US_ASCII), disk.bytes);
        assertTrue(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        assertTrue(session.canSend(run, PEER));
        session.stop();

        EnrollmentStore reopened = new EnrollmentStore(disk);
        assertEquals(PEER, reopened.peer());
        assertFalse(reopened.requiresReenrollment());
        session = new ProbeSession(reopened);
        run = start(session);
        assertNull(session.eligiblePeer());
        assertFalse(session.neutralSubmitted(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertTrue(session.connectSaved(run, true));
        assertTrue(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        assertTrue(session.canSend(run, PEER));
    }

    @Test public void invalidSavePreservesLegacyUntilVerifiedForget() {
        EnrollmentStore store = legacyStore();
        byte[] original = disk.bytes.clone();
        assertFalse(store.save("invalid"));
        assertTrue(store.requiresReenrollment());
        assertNull(store.peer());
        assertFalse(store.failed());
        assertArrayEquals(original, disk.bytes);
        assertTrue(store.forget());
        assertFalse(store.requiresReenrollment());
        assertNull(store.peer());
        assertArrayEquals(new byte[0], disk.bytes);
        assertFalse(new EnrollmentStore(disk).requiresReenrollment());
    }

    @Test public void failedMigrationSaveLatchesUntilVerifiedForget() {
        for (int failure = 0; failure < 3; failure++) {
            EnrollmentStore store = legacyStore();
            ProbeSession session = new ProbeSession(store);
            long run = start(session);
            assertTrue(session.observePairing(run, PEER, 2));
            session.bonded(run, PEER);
            disk.failWrite = failure == 0;
            disk.loseWrite = failure == 1;
            disk.failRead = failure == 2;
            assertFalse(session.confirmComparison(run));
            assertFalse(session.active(run));
            assertNull(session.eligiblePeer());
            assertFalse(session.neutralSubmitted(run, PEER, true));
            assertFalse(session.canSend(run, PEER));
            assertFailedUntilForget(store);
        }
    }

    @Test public void failedLegacyForgetDoesNotClearNoticeOrFailureLatch() {
        for (int failure = 0; failure < 3; failure++) {
            EnrollmentStore store = legacyStore();
            disk.failWrite = failure == 0;
            disk.loseWrite = failure == 1;
            disk.failRead = failure == 2;
            assertFalse(store.forget());
            assertFailedUntilForget(store);
        }
    }

    private void assertFailedUntilForget(EnrollmentStore store) {
        assertTrue(store.failed());
        assertTrue(store.requiresReenrollment());
        assertNull(store.peer());
        disk.failWrite = false;
        disk.loseWrite = false;
        disk.failRead = false;
        assertFalse(store.save(PEER));
        assertTrue(store.forget());
        assertFalse(store.failed());
        assertFalse(store.requiresReenrollment());
        assertNull(store.peer());
        assertArrayEquals(new byte[0], disk.bytes);
        assertTrue(store.save(PEER));
        assertEquals(PEER, new EnrollmentStore(disk).peer());
    }

    @Test public void unreadableLegacyIsBlockedWithoutClaimingMigrationWasDetected() {
        legacyStore();
        disk.failRead = true;
        EnrollmentStore store = new EnrollmentStore(disk);
        assertTrue(store.failed());
        assertNull(store.peer());
        assertFalse(store.requiresReenrollment());
        disk.failRead = false;
        assertFalse(store.save(PEER));
        assertTrue(store.forget());
        assertFalse(store.failed());
    }
}

package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetainedSessionTest {
    private static final String PEER = "02:00:00:00:00:01";
    private static final String OTHER = "02:00:00:00:00:02";
    private final MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
    private final EnrollmentStore store = new EnrollmentStore(disk);
    private final ProbeSession session = new ProbeSession(store);

    private long start() {
        long run = session.start();
        session.foreground(run, true);
        session.registered(run, true);
        return run;
    }

    private long enroll() {
        long run = start();
        assertTrue(session.observePairing(run, PEER, 2));
        session.bonded(run, PEER);
        assertTrue(session.confirmComparison(run));
        return run;
    }

    @Test public void savedPeerAndUnsolicitedConnectionCannotAuthorize() {
        enroll();
        session.stop();
        long run = start();
        assertEquals(PEER, session.savedPeer());
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.neutralSubmitted(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
    }

    @Test public void stopThenExplicitStartAndConnectRequiresNeutralAndOnlyOneAttempt() {
        long old = enroll();
        session.stop();
        long run = start();
        assertTrue(session.connectSaved(run, true));
        assertFalse(session.connectSaved(run, true));
        assertFalse(session.connected(old, PEER, true));
        assertFalse(session.connected(run, OTHER, true));
        assertTrue(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertFalse(session.neutralSubmitted(old, PEER, true));
        assertFalse(session.neutralSubmitted(run, OTHER, true));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        assertTrue(session.canSend(run, PEER));
        session.registered(old, false);
        assertFalse(session.connected(old, PEER, false));
        assertTrue(session.canSend(run, PEER));
        assertTrue(session.connected(run, PEER, false));
        assertFalse(session.connectSaved(run, true));
        assertFalse(session.canSend(run, PEER));
        assertEquals(PEER, session.savedPeer());
    }

    @Test public void reopenedStoreRetainsSelectorButNoAuthorization() {
        enroll();
        ProbeSession reopened = new ProbeSession(new EnrollmentStore(disk));
        long run = reopened.start();
        assertEquals(PEER, reopened.savedPeer());
        assertFalse(reopened.connectSaved(run, true));
        reopened.foreground(run, true);
        assertFalse(reopened.connectSaved(run, true));
        reopened.registered(run, true);
        assertTrue(reopened.connectSaved(run, true));
        assertFalse(reopened.canSend(run, PEER));
    }

    @Test public void failedNeutralAndForegroundLossFenceAttempt() {
        long run = enroll();
        assertTrue(session.connected(run, PEER, true));
        assertFalse(session.neutralSubmitted(run, PEER, false));
        assertFalse(session.active(run));
        run = start();
        assertTrue(session.connectSaved(run, true));
        session.foreground(run, false);
        assertFalse(session.connected(run, PEER, true));
        assertEquals(PEER, session.savedPeer());
    }

    @Test public void missingBondRevokesSavedEnrollment() {
        enroll();
        session.stop();
        long run = start();
        assertFalse(session.connectSaved(run, false));
        assertFalse(session.active(run));
        assertNull(session.savedPeer());
        assertNull(new EnrollmentStore(disk).peer());
    }

    @Test public void selectedRevocationPersistsEvenWhileStoppedAndUnknownPeerCannotRevoke() {
        enroll();
        session.stop();
        assertFalse(session.revoke(OTHER));
        assertEquals(PEER, session.savedPeer());
        assertTrue(session.revoke(PEER));
        assertNull(session.savedPeer());
        assertNull(new EnrollmentStore(disk).peer());
    }

    @Test public void numericRePairRevokesWithoutAdoptingReplacement() {
        long run = enroll();
        assertFalse(session.observePairing(run, PEER, 2));
        assertNull(session.savedPeer());
        assertFalse(session.active(run));
    }

    @Test public void duplicateConnectedFencesInputRatherThanLeavingItEnabledDuringDisconnect() {
        long run = enroll();
        assertTrue(session.connected(run, PEER, true));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertFalse(session.active(run));
    }

    @Test public void rejectedConnectionMustFinishDisconnectBeforeAnExplicitAttempt() {
        enroll();
        session.stop();
        long run = start();
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.canConnect(run));
        assertFalse(session.connectSaved(run, true));
        assertFalse(session.connected(run, PEER, false));
        assertTrue(session.connectSaved(run, true));
    }

    @Test public void saveFailureNeverCreatesAttemptAndForgetFailureStopsInput() {
        long run = start();
        assertTrue(session.observePairing(run, PEER, 2));
        session.bonded(run, PEER);
        disk.failWrite = true;
        assertFalse(session.confirmComparison(run));
        assertNull(session.eligiblePeer());
        assertFalse(session.active(run));
        assertTrue(store.failed());
        disk.failWrite = false;
        assertTrue(session.forget());
        run = enroll();
        assertTrue(session.connected(run, PEER, true));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        disk.failWrite = true;
        assertFalse(session.forget());
        assertFalse(session.canSend(run, PEER));
        assertTrue(store.failed());
    }
}

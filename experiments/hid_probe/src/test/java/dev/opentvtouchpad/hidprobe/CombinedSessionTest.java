package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class CombinedSessionTest {
    private static final String PEER = "02:00:00:00:00:01";
    private final EnrollmentStore store = new EnrollmentStore(new MemoryEnrollmentDisk());
    private final ProbeSession session = new ProbeSession(store);

    private long start(TvEnrollment.State state) {
        assertTrue(store.peer() != null || store.save(PEER));
        long run = session.start(state);
        session.foreground(run, true);
        session.registered(run, true);
        return run;
    }

    @Test public void combinedConnectRequiresTlsBeforeHidAndNeutral() {
        long run = start(TvEnrollment.State.VALID);
        assertTrue(session.connectSaved(run, true));
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        session.connected(run, PEER, false);
        assertTrue(session.tvConnected(run));
        assertTrue(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        assertTrue(session.canSend(run, PEER));
        session.stop();
        assertFalse(session.tvConnected(run));
        assertFalse(session.canSend(run, PEER));
    }

    @Test public void unusableTvStoreNeverSelectsHidOnlyOrPairing() {
        long run = start(TvEnrollment.State.UNUSABLE);
        assertFalse(session.canConnect(run));
        assertFalse(session.connectSaved(run, true));
        assertFalse(session.canPairTv(run));
        assertFalse(session.beginTvPair(run));
        assertFalse(session.tvConnected(run));
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
    }

    @Test public void pairingRunHasNoInputOrSecondAttempt() {
        long run = start(TvEnrollment.State.ABSENT);
        assertTrue(session.canPairTv(run));
        assertTrue(session.beginTvPair(run));
        assertFalse(session.beginTvPair(run));
        assertFalse(session.connectSaved(run, true));
        assertFalse(session.tvConnected(run));
        assertFalse(session.connected(run, PEER, true));
        assertFalse(session.canSend(run, PEER));
        session.foreground(run, false);
        session.foreground(run, true);
        assertFalse(session.active(run));
    }

    @Test public void tvOperationPauseStopsEvenBeforeHidConnect() {
        long run = start(TvEnrollment.State.VALID);
        assertTrue(session.connectSaved(run, true));
        session.foreground(run, false);
        session.foreground(run, true);
        assertFalse(session.active(run));
        assertFalse(session.tvConnected(run));
        long next = start(TvEnrollment.State.VALID);
        assertFalse(session.tvConnected(run));
        assertFalse(session.tvConnected(next));
        assertTrue(session.connectSaved(next, true));
        assertTrue(session.tvConnected(next));
    }

    @Test public void hidOnlyRemainsAvailableOnlyWithoutTvEnrollment() {
        long run = start(TvEnrollment.State.ABSENT);
        assertTrue(session.connectSaved(run, true));
        assertFalse(session.canPairTv(run));
        assertTrue(session.connected(run, PEER, true));
        assertTrue(session.neutralSubmitted(run, PEER, true));
        assertTrue(session.canSend(run, PEER));
    }

    @Test public void initialBluetoothSystemUiPauseStillPreservesComparison() {
        long run = session.start(TvEnrollment.State.ABSENT);
        session.foreground(run, true);
        session.registered(run, true);
        assertFalse(session.beginTvPair(run));
        assertTrue(session.observePairing(run, PEER, 2));
        session.foreground(run, false);
        session.bonded(run, PEER);
        assertTrue(session.active(run));
        assertFalse(session.canConfirm(run));
        session.foreground(run, true);
        assertTrue(session.confirmComparison(run));
        assertTrue(session.connected(run, PEER, true));
    }

    @Test public void nativeOwnerWaitsForTvWorkerAfterOtherResourcesFinish() {
        NativeRunGate gate = new NativeRunGate();
        NativeRunGate.Run run = gate.start();
        run.await(NativeRunGate.Resource.TV);
        run.await(NativeRunGate.Resource.SERVICE);
        run.stop();
        run.completed(NativeRunGate.Resource.SERVICE);
        assertNull(gate.start());
        run.completed(NativeRunGate.Resource.TV);
        NativeRunGate.Run next = gate.start();
        assertNotNull(next);
        run.completed(NativeRunGate.Resource.TV);
        assertTrue(next.active());
    }
}

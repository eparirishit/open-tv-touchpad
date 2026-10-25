package dev.opentvtouchpad.hidprobe;

import org.junit.Test;

import static org.junit.Assert.*;

public class ProbeSessionTest {
    private static final String PEER = "02:00:00:00:00:01";
    private static final String OTHER = "02:00:00:00:00:02";

    private final ProbeSession session = new ProbeSession(new EnrollmentStore(new MemoryEnrollmentDisk()));

    @Test public void inactiveSessionRejectsAllCallbacks() {
        assertFenced(0);
    }

    @Test public void newSessionCannotConfirmUntilForegrounded() {
        long generation = session.start();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertFalse(session.confirmComparison(generation));
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.canSend(generation, PEER));
    }

    @Test public void registrationGetterRequiresActualCallbackForActiveGeneration() {
        assertFalse(session.isRegistered(0));
        long generation = startForeground();
        assertFalse(session.isRegistered(generation));
        session.registered(generation, true);
        assertTrue(session.isRegistered(generation));
        assertFalse(session.isRegistered(generation + 1));
        session.registered(generation, false);
        assertFalse(session.isRegistered(generation));
        session.registered(generation, true);
        assertFalse(session.isRegistered(generation));
    }

    @Test public void registrationGetterRejectsPriorSessionAndPendingRegistration() {
        long old = ready();
        assertTrue(session.isRegistered(old));
        long current = startForeground();
        assertFalse(session.isRegistered(old));
        assertFalse(session.isRegistered(current));
        session.registered(old, true);
        assertFalse(session.isRegistered(current));
        session.registered(current, true);
        assertTrue(session.isRegistered(current));
        assertFalse(session.isRegistered(old));
        session.stop();
        assertFalse(session.isRegistered(current));
    }

    @Test public void inputRequiresExplicitComparisonAndSubsequentConnection() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.canSend(generation, PEER));

        assertTrue(session.confirmComparison(generation));
        assertEquals(PEER, session.eligiblePeer());
        assertFalse(session.canSend(generation, PEER));
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void registrationMustBeAnActualCurrentCallback() {
        long generation = startForeground();
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertFalse(session.confirmComparison(generation));
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.canSend(generation, PEER));
        session.registered(generation, true);
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertTrue(session.confirmComparison(generation));
        assertEquals(PEER, session.eligiblePeer());
        assertFalse(session.canSend(generation, PEER));
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void preexistingBondAndPrematureConfirmationDoNotAuthorize() {
        long generation = startForeground();
        session.registered(generation, true);
        session.bonded(generation, PEER);
        assertFalse(session.confirmComparison(generation));
        assertTrue(session.observePairing(generation, PEER, 2));
        assertFalse(session.confirmComparison(generation));
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.canSend(generation, PEER));
        session.bonded(generation, PEER);
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.canSend(generation, PEER));
        assertTrue(session.confirmComparison(generation));
        assertEquals(PEER, session.eligiblePeer());
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void firstCandidateCannotBeReplacedByAnotherPeer() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        assertFalse(session.observePairing(generation, OTHER, 2));
        session.bonded(generation, OTHER);
        assertFalse(session.confirmComparison(generation));
        assertEquals(PEER, session.candidate());
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(generation, OTHER, true));
        assertFalse(session.connected(generation, OTHER, false));
        assertFalse(session.connected(generation, OTHER, false));
        assertTrue(session.active(generation));
        session.bonded(generation, PEER);
        assertNull(session.eligiblePeer());
        assertTrue(session.confirmComparison(generation));
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertFalse(session.canSend(generation, OTHER));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void onlyNumericComparisonCanChooseCandidate() {
        long generation = startForeground();
        session.registered(generation, true);
        for (int variant : new int[] {-1, 0, 1, 3, 4, 5, 6, 7, Integer.MAX_VALUE}) {
            assertFalse(session.observePairing(generation, PEER, variant));
            session.bonded(generation, PEER);
            assertFalse(session.confirmComparison(generation));
            assertNull(session.candidate());
            assertNull(session.eligiblePeer());
            assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
            assertFalse(session.canSend(generation, PEER));
        }
        assertTrue(session.observePairing(generation, PEER, 2));
    }

    @Test public void malformedPeersCannotChooseCandidate() {
        long generation = startForeground();
        session.registered(generation, true);
        for (String peer : new String[] {null, "", " ", "peer", "02:00:00:00:00",
                "02:00:00:00:00:0G", "02-00-00-00-00-01", PEER + "\n",
                " " + PEER, "A".repeat(1024)}) {
            assertFalse(session.observePairing(generation, peer, 2));
            session.bonded(generation, peer);
            assertFalse(session.confirmComparison(generation));
            assertFalse(session.connected(generation, peer, true));
        assertFalse(session.connected(generation, peer, false));
            assertFalse(session.connected(generation, peer, false));
            assertFalse(session.canSend(generation, peer));
            assertNull(session.candidate());
            assertNull(session.eligiblePeer());
            assertTrue(session.active(generation));
        }
        assertTrue(session.observePairing(generation, PEER, 2));
    }

    @Test public void unsupportedRepeatPairingRevokesAuthorizedSession() {
        for (int variant : new int[] {-1, 0, 1, 3, 4, 5, 6, 7, Integer.MAX_VALUE}) {
            long generation = ready();
            assertFalse(session.observePairing(generation, PEER, variant));
            assertFenced(generation);
        }
    }

    @Test public void unsupportedRepeatPairingAlsoRevokesPendingCandidate() {
        long generation = startForeground();
        assertTrue(session.observePairing(generation, PEER, 2));
        assertFalse(session.observePairing(generation, PEER, 3));
        assertFenced(generation);
    }

    @Test public void unsupportedPairingForOtherPeerOrStaleGenerationCannotStopSession() {
        long old = ready();
        long current = ready();
        assertFalse(session.observePairing(old, PEER, 3));
        assertFalse(session.observePairing(current, OTHER, 3));
        assertFalse(session.observePairing(current, null, 3));
        assertEquals(PEER, session.eligiblePeer());
        assertTrue(session.canSend(current, PEER));
    }

    @Test public void duplicateConfirmationIsRejectedWithoutRevokingAuthorizedCandidate() {
        long generation = ready();
        session.registered(generation, true);
        assertFalse(session.canConfirm(generation));
        assertFalse(session.confirmComparison(generation));
        assertEquals(PEER, session.candidate());
        assertTrue(session.canSend(generation, PEER));
        assertFalse(session.connected(generation, null, false));
        assertFalse(session.connected(generation, OTHER, true));
        assertFalse(session.connected(generation, OTHER, false));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void stopClearsTrustAndFencesLateCallbacks() {
        long generation = ready();
        session.stop();
        session.stop();
        assertFenced(generation);
    }

    @Test public void unregisterClearsTrustAndCannotBeUndoneByLateRegistration() {
        long generation = ready();
        session.registered(generation, false);
        assertFenced(generation);
    }

    @Test public void failedInitialRegistrationAlsoRequiresRestart() {
        long generation = startForeground();
        session.registered(generation, false);
        assertFenced(generation);
    }

    @Test public void connectionLossClearsTrustAndRequiresFreshStart() {
        long generation = ready();
        assertTrue(session.connected(generation, PEER, false));
        assertFenced(generation);
        assertNotEquals(generation, ready());
    }

    @Test public void rejectedAutomaticConnectionBeforeComparisonCannotEnableInput() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.connected(generation, PEER, false));
        assertTrue(session.active(generation));
        assertNull(session.eligiblePeer());
        assertFalse(session.canSend(generation, PEER));
        assertTrue(session.confirmComparison(generation));
        assertFalse(session.canSend(generation, PEER));
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void disconnectAfterComparisonBeforeFirstConnectionFencesLateSuccess() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertTrue(session.confirmComparison(generation));
        assertTrue(session.connected(generation, PEER, false));
        assertFenced(generation);
    }

    @Test public void startInvalidatesActiveGenerationAndAllItsTrust() {
        long old = ready();
        long current = startForeground();
        assertNotEquals(old, current);
        assertFalse(session.active(old));
        assertTrue(session.active(current));
        assertNull(session.candidate());
        assertNull(session.eligiblePeer());
        assertFalse(session.canSend(current, PEER));
        session.registered(old, true);
        assertFalse(session.observePairing(old, OTHER, 2));
        session.bonded(old, OTHER);
        assertFalse(session.confirmComparison(old));
        assertFalse(session.connected(old, PEER, true));
        assertFalse(session.connected(old, PEER, false));
        assertNull(session.candidate());
        assertTrue(session.observePairing(current, OTHER, 2));
        session.bonded(old, OTHER);
        session.registered(current, true);
        assertFalse(session.confirmComparison(current));
        assertNull(session.eligiblePeer());
        session.bonded(current, OTHER);
        assertNull(session.eligiblePeer());
        assertTrue(session.confirmComparison(current));
        assertEquals(OTHER, session.eligiblePeer());
        assertTrue(session.connected(current, OTHER, true));
        assertTrue(session.neutralSubmitted(current, OTHER, true));
        session.registered(old, false);
        assertFalse(session.connected(old, OTHER, false));
        assertFalse(session.canSend(old, OTHER));
        assertTrue(session.canSend(current, OTHER));
    }

    @Test public void staleConfirmationCannotAuthorizeNewSessionWithSamePeer() {
        long old = ready();
        session.stop();
        long current = startForeground();
        session.registered(current, true);
        assertTrue(session.observePairing(current, PEER, 2));
        session.bonded(current, PEER);
        assertFalse(session.confirmComparison(old));
        assertNull(session.eligiblePeer());
        assertFalse(session.connected(current, PEER, true));
        assertFalse(session.connected(current, PEER, false));
        assertTrue(session.confirmComparison(current));
        assertFalse(session.connected(old, PEER, true));
        assertFalse(session.connected(old, PEER, false));
        assertFalse(session.canSend(current, PEER));
        assertTrue(session.connected(current, PEER, true));
        assertTrue(session.neutralSubmitted(current, PEER, true));
        assertTrue(session.canSend(current, PEER));
    }

    @Test public void coveredPairingCallbacksPreserveEvidenceForResumedConfirmation() {
        long generation = startForeground();
        session.registered(generation, true);
        session.foreground(generation, false);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.connected(generation, PEER, false));
        session.foreground(generation, false);
        assertTrue(session.active(generation));
        assertTrue(session.isRegistered(generation));
        assertEquals(PEER, session.candidate());
        assertFalse(session.canConfirm(generation));
        assertFalse(session.confirmComparison(generation));
        assertNull(session.eligiblePeer());
        assertFalse(session.canSend(generation, PEER));

        session.foreground(generation, true);
        assertTrue(session.canConfirm(generation));
        assertTrue(session.confirmComparison(generation));
        assertFalse(session.canConfirm(generation));
        assertFalse(session.confirmComparison(generation));
        assertFalse(session.canSend(generation, PEER));
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertTrue(session.canSend(generation, PEER));
    }

    @Test public void backgroundingReadyComparisonPreservesEvidenceButDisablesConfirmation() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertTrue(session.canConfirm(generation));
        session.foreground(generation, false);
        assertFalse(session.canConfirm(generation));
        assertFalse(session.confirmComparison(generation));
        assertTrue(session.active(generation));
        session.foreground(generation, true);
        assertTrue(session.canConfirm(generation));
        assertTrue(session.confirmComparison(generation));
    }

    @Test public void canConfirmRequiresCurrentRegistrationCandidateAndFreshBond() {
        long generation = startForeground();
        assertFalse(session.canConfirm(generation));
        session.bonded(generation, PEER);
        assertTrue(session.observePairing(generation, PEER, 2));
        assertFalse(session.canConfirm(generation));
        session.bonded(generation, OTHER);
        assertFalse(session.canConfirm(generation));
        session.bonded(generation, PEER);
        assertFalse(session.canConfirm(generation));
        session.registered(generation + 1, true);
        assertFalse(session.canConfirm(generation));
        session.registered(generation, true);
        assertTrue(session.canConfirm(generation));
        assertFalse(session.canConfirm(generation + 1));
        session.registered(generation, false);
        assertFenced(generation);
    }

    @Test public void backgroundingConfirmedPendingConnectionFencesLateSuccess() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertTrue(session.confirmComparison(generation));
        session.foreground(generation, false);
        assertFenced(generation);
    }

    @Test public void backgroundingConnectedSessionImmediatelyFencesInput() {
        long generation = ready();
        session.foreground(generation, false);
        assertFalse(session.canSend(generation, PEER));
        assertFenced(generation);
    }

    @Test public void restartResetsForegroundAndIgnoresStaleLifecycleCallbacks() {
        long old = ready();
        assertTrue(session.forget());
        long current = session.start();
        session.registered(current, true);
        assertTrue(session.observePairing(current, PEER, 2));
        session.bonded(current, PEER);
        session.foreground(old, true);
        assertFalse(session.canConfirm(current));
        assertFalse(session.confirmComparison(current));
        session.foreground(current, true);
        assertTrue(session.canConfirm(current));
        assertTrue(session.confirmComparison(current));
        session.foreground(old, false);
        session.foreground(current + 1, false);
        assertTrue(session.active(current));
        assertTrue(session.connected(current, PEER, true));
        assertTrue(session.neutralSubmitted(current, PEER, true));
        assertTrue(session.canSend(current, PEER));
    }

    @Test public void connectedCallbackAloneMustNotEnableInputBeforeNeutralSuccess() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertTrue(session.confirmComparison(generation));
        assertTrue(session.connected(generation, PEER, true));
        assertFalse("CONNECTED is not neutral submission success", session.canSend(generation, PEER));
    }

    @Test public void repeatedNumericPairingMustRevokeAnAuthorizedPeer() {
        long generation = ready();
        assertFalse("re-pairing must revoke even with Numeric Comparison",
            session.observePairing(generation, PEER, 2));
        assertFenced(generation);
    }

    private long startForeground() {
        assertTrue(session.forget());
        long generation = session.start();
        session.foreground(generation, true);
        return generation;
    }

    private long ready() {
        long generation = startForeground();
        session.registered(generation, true);
        assertTrue(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertTrue(session.confirmComparison(generation));
        assertTrue(session.connected(generation, PEER, true));
        assertTrue(session.neutralSubmitted(generation, PEER, true));
        assertTrue(session.canSend(generation, PEER));
        return generation;
    }

    private void assertFenced(long generation) {
        assertFalse(session.active(generation));
        assertFalse(session.isRegistered(generation));
        assertNull(session.candidate());
        assertNull(session.eligiblePeer());
        session.foreground(generation, true);
        assertFalse(session.canConfirm(generation));
        session.registered(generation, true);
        assertFalse(session.observePairing(generation, PEER, 2));
        session.bonded(generation, PEER);
        assertFalse(session.confirmComparison(generation));
        assertFalse(session.connected(generation, PEER, true));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.connected(generation, PEER, false));
        assertFalse(session.canSend(generation, PEER));
        assertFalse(session.active(generation));
        assertFalse(session.isRegistered(generation));
        assertNull(session.candidate());
        assertNull(session.eligiblePeer());
    }
}

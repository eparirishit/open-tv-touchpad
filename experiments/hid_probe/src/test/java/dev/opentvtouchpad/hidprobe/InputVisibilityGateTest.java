package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputVisibilityGateTest {
    @Test public void startsRevokedAndNeverAcceptsZeroOrUnknownEpoch() {
        InputVisibilityGate gate = new InputVisibilityGate();
        assertEquals(0, gate.epoch()); assertFalse(gate.accepts(0));
        assertFalse(gate.accepts(1)); assertFalse(gate.accepts(-1));
        assertEquals(0, gate.update(false));
    }

    @Test public void onlyNewEligibilityGrantsAnEpochAndRevocationCannotReuseIt() {
        InputVisibilityGate gate = new InputVisibilityGate();
        long first = gate.update(true);
        assertTrue(first > 0); assertTrue(gate.accepts(first));
        assertEquals(first, gate.update(true));
        assertEquals(0, gate.update(false)); assertFalse(gate.accepts(first));
        gate.update(false);
        long second = gate.update(true);
        assertTrue(second > first); assertFalse(gate.accepts(first));
        assertTrue(gate.accepts(second)); assertFalse(gate.accepts(second + 1));
    }

    @Test public void writerObservesRevocationAndFreshGrant() throws Exception {
        InputVisibilityGate gate = new InputVisibilityGate();
        long first = gate.update(true);
        Thread owner = new Thread(() -> { gate.update(false); gate.update(true); });
        owner.start(); owner.join();
        assertFalse(gate.accepts(first)); assertTrue(gate.accepts(gate.epoch()));
    }
}

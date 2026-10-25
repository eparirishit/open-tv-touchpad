package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import static org.junit.Assert.*;
import static dev.opentvtouchpad.hidprobe.NativeRunGate.Resource.*;

public class NativeRunGateTest {
    @Test public void stopWaitsForServiceProfileAndRegistrationCompletion() {
        NativeRunGate gate = new NativeRunGate();
        NativeRunGate.Run old = gate.start();
        old.await(SERVICE);
        old.await(PROFILE);
        old.await(REGISTRATION);
        old.stop();
        assertFalse(old.active());
        assertNull(gate.start());
        old.completed(REGISTRATION);
        assertNull(gate.start());
        old.completed(PROFILE);
        assertNull(gate.start());
        old.completed(SERVICE);
        NativeRunGate.Run next = gate.start();
        assertNotNull(next);
        assertTrue(next.active());
        assertFalse(old.owns());
        old.stop();
        old.fail();
        old.completed(SERVICE);
        assertTrue(next.active());
        assertNull(gate.start());
    }

    @Test public void ambiguousNativeFailureNeverPermitsRestart() {
        NativeRunGate gate = new NativeRunGate();
        NativeRunGate.Run run = gate.start();
        run.await(REGISTRATION);
        run.fail();
        run.stop();
        run.completed(REGISTRATION);
        assertNull(gate.start());
        assertFalse(run.active());
    }

    @Test public void activeRunCannotBeReplacedEvenWhenResourcesAreIdle() {
        NativeRunGate gate = new NativeRunGate();
        NativeRunGate.Run run = gate.start();
        assertNull(gate.start());
        run.stop();
        assertNotNull(gate.start());
    }
}

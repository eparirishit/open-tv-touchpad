package dev.opentvtouchpad.hidprobe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionLifetimeTest {
    private final AtomicLong now = new AtomicLong(10000);
    private final SessionLifetime lifetime = new SessionLifetime(now::get);

    @Test public void setupHasFixedFiveMinuteCeilingAndExpiryCannotReconnect() {
        assertFalse(lifetime.alive()); assertFalse(lifetime.connected());
        lifetime.beginSetup();
        assertTrue(lifetime.alive()); assertFalse(lifetime.control());
        assertEquals(310000, lifetime.deadlineMillis());
        now.set(309999); assertTrue(lifetime.visibility(true));
        now.set(310000); assertFalse(lifetime.alive());
        assertFalse(lifetime.connected()); assertFalse(lifetime.visibility(true));
        lifetime.beginSetup(); assertTrue(lifetime.alive());
    }

    @Test public void activeControlIsUnboundedWhileFirstLossStartsThirtyMinuteIdle() {
        lifetime.beginSetup(); assertTrue(lifetime.connected());
        assertTrue(lifetime.control()); assertEquals(Long.MAX_VALUE, lifetime.deadlineMillis());
        now.set(1000000); assertTrue(lifetime.alive());
        assertTrue(lifetime.visibility(false)); assertTrue(lifetime.control());
        assertEquals(2800000, lifetime.deadlineMillis());
        now.set(1200000); assertTrue(lifetime.visibility(false));
        assertFalse(lifetime.connected()); // A duplicate readiness callback cannot renew idle.
        assertEquals(2800000, lifetime.deadlineMillis());
        now.set(2799999); assertTrue(lifetime.visibility(true));
        assertEquals(Long.MAX_VALUE, lifetime.deadlineMillis());
        now.set(2800000); assertTrue(lifetime.visibility(false));
        assertEquals(4600000, lifetime.deadlineMillis());
    }

    @Test public void expiryAfterSleepIsTerminalEvenWhenReturnIsFirstObserver() {
        lifetime.beginSetup(); assertTrue(lifetime.connected()); assertTrue(lifetime.visibility(false));
        now.set(1810000);
        assertFalse(lifetime.visibility(true)); assertFalse(lifetime.alive());
        assertFalse(lifetime.control()); assertFalse(lifetime.connected());
        assertTrue(lifetime.deadlineMillis() <= now.get());
    }

    @Test public void hidingAnUnfinishedConnectionCancelsIt() {
        lifetime.beginSetup(); assertTrue(lifetime.alive()); assertFalse(lifetime.visibility(false));
        assertFalse(lifetime.alive()); assertFalse(lifetime.connected());
        assertFalse(lifetime.visibility(true));
    }

    @Test public void explicitEndCannotBeRevivedByReadinessOrVisibility() {
        lifetime.beginSetup(); lifetime.connected(); lifetime.end();
        assertFalse(lifetime.alive()); assertFalse(lifetime.control());
        assertFalse(lifetime.connected()); assertFalse(lifetime.visibility(true));
        lifetime.beginSetup(); assertTrue(lifetime.connected());
    }

    @Test public void expiryAndEligibleReturnRaceCannotReviveAtDeadline() throws Exception {
        lifetime.beginSetup(); assertTrue(lifetime.connected()); assertTrue(lifetime.visibility(false));
        now.set(1810000);
        CountDownLatch start = new CountDownLatch(1);
        Thread returner = new Thread(() -> { await(start); lifetime.visibility(true); });
        Thread watchdog = new Thread(() -> { await(start); lifetime.alive(); });
        returner.start(); watchdog.start(); start.countDown(); returner.join(); watchdog.join();
        assertFalse(lifetime.alive()); assertFalse(lifetime.connected());
    }

    private static void await(CountDownLatch start) {
        try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
}

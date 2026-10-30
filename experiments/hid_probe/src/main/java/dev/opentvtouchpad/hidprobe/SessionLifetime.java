package dev.opentvtouchpad.hidprobe;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Monotonic transport lifetime, independent of input grants and protocol deadlines. */
final class SessionLifetime {
    private enum State { ENDED, SETUP, ACTIVE, IDLE }
    private final LongSupplier clock;
    private State state = State.ENDED;
    private long deadline;

    SessionLifetime(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    synchronized void beginSetup() {
        state = State.SETUP;
        deadline = clock.getAsLong() + 300000;
    }

    synchronized boolean connected() {
        if (!alive() || state != State.SETUP) return false;
        state = State.ACTIVE;
        deadline = Long.MAX_VALUE;
        return true;
    }

    synchronized boolean visibility(boolean visible) {
        if (!alive()) return false;
        if (!visible && state == State.SETUP) end();
        else if (!visible && state == State.ACTIVE) {
            state = State.IDLE;
            deadline = clock.getAsLong() + 1800000;
        } else if (visible && state == State.IDLE) {
            state = State.ACTIVE;
            deadline = Long.MAX_VALUE;
        }
        return state != State.ENDED;
    }

    synchronized boolean alive() {
        if (state != State.ENDED && clock.getAsLong() >= deadline) end();
        return state != State.ENDED;
    }

    synchronized boolean control() { return alive() && (state == State.ACTIVE || state == State.IDLE); }
    synchronized long deadlineMillis() { alive(); return deadline; }
    synchronized void end() { state = State.ENDED; deadline = 0; }
}

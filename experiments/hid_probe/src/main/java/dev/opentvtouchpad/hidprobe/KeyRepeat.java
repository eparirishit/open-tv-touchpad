package dev.opentvtouchpad.hidprobe;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Hold-to-repeat for the D-pad arrows. Each repeat is an ordinary complete SHORT press sent
 * through the normal admission path, so nothing is ever held down on the TV: releasing,
 * sliding off, losing focus or losing admission simply stops further presses.
 */
final class KeyRepeat {
    interface Scheduler {
        void post(Runnable task, long delayMillis);
        void cancel(Runnable task);
    }

    static final long DELAY_MILLIS = 400;
    static final long INTERVAL_MILLIS = 150;
    /** Bounds one hold to about 30 seconds of repeats. */
    static final int MAX_REPEATS = 200;

    private final Scheduler scheduler;
    private final Consumer<TvCommand> send;
    private TvCommand held;
    private BooleanSupplier stillHeld = () -> false;
    private int repeats;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (held == null) return;
            if (!stillHeld.getAsBoolean() || repeats >= MAX_REPEATS) { stop(); return; }
            repeats++;
            send.accept(held);
            scheduler.post(this, INTERVAL_MILLIS);
        }
    };

    KeyRepeat(Scheduler scheduler, Consumer<TvCommand> send) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.send = Objects.requireNonNull(send);
    }

    /** Only spatial navigation repeats; OK, Home, Back, media and app keys stay single presses. */
    static boolean repeats(TvCommand command) {
        return command == TvCommand.UP || command == TvCommand.DOWN
                || command == TvCommand.LEFT || command == TvCommand.RIGHT;
    }

    void press(TvCommand command, BooleanSupplier stillHeld) {
        stop();
        repeats = 0;
        if (!repeats(command)) return;
        held = command;
        this.stillHeld = Objects.requireNonNull(stillHeld);
        scheduler.post(tick, DELAY_MILLIS);
    }

    /** Ends the hold; true when it already sent repeats, so the release must not add a press. */
    boolean release() {
        boolean repeated = repeats > 0;
        stop();
        return repeated;
    }

    void stop() {
        held = null;
        stillHeld = () -> false;
        scheduler.cancel(tick);
    }
}

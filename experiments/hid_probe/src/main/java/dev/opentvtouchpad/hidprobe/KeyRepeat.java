package dev.opentvtouchpad.hidprobe;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * D-pad holds. Arrows repeat: each repeat is an ordinary complete SHORT press through normal
 * admission, so nothing is held on the TV. OK long-presses: after LONG_PRESS_MILLIS it asks the
 * TV client to start a hold once, and every end path (lift, cancel, stop) asks it to release;
 * the client bounds the TV-side hold and closes the connection if a release cannot be sent.
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
    static final long LONG_PRESS_MILLIS = 500;

    private final Scheduler scheduler;
    private final Consumer<TvCommand> send, holdStart;
    private final Runnable holdEnd;
    private TvCommand held;
    private boolean suppressClick, longStarted;
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

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (held == null || longStarted || !stillHeld.getAsBoolean()) return;
            longStarted = true;
            holdStart.accept(held);
        }
    };

    KeyRepeat(Scheduler scheduler, Consumer<TvCommand> send, Consumer<TvCommand> holdStart, Runnable holdEnd) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.send = Objects.requireNonNull(send);
        this.holdStart = Objects.requireNonNull(holdStart);
        this.holdEnd = Objects.requireNonNull(holdEnd);
    }

    /** Only OK has a real long press; every other key is a tap or an arrow repeat. */
    static boolean longPresses(TvCommand command) { return command == TvCommand.OK; }

    /** Only spatial navigation repeats; OK, Home, Back, media and app keys stay single presses. */
    static boolean repeats(TvCommand command) {
        return command == TvCommand.UP || command == TvCommand.DOWN
                || command == TvCommand.LEFT || command == TvCommand.RIGHT;
    }

    void press(TvCommand command, BooleanSupplier stillHeld) {
        stop();
        repeats = 0;
        if (!repeats(command) && !longPresses(command)) return;
        held = command;
        this.stillHeld = Objects.requireNonNull(stillHeld);
        if (longPresses(command)) scheduler.post(longPress, LONG_PRESS_MILLIS);
        else scheduler.post(tick, DELAY_MILLIS);
    }

    /** Ends the hold; true when it already repeated or long-pressed, so release adds no press. */
    boolean release() {
        boolean handled = repeats > 0 || longStarted;
        stop();
        return handled;
    }

    /**
     * Installs the hold listener on one TV button. {@code onDown} runs first on every touch.
     * The button's normal click must call {@link #consumeClick()} before sending its press.
     */
    void attach(android.view.View button, TvCommand command, Runnable onDown) {
        Objects.requireNonNull(onDown);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN -> {
                    onDown.run();
                    suppressClick = false;
                    press(command, () -> view.isPressed() && view.isEnabled() && view.isShown());
                }
                case android.view.MotionEvent.ACTION_UP -> suppressClick = release();
                case android.view.MotionEvent.ACTION_CANCEL -> stop();
                default -> { }
            }
            return false;
        });
    }

    /** True once for the click that ends a hold which already repeated; that click sends nothing. */
    boolean consumeClick() {
        boolean suppressed = suppressClick;
        suppressClick = false;
        return suppressed;
    }

    void stop() {
        suppressClick = false;
        held = null;
        stillHeld = () -> false;
        scheduler.cancel(tick);
        scheduler.cancel(longPress);
        if (longStarted) { longStarted = false; holdEnd.run(); }
    }
}

package dev.opentvtouchpad.hidprobe;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One run's main-thread scroll admission; no queue or delayed work. */
final class ScrollInput {
    private final Runnable cancel;
    private final BooleanSupplier ready;
    private final Consumer<MouseReport.Direction> send;
    private boolean sent;
    private long lastSubmission;

    ScrollInput(Runnable cancel, BooleanSupplier ready, Consumer<MouseReport.Direction> send) {
        this.cancel = cancel;
        this.ready = ready;
        this.send = send;
    }

    void touchDown() { cancel.run(); }

    void click(MouseReport.Direction direction, long now) {
        if (direction == null) throw new IllegalArgumentException("Missing scroll direction");
        if (!ready.getAsBoolean()) return;
        cancel.run();
        if (sent && now - lastSubmission < 100) return;
        sent = true;
        lastSubmission = now;
        send.accept(direction);
    }
}

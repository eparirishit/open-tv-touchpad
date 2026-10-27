package dev.opentvtouchpad.hidprobe;

import java.util.function.BiConsumer;

/** Main-thread gesture state; coordinates and slop are dp, times are monotonic ms. */
final class TouchpadGesture {
    private final double slop;
    private final BiConsumer<Integer, Integer> move;
    private final Runnable tap;
    private boolean enabled = true;
    private boolean blocked;
    private boolean moving;
    private boolean dispatched;
    private int pointer = -1;
    private long generation;
    private long downTime;
    private long lastDispatch;
    private double sensitivity = 1;
    private double originX, originY, lastX, lastY, pendingX, pendingY;

    TouchpadGesture(double slop, BiConsumer<Integer, Integer> move, Runnable tap) {
        this.slop = slop;
        this.move = move;
        this.tap = tap;
    }

    int pointerId() { return pointer; }
    long generation() { return generation; }

    void setSensitivity(double sensitivity) {
        if (!Double.isFinite(sensitivity) || sensitivity < 0.5 || sensitivity > 2) {
            throw new IllegalArgumentException("Sensitivity must be finite and between 0.5 and 2");
        }
        cancel();
        this.sensitivity = sensitivity;
    }

    void down(int id, double x, double y, long now) {
        cancel();
        if (!enabled || blocked) return;
        pointer = id;
        originX = lastX = x;
        originY = lastY = y;
        downTime = now;
    }

    void move(int id, double x, double y) {
        if (pointer == -1) return;
        if (id != pointer) { cancel(); return; }
        if (!moving) {
            if (Math.hypot(x - originX, y - originY) <= slop) return;
            moving = true;
        }
        pendingX = bounded(pendingX + (x - lastX) * sensitivity);
        pendingY = bounded(pendingY + (y - lastY) * sensitivity);
        lastX = x;
        lastY = y;
    }

    void up(int id, double x, double y, long now) {
        move(id, x, y);
        boolean click = pointer != -1 && !moving && now - downTime <= 250;
        cancel();
        if (click) tap.run();
    }

    void cancel() {
        generation++;
        pointer = -1;
        moving = false;
        downTime = 0;
        originX = originY = lastX = lastY = pendingX = pendingY = 0;
        // Keep the dispatch clock across gestures and disable/re-enable cycles.
    }

    void multiTouch() { cancel(); blocked = true; }
    void allUp() { cancel(); blocked = false; }
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cancel();
    }

    boolean hasPendingMotion() {
        return pointer != -1 && (Math.abs(pendingX) >= 1 || Math.abs(pendingY) >= 1);
    }

    long delayMillis(long now) {
        // Integer milliseconds require rounding 1000/30 upward.
        return dispatched ? Math.max(0, 34 - (now - lastDispatch)) : 0;
    }

    void frame(long epoch, long now) {
        if (epoch != generation || !hasPendingMotion() || delayMillis(now) != 0) return;
        int dx = Math.max(-127, Math.min(127, (int) pendingX));
        int dy = Math.max(-127, Math.min(127, (int) pendingY));
        pendingX -= dx;
        pendingY -= dy;
        dispatched = true;
        lastDispatch = now;
        move.accept(dx, dy);
    }

    private static double bounded(double value) { return Math.max(-254, Math.min(254, value)); }
}

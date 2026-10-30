package dev.opentvtouchpad.hidprobe;

/** Runtime supplies current-owner eligibility; this gate retains no UI or transport owner. */
final class InputVisibilityGate {
    private long sequence, epoch;

    synchronized long update(boolean eligible) {
        if (!eligible) epoch = 0;
        else if (epoch == 0 && sequence < Long.MAX_VALUE) epoch = ++sequence;
        return epoch;
    }

    synchronized long epoch() { return epoch; }
    synchronized boolean accepts(long candidate) { return candidate != 0 && candidate == epoch; }
}

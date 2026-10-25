package dev.opentvtouchpad.hidprobe;

import java.util.function.BooleanSupplier;

/** Shared native failure boundary for buttons, touch and scheduled movement. */
final class InputSubmission {
    private InputSubmission() { }

    static void guard(Runnable action, Runnable failure) {
        try { action.run(); }
        catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
            failure.run();
        }
    }

    static boolean click(BooleanSupplier press, BooleanSupplier release) {
        boolean pressed;
        boolean released;
        try { pressed = press.getAsBoolean(); }
        finally { released = release.getAsBoolean(); }
        return pressed && released;
    }
}

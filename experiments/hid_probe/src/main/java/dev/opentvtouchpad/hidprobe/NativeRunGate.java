package dev.opentvtouchpad.hidprobe;

import java.util.EnumSet;

/** Main-thread process-wide ownership; a stopped owner keeps its slot until native cleanup. */
final class NativeRunGate {
    enum Resource { SERVICE, PROFILE, REGISTRATION, TV }
    private Run current;

    boolean available() { return current == null; }

    Run start() {
        if (!available()) return null;
        current = new Run();
        return current;
    }

    final class Run {
        private final EnumSet<Resource> pending = EnumSet.noneOf(Resource.class);
        private boolean stopping;
        private boolean failed;

        boolean owns() { return current == this; }
        boolean active() { return owns() && !stopping && !failed; }
        void await(Resource resource) { if (owns()) pending.add(resource); }
        void completed(Resource resource) {
            if (!owns()) return;
            pending.remove(resource);
            release();
        }
        void fail() { if (owns()) failed = true; }
        void stop() { if (owns()) { stopping = true; release(); } }
        private void release() {
            if (stopping && !failed && pending.isEmpty()) current = null;
        }
    }
}

package dev.opentvtouchpad.hidprobe;

/** Per-run, main-thread observations only; no identifiers or free-form payload API. */
final class ProbeTrace {
    enum Event {
        RUN_SAVED, RUN_EMPTY,
        REGISTER_REQUEST, REGISTER_ACCEPTED, REGISTER_REJECTED, REGISTER_FAILED,
        REGISTERED, UNREGISTERED,
        CONNECT_REQUEST, CONNECT_ACCEPTED, CONNECT_REJECTED, CONNECT_FAILED, CONNECT_NATIVE_PENDING,
        SELECTED_CONNECTING, SELECTED_CONNECTED, SELECTED_DISCONNECTING, SELECTED_DISCONNECTED, SELECTED_STATE_OTHER,
        OTHER_CONNECTING, OTHER_CONNECTED, OTHER_DISCONNECTING, OTHER_DISCONNECTED, OTHER_STATE_OTHER,
        CONNECTION_NOT_BONDED, CONNECTION_NOT_ADMITTED, CONNECTION_CALLBACK_FAILED,
        NEUTRAL_ACCEPTED, NEUTRAL_REJECTED, MOVE_ACCEPTED, MOVE_REJECTED,
        PROTOCOL_REPORT, PROTOCOL_BOOT, PROTOCOL_OTHER,
        PAUSE, STOP, TEARDOWN_COMPLETE, SERVICE_STOP, SERVICE_LOST, PROFILE_LOST,
        REGISTRATION_DEADLINE, CONNECTION_DEADLINE, TEARDOWN_DEADLINE, RUN_DEADLINE, TRUNCATED
    }

    private final long startedMs;
    private int sequence;
    private long elapsedMs;

    ProbeTrace(long startedMs) { this.startedMs = startedMs; }

    String line(Event event, long nowMs) {
        if (sequence == 64) return null;
        elapsedMs = Math.max(elapsedMs, nowMs - startedMs);
        sequence++;
        return "v1 seq=" + sequence + " elapsed_ms=" + elapsedMs + " event="
            + (sequence == 64 ? Event.TRUNCATED : event).name();
    }
}

package dev.opentvtouchpad.hidprobe;

import org.junit.Test;

import static dev.opentvtouchpad.hidprobe.ProbeTrace.Event.*;
import static org.junit.Assert.*;

public class ProbeTraceTest {
    @Test public void fixedFormatContainsOnlyVersionSequenceElapsedAndEnum() {
        for (ProbeTrace.Event event : ProbeTrace.Event.values()) {
            ProbeTrace trace = new ProbeTrace(1000);
            assertEquals("v1 seq=1 elapsed_ms=25 event=" + event.name(), trace.line(event, 1025));
        }
    }

    @Test public void elapsedTimeIsNonnegativeAndNeverDecreases() {
        ProbeTrace trace = new ProbeTrace(1000);
        assertEquals("v1 seq=1 elapsed_ms=0 event=RUN_EMPTY", trace.line(RUN_EMPTY, 999));
        assertEquals("v1 seq=2 elapsed_ms=20 event=REGISTERED", trace.line(REGISTERED, 1020));
        assertEquals("v1 seq=3 elapsed_ms=20 event=CONNECT_REQUEST", trace.line(CONNECT_REQUEST, 1010));
        assertEquals("v1 seq=4 elapsed_ms=30 event=CONNECT_ACCEPTED", trace.line(CONNECT_ACCEPTED, 1030));
    }

    @Test public void instancesResetSequenceElapsedAndOutputBudget() {
        ProbeTrace old = new ProbeTrace(100);
        for (int i = 0; i < 100; i++) old.line(STOP, 200);
        ProbeTrace fresh = new ProbeTrace(500);
        assertEquals("v1 seq=1 elapsed_ms=0 event=RUN_SAVED", fresh.line(RUN_SAVED, 500));
        assertEquals("v1 seq=2 elapsed_ms=1 event=REGISTERED", fresh.line(REGISTERED, 501));
        assertNull(old.line(STOP, 501));
    }

    @Test public void capsAt64LinesIncludingExactlyOneFinalTruncationMarker() {
        ProbeTrace trace = new ProbeTrace(0);
        for (int sequence = 1; sequence <= 63; sequence++) {
            assertEquals("v1 seq=" + sequence + " elapsed_ms=" + sequence + " event=STOP",
                trace.line(STOP, sequence));
        }
        assertEquals("v1 seq=64 elapsed_ms=64 event=TRUNCATED", trace.line(STOP, 64));
        for (int i = 65; i <= 1000; i++) assertNull(trace.line(STOP, i));
    }

    @Test public void preservesObservedOrderIncludingRepeatedAndTerminalEvents() {
        ProbeTrace trace = new ProbeTrace(10);
        ProbeTrace.Event[] events = {RUN_SAVED, REGISTERED, CONNECT_REQUEST, CONNECT_ACCEPTED,
            SELECTED_CONNECTED, NEUTRAL_ACCEPTED, PAUSE, STOP, UNREGISTERED,
            SELECTED_DISCONNECTED, TEARDOWN_COMPLETE, TEARDOWN_COMPLETE};
        for (int i = 0; i < events.length; i++) {
            assertEquals("v1 seq=" + (i + 1) + " elapsed_ms=0 event=" + events[i].name(),
                trace.line(events[i], 10));
        }
    }
}

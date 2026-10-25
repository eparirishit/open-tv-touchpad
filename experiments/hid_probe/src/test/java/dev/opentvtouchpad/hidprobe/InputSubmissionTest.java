package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class InputSubmissionTest {
    @Test public void expectedNativeFailuresStopOnceForDirectAndQueuedActions() {
        for (RuntimeException error : new RuntimeException[] {
                new SecurityException(), new IllegalStateException(), new IllegalArgumentException()}) {
            int[] stopped = {0};
            Runnable action = () -> InputSubmission.guard(() -> { throw error; }, () -> stopped[0]++);
            action.run();
            assertEquals(1, stopped[0]);
            List<Runnable> queue = new ArrayList<>();
            queue.add(action);
            queue.remove(0).run();
            assertEquals(2, stopped[0]);
        }
    }

    @Test public void successfulActionDoesNotStop() {
        int[] sent = {0};
        InputSubmission.guard(() -> sent[0]++, () -> fail("unexpected stop"));
        assertEquals(1, sent[0]);
    }

    @Test public void clickAlwaysReleasesAfterRejectedPress() {
        List<String> sent = new ArrayList<>();
        assertFalse(InputSubmission.click(() -> { sent.add("press"); return false; },
            () -> { sent.add("release"); return true; }));
        assertEquals(List.of("press", "release"), sent);
    }

    @Test public void throwingPressStillReleasesThenStops() {
        List<String> events = new ArrayList<>();
        InputSubmission.guard(() -> InputSubmission.click(
            () -> { events.add("press"); throw new SecurityException(); },
            () -> { events.add("release"); return true; }), () -> events.add("stop"));
        assertEquals(List.of("press", "release", "stop"), events);
    }

    @Test public void throwingReleaseStops() {
        List<String> events = new ArrayList<>();
        InputSubmission.guard(() -> InputSubmission.click(
            () -> { events.add("press"); return true; },
            () -> { events.add("release"); throw new IllegalStateException(); }), () -> events.add("stop"));
        assertEquals(List.of("press", "release", "stop"), events);
    }

    @Test public void rejectedReleaseMakesClickFail() {
        assertFalse(InputSubmission.click(() -> true, () -> false));
        assertTrue(InputSubmission.click(() -> true, () -> true));
    }

    @Test public void stopFailureIsNotRetried() {
        int[] stops = {0};
        try {
            InputSubmission.guard(() -> { throw new SecurityException(); }, () -> {
                stops[0]++;
                throw new IllegalStateException();
            });
            fail("expected stop failure");
        } catch (IllegalStateException expected) {
            assertEquals(1, stops[0]);
        }
    }
}

package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ScrollInputTest {
    private final List<MouseReport.Direction> sent = new ArrayList<>();
    private final int[] moves = {0}, taps = {0}, canceled = {0};
    private boolean ready = true;
    private final TouchpadGesture gesture = new TouchpadGesture(5, (x,y) -> moves[0]++, () -> taps[0]++);
    private final ScrollInput scroll = new ScrollInput(() -> { canceled[0]++; gesture.cancel(); },
        () -> ready, sent::add);

    @Test public void buttonDownFencesQueuedMoveAndUpBeforeButtonClick() {
        gesture.down(1, 0, 0, 0);
        gesture.move(1, 20, 0);
        long old = gesture.generation();
        scroll.touchDown();
        gesture.frame(old, 50);
        gesture.up(1, 0, 0, 60);
        assertEquals(0, moves[0]);
        assertEquals(0, taps[0]);
        assertTrue(sent.isEmpty());
        scroll.click(MouseReport.Direction.DOWN, 100);
        assertEquals(List.of(MouseReport.Direction.DOWN), sent);
    }

    @Test public void canceledButtonTouchDoesNotScrollOrAllowOldTap() {
        gesture.down(1, 0, 0, 0);
        scroll.touchDown();
        gesture.up(1, 0, 0, 100);
        assertEquals(0, taps[0]);
        assertTrue(sent.isEmpty());
    }

    @Test public void keyboardStyleClickCancelsPadWithoutButtonDown() {
        gesture.down(1, 0, 0, 0);
        scroll.click(MouseReport.Direction.UP, 10);
        gesture.up(1, 0, 0, 50);
        assertEquals(1, canceled[0]);
        assertEquals(0, taps[0]);
        assertEquals(List.of(MouseReport.Direction.UP), sent);
    }

    @Test public void throttleDropsAcrossDirectionsButStillCancelsPad() {
        scroll.click(MouseReport.Direction.UP, 0);
        gesture.down(1, 0, 0, 10);
        gesture.move(1, 50, 0);
        long old = gesture.generation();
        scroll.click(MouseReport.Direction.LEFT, 99);
        gesture.frame(old, 100);
        assertEquals(0, moves[0]);
        assertEquals(2, canceled[0]);
        assertEquals(List.of(MouseReport.Direction.UP), sent);
        scroll.click(MouseReport.Direction.RIGHT, 100);
        assertEquals(List.of(MouseReport.Direction.UP, MouseReport.Direction.RIGHT), sent);
    }

    @Test public void ineligibleClicksNeverSendOrConsumeRateWindow() {
        ready = false;
        scroll.click(MouseReport.Direction.UP, 0);
        assertTrue(sent.isEmpty());
        ready = true;
        scroll.click(MouseReport.Direction.DOWN, 1);
        assertEquals(List.of(MouseReport.Direction.DOWN), sent);
    }

    @Test public void newRunHasItsOwnRateWindow() {
        scroll.click(MouseReport.Direction.UP, 0);
        ScrollInput next = new ScrollInput(gesture::cancel, () -> true, sent::add);
        next.click(MouseReport.Direction.DOWN, 1);
        assertEquals(List.of(MouseReport.Direction.UP, MouseReport.Direction.DOWN), sent);
    }

    @Test public void nativeFailureStopsWithoutRetryOrStalePadAction() {
        int[] attempts = {0}, stops = {0};
        ScrollInput failing = new ScrollInput(gesture::cancel, () -> ready, direction -> {
            attempts[0]++;
            throw new IllegalArgumentException();
        });
        gesture.down(1, 0, 0, 0);
        Runnable stop = () -> { ready = false; gesture.cancel(); stops[0]++; };
        InputSubmission.guard(() -> failing.click(MouseReport.Direction.UP, 0), stop);
        gesture.up(1, 0, 0, 20);
        InputSubmission.guard(() -> failing.click(MouseReport.Direction.UP, 500), stop);
        assertEquals(1, attempts[0]);
        assertEquals(1, stops[0]);
        assertEquals(0, taps[0]);
    }

    @Test public void nullDirectionCannotSend() {
        assertThrows(IllegalArgumentException.class, () -> scroll.click(null, 0));
        assertTrue(sent.isEmpty());
    }
}

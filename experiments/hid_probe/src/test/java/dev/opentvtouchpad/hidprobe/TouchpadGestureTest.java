package dev.opentvtouchpad.hidprobe;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchpadGestureTest {
    private final List<String> moves = new ArrayList<>();
    private int taps;
    private final TouchpadGesture gesture = new TouchpadGesture(5,
            (x, y) -> moves.add(x + "," + y), () -> taps++);

    private void frame(long time) { gesture.frame(gesture.generation(), time); }

    @Test public void sensitivityScalesBothAxes() {
        double[] gains = {0.5, 1.25, 2};
        String[] expected = {"4,-3", "10,-7", "16,-12"};
        for (int i = 0; i < gains.length; i++) {
            gesture.setSensitivity(gains[i]);
            gesture.down(7, 0, 0, i * 34);
            gesture.move(7, 8, -6);
            frame(i * 34);
            assertEquals(expected[i], moves.get(i));
        }
    }

    @Test public void sensitivityKeepsSlopInPhysicalDp() {
        double[] gains = {0.5, 2};
        String[] expected = {"3,0", "12,0"};
        for (int i = 0; i < gains.length; i++) {
            gesture.setSensitivity(gains[i]);
            gesture.down(7, 0, 0, i * 100);
            gesture.move(7, 3, 4);
            assertFalse(gesture.hasPendingMotion());
            gesture.up(7, 3, 4, i * 100 + 10);
            assertEquals(i + 1, taps);
            gesture.down(8, 0, 0, i * 100 + 20);
            gesture.move(8, 6, 0);
            frame(i * 100 + 20);
            assertEquals(expected[i], moves.get(i));
            gesture.up(8, 6, 0, i * 100 + 30);
            assertEquals(i + 1, taps);
        }
    }

    @Test public void sensitivityRetainsSignedFractionalRemainders() {
        gesture.setSensitivity(0.5);
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6.5, -6.5);
        frame(0);
        gesture.move(7, 7.5, -7.5);
        assertFalse(gesture.hasPendingMotion());
        gesture.move(7, 8.5, -8.5);
        frame(34);
        assertEquals(List.of("3,-3", "1,-1"), moves);
    }

    @Test public void sensitivityIsAppliedBeforeBoundedAccumulationAndSigned127Reports() {
        double[] gains = {0.5, 2};
        double[] displacement = {600, 200};
        for (int i = 0; i < gains.length; i++) {
            gesture.setSensitivity(gains[i]);
            gesture.down(7, 0, 0, i * 100);
            gesture.move(7, displacement[i], -displacement[i]);
            frame(i * 100);
            frame(i * 100 + 34);
            frame(i * 100 + 68);
            assertFalse(gesture.hasPendingMotion());
        }
        assertEquals(List.of("127,-127", "127,-127", "127,-127", "127,-127"), moves);
    }

    @Test public void sensitivityChangeDropsPendingFractionsAndFencesOldFrames() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6.75, 0);
        frame(0);
        gesture.move(7, 8.75, 0);
        long old = gesture.generation();
        gesture.setSensitivity(0.5);
        assertEquals(-1, gesture.pointerId());
        assertFalse(gesture.hasPendingMotion());
        gesture.frame(old, 34);
        gesture.move(7, 20, 0);
        gesture.up(7, 20, 0, 40);
        assertEquals(List.of("6,0"), moves);
        assertEquals(0, taps);
        gesture.down(8, 0, 0, 50);
        gesture.move(8, 6.5, 0);
        gesture.frame(old, 50);
        assertEquals(1, moves.size());
        frame(50);
        assertEquals(List.of("6,0", "3,0"), moves);
    }

    @Test public void sensitivityChangeCancelsPotentialTapUntilFreshDown() {
        gesture.down(7, 0, 0, 0);
        gesture.setSensitivity(2);
        gesture.up(7, 0, 0, 100);
        assertEquals(0, taps);
        assertTrue(moves.isEmpty());
        gesture.down(8, 0, 0, 110);
        gesture.up(8, 0, 0, 120);
        assertEquals(1, taps);
    }

    @Test public void sensitivityChangePreserves30HzDispatchClock() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        frame(100);
        gesture.setSensitivity(2);
        gesture.down(8, 0, 0, 101);
        gesture.move(8, 6, 0);
        assertEquals(1, gesture.delayMillis(133));
        frame(133);
        assertEquals(1, moves.size());
        frame(134);
        assertEquals(List.of("6,0", "12,0"), moves);
    }

    @Test public void invalidSensitivityRejectsWithoutChangingGainOrPendingGesture() {
        gesture.setSensitivity(1.5);
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 8, 0);
        long epoch = gesture.generation();
        for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Math.nextDown(0.5), Math.nextUp(2.0)}) {
            assertThrows(IllegalArgumentException.class, () -> gesture.setSensitivity(invalid));
            assertEquals(epoch, gesture.generation());
            assertEquals(7, gesture.pointerId());
            assertTrue(gesture.hasPendingMotion());
        }
        frame(0);
        gesture.move(7, 10, 0);
        frame(34);
        assertEquals(List.of("12,0", "3,0"), moves);
    }

    @Test public void sensitivityChangeCannotEnableInputOrLiftMultitouchBlock() {
        gesture.setEnabled(false);
        gesture.setSensitivity(2);
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        gesture.up(7, 6, 0, 10);
        frame(10);
        gesture.setEnabled(true);
        gesture.multiTouch();
        gesture.setSensitivity(0.5);
        gesture.down(8, 0, 0, 20);
        gesture.up(8, 0, 0, 30);
        assertTrue(moves.isEmpty());
        assertEquals(0, taps);
        gesture.allUp();
        gesture.down(9, 0, 0, 40);
        gesture.up(9, 0, 0, 50);
        assertEquals(1, taps);
    }

    @Test public void slopBoundaryAnd250msStillAllowTapWithoutMovement() {
        gesture.down(7, 10, 10, 100);
        gesture.move(7, 13, 14);
        assertFalse(gesture.hasPendingMotion());
        frame(200);
        gesture.up(7, 13, 14, 350);
        assertEquals(1, taps);
        assertTrue(moves.isEmpty());
    }

    @Test public void longPressDoesNothing() {
        gesture.down(7, 0, 0, 0);
        gesture.up(7, 0, 0, 251);
        assertEquals(0, taps);
        assertFalse(gesture.hasPendingMotion());
    }

    @Test public void firstMovementIncludesDisplacementSinceDown() {
        gesture.down(7, 10, 10, 0);
        gesture.move(7, 13, 10);
        gesture.move(7, 16, 12);
        frame(10);
        assertEquals(List.of("6,2"), moves);
    }

    @Test public void returningToOriginNeverRestoresTap() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        gesture.move(7, 0, 0);
        gesture.up(7, 0, 0, 100);
        assertEquals(0, taps);
        assertTrue(moves.isEmpty());
    }

    @Test public void upCoordinatesAlsoDisqualifyTap() {
        gesture.down(7, 0, 0, 0);
        gesture.up(7, 6, 0, 100);
        assertEquals(0, taps);
        frame(100);
        assertTrue(moves.isEmpty());
    }

    @Test public void fractionalRemaindersAccumulateWithSignedTruncation() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6.25, -6.25);
        frame(0);
        gesture.move(7, 6.75, -6.75);
        assertFalse(gesture.hasPendingMotion());
        gesture.move(7, 7.25, -7.25);
        frame(34);
        assertEquals(List.of("6,-6", "1,-1"), moves);
    }

    @Test public void burstOverflowIsDiscardedAndReportsAreSigned127() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 10000, -10000);
        frame(0);
        frame(34);
        frame(68);
        assertEquals(List.of("127,-127", "127,-127"), moves);
        assertFalse(gesture.hasPendingMotion());
        gesture.move(7, 9999, -9999);
        frame(102);
        assertEquals("-1,1", moves.get(2));
    }

    @Test public void movementCoalescesAndCannotDispatchFasterThan30Hz() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        frame(100);
        gesture.move(7, 8, 2);
        gesture.move(7, 10, 3);
        assertEquals(1, gesture.delayMillis(133));
        frame(133);
        assertEquals(1, moves.size());
        frame(134);
        assertEquals(List.of("6,0", "4,3"), moves);
        assertFalse(gesture.hasPendingMotion());
    }

    @Test public void rapidNewGesturesDoNotResetDispatchClock() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        frame(100);
        gesture.up(7, 6, 0, 101);
        gesture.down(8, 0, 0, 102);
        gesture.move(8, 0, 6);
        frame(103);
        assertEquals(1, moves.size());
        frame(134);
        assertEquals(List.of("6,0", "0,6"), moves);
    }

    @Test public void movementUpDiscardsPendingAndFractionalMotion() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6.75, 0);
        long old = gesture.generation();
        gesture.up(7, 6.75, 0, 10);
        gesture.frame(old, 20);
        assertTrue(moves.isEmpty());
        assertFalse(gesture.hasPendingMotion());
        gesture.down(8, 0, 0, 30);
        gesture.move(8, 6.5, 0);
        frame(40);
        assertEquals(List.of("6,0"), moves);
        assertFalse(gesture.hasPendingMotion());
    }

    @Test public void cancelDropsMotionAndTapAndRequiresNewDown() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        gesture.cancel();
        gesture.move(7, 20, 0);
        gesture.up(7, 20, 0, 100);
        frame(100);
        assertTrue(moves.isEmpty());
        assertEquals(0, taps);
        assertFalse(gesture.hasPendingMotion());
        gesture.down(8, 0, 0, 110);
        gesture.up(8, 0, 0, 120);
        assertEquals(1, taps);
    }

    @Test public void secondFingerLocksOutUntilAllUpAndFreshDown() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        gesture.multiTouch();
        gesture.move(7, 20, 0);
        gesture.down(8, 0, 0, 10);
        gesture.up(8, 0, 0, 20);
        frame(30);
        assertTrue(moves.isEmpty());
        assertEquals(0, taps);
        gesture.allUp();
        gesture.move(8, 30, 0);
        frame(40);
        assertTrue(moves.isEmpty());
        gesture.down(9, 0, 0, 50);
        gesture.up(9, 0, 0, 60);
        assertEquals(1, taps);
    }

    @Test public void missingOriginalPointerCancelsWithoutAdoptingAnother() {
        gesture.down(7, 0, 0, 0);
        gesture.move(8, 6, 0);
        gesture.move(7, 20, 0);
        frame(90);
        gesture.up(7, 0, 0, 100);
        frame(100);
        assertTrue(moves.isEmpty());
        assertEquals(0, taps);
    }

    @Test public void disabledDownCannotBecomeAuthorizedOnEnable() {
        gesture.setEnabled(false);
        gesture.down(7, 0, 0, 0);
        gesture.setEnabled(true);
        gesture.move(7, 6, 0);
        gesture.up(7, 0, 0, 100);
        frame(100);
        assertTrue(moves.isEmpty());
        assertEquals(0, taps);
        gesture.down(8, 0, 0, 110);
        gesture.up(8, 0, 0, 120);
        assertEquals(1, taps);
    }

    @Test public void disablingFencesOldCallbackEvenAfterNewGesture() {
        gesture.down(7, 0, 0, 0);
        gesture.move(7, 6, 0);
        long old = gesture.generation();
        gesture.setEnabled(false);
        assertFalse(gesture.hasPendingMotion());
        gesture.setEnabled(true);
        gesture.down(8, 0, 0, 10);
        gesture.move(8, 0, 7);
        gesture.frame(old, 20);
        assertTrue(moves.isEmpty());
        assertTrue(gesture.hasPendingMotion());
        frame(20);
        assertEquals(List.of("0,7"), moves);
    }
}

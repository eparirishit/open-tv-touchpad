package dev.opentvtouchpad.hidprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyRepeatTest {
    /** Manual clock: runs posted tasks in time order as time advances. */
    private static final class FakeScheduler implements KeyRepeat.Scheduler {
        long now;
        final List<long[]> due = new ArrayList<>();
        final List<Runnable> tasks = new ArrayList<>();
        @Override public void post(Runnable task, long delayMillis) { due.add(new long[]{now + delayMillis}); tasks.add(task); }
        @Override public void cancel(Runnable task) {
            for (int i = tasks.size() - 1; i >= 0; i--) if (tasks.get(i) == task) { tasks.remove(i); due.remove(i); }
        }
        int pending() { return tasks.size(); }
        void advance(long millis) {
            long end = now + millis;
            while (true) {
                int next = -1;
                for (int i = 0; i < due.size(); i++) if (due.get(i)[0] <= end && (next < 0 || due.get(i)[0] < due.get(next)[0])) next = i;
                if (next < 0) break;
                now = due.remove(next)[0];
                tasks.remove(next).run();
            }
            now = end;
        }
    }

    private final FakeScheduler clock = new FakeScheduler();
    private final List<TvCommand> sent = new ArrayList<>();
    private final KeyRepeat repeat = new KeyRepeat(clock, sent::add);
    private final AtomicBoolean pressed = new AtomicBoolean(true);

    @Test public void shortTapSendsNothingExtraAndAllowsTheClick() {
        repeat.press(TvCommand.DOWN, pressed::get);
        clock.advance(KeyRepeat.DELAY_MILLIS - 1);
        assertTrue(sent.isEmpty());
        assertFalse("A tap keeps its normal single press", repeat.release());
        clock.advance(10_000);
        assertTrue(sent.isEmpty());
        assertEquals(0, clock.pending());
    }

    @Test public void holdRepeatsAfterDelayAtBoundedIntervalAndSuppressesReleaseClick() {
        repeat.press(TvCommand.RIGHT, pressed::get);
        clock.advance(KeyRepeat.DELAY_MILLIS);
        assertEquals(List.of(TvCommand.RIGHT), sent);
        clock.advance(KeyRepeat.INTERVAL_MILLIS * 3);
        assertEquals(4, sent.size());
        assertTrue("Release after repeats must not add a press", repeat.release());
        clock.advance(10_000);
        assertEquals(4, sent.size());
        assertEquals(0, clock.pending());
    }

    @Test public void onlyArrowsRepeat() {
        for (TvCommand command : TvCommand.values()) {
            sent.clear();
            repeat.press(command, pressed::get);
            clock.advance(2_000);
            assertEquals(command.name(), KeyRepeat.repeats(command), !sent.isEmpty());
            repeat.stop();
        }
        for (TvCommand single : new TvCommand[]{TvCommand.OK, TvCommand.HOME, TvCommand.BACK,
                TvCommand.VOLUME_UP, TvCommand.VOLUME_DOWN, TvCommand.PLAY_PAUSE, TvCommand.NETFLIX})
            assertFalse(single.name(), KeyRepeat.repeats(single));
    }

    @Test public void slidingOffOrLosingTheButtonStopsWithoutAnotherPress() {
        repeat.press(TvCommand.UP, pressed::get);
        clock.advance(KeyRepeat.DELAY_MILLIS + KeyRepeat.INTERVAL_MILLIS);
        assertEquals(2, sent.size());
        pressed.set(false);
        clock.advance(10_000);
        assertEquals("No press once the button is no longer held", 2, sent.size());
        assertEquals(0, clock.pending());
    }

    @Test public void oneHoldIsBounded() {
        repeat.press(TvCommand.LEFT, pressed::get);
        clock.advance(KeyRepeat.DELAY_MILLIS + KeyRepeat.INTERVAL_MILLIS * (KeyRepeat.MAX_REPEATS + 50L));
        assertEquals(KeyRepeat.MAX_REPEATS, sent.size());
        assertEquals(0, clock.pending());
    }

    @Test public void stopAndNewPressCancelThePreviousHold() {
        repeat.press(TvCommand.DOWN, pressed::get);
        repeat.stop();
        clock.advance(10_000);
        assertTrue(sent.isEmpty());
        repeat.press(TvCommand.DOWN, pressed::get);
        repeat.press(TvCommand.OK, pressed::get);
        clock.advance(10_000);
        assertTrue("A non-repeating press replaces the arrow hold", sent.isEmpty());
        assertFalse(repeat.release());
    }
}

package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import static org.junit.Assert.*;
import static dev.opentvtouchpad.hidprobe.SetupState.Page.ADDRESS;
import static dev.opentvtouchpad.hidprobe.SetupState.Page.BT_START;
import static dev.opentvtouchpad.hidprobe.SetupState.Page.MANAGE;
import static dev.opentvtouchpad.hidprobe.SetupState.Page.OVERVIEW;
import static dev.opentvtouchpad.hidprobe.SetupState.Page.TV_START;
import static dev.opentvtouchpad.hidprobe.SetupState.Status.*;

public class SetupStateTest {
    @Test public void independentSavedItemsSelectTheFirstMissingStepAndChips() {
        boolean[][] combinations = {
            {false, false, false}, {false, false, true},
            {false, true, false}, {false, true, true},
            {true, false, false}, {true, false, true},
            {true, true, false}, {true, true, true}
        };
        SetupState.Page[] first = {BT_START, BT_START, BT_START, BT_START,
            ADDRESS, ADDRESS, TV_START, MANAGE};
        SetupState.Status[][] chips = {
            {NEXT, TO_DO, TO_DO}, {NEXT, TO_DO, DONE},
            {NEXT, DONE, TO_DO}, {NEXT, DONE, DONE},
            {DONE, NEXT, TO_DO}, {DONE, NEXT, DONE},
            {DONE, DONE, NEXT}, {DONE, DONE, DONE}
        };
        for (int i = 0; i < combinations.length; i++) {
            boolean[] saved = combinations[i];
            SetupState.Snapshot state = snapshot(saved[0], saved[1], saved[2]);
            assertEquals("complete " + i, i == 7, state.complete());
            assertEquals("anyComplete " + i, i != 0, state.anyComplete());
            assertEquals("firstMissing " + i, first[i], state.firstMissing());
            for (int step = 1; step <= 3; step++)
                assertEquals("chip " + i + "/" + step, chips[i][step - 1], state.status(step));
        }
    }

    @Test public void statusRejectsStepsOutsideTheThreeConfigurationItems() {
        SetupState.Snapshot state = snapshot(false, false, false);
        for (int step : new int[]{Integer.MIN_VALUE, -1, 0, 4, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> state.status(step));
    }

    @Test public void completedConfigurationDoesNotStartOrAuthorizeAControlSession() {
        MemoryEnrollmentDisk disk = new MemoryEnrollmentDisk();
        EnrollmentStore enrollment = new EnrollmentStore(disk);
        assertTrue(enrollment.save("02:00:00:00:00:01"));
        ProbeSession session = new ProbeSession(enrollment);
        SetupState.Snapshot state = snapshot(true, true, true);
        assertTrue(state.complete());
        assertEquals(MANAGE, state.firstMissing());
        assertFalse(session.active(state.generation()));
        assertFalse(session.canConnect(state.generation()));
        assertFalse(session.canSend(state.generation(), enrollment.peer()));
    }

    private static SetupState.Snapshot snapshot(boolean bluetooth, boolean address, boolean tv) {
        return new SetupState.Snapshot(OVERVIEW, bluetooth, address, tv,
            address ? "192.168.50.20" : null, "Saved configuration", "", 0,
            false, true, 7, false);
    }
}

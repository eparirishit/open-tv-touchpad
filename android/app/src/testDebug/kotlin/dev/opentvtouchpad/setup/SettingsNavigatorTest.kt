package dev.opentvtouchpad.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class SettingsNavigatorTest {
    @Test
    fun attemptsChosenPrimaryAction() {
        val attemptedActions = mutableListOf<String>()

        SettingsNavigator(attemptedActions::add).open(
            "android.settings.ACCESSIBILITY_SETTINGS",
        )

        assertEquals(
            listOf("android.settings.ACCESSIBILITY_SETTINGS"),
            attemptedActions,
        )
    }

    @Test
    fun unavailablePrimaryFallsBackToGeneralSettingsExactlyOnce() {
        val attemptedActions = mutableListOf<String>()
        val navigator = SettingsNavigator { action ->
            attemptedActions += action
            if (attemptedActions.size == 1) {
                throw SettingsNavigator.ActivityUnavailableException()
            }
        }

        navigator.open("android.settings.ACCESSIBILITY_SETTINGS")

        assertEquals(
            listOf(
                "android.settings.ACCESSIBILITY_SETTINGS",
                "android.settings.SETTINGS",
            ),
            attemptedActions,
        )
    }

    @Test
    fun unrelatedExceptionsAreNotSwallowed() {
        val attemptedActions = mutableListOf<String>()
        val failure = IllegalStateException("unexpected failure")

        try {
            SettingsNavigator { action ->
                attemptedActions += action
                throw failure
            }.open("android.settings.ACCESSIBILITY_SETTINGS")
            fail("Expected unrelated failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }

        assertEquals(
            listOf("android.settings.ACCESSIBILITY_SETTINGS"),
            attemptedActions,
        )
    }
}

package dev.opentvtouchpad.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsActionPolicyTest {
    @Test
    fun realHandlerKeepsAccessibilitySettings() {
        assertEquals(
            "android.settings.ACCESSIBILITY_SETTINGS",
            SettingsActionPolicy.actionFor("com.android.tv.settings"),
        )
    }

    @Test
    fun missingHandlerFallsBackToGeneralSettings() {
        assertEquals(
            "android.settings.SETTINGS",
            SettingsActionPolicy.actionFor(null),
        )
    }

    @Test
    fun frameworkPackageStubFallsBackToGeneralSettings() {
        assertEquals(
            "android.settings.SETTINGS",
            SettingsActionPolicy.actionFor("com.google.android.tv.frameworkpackagestubs"),
        )
    }
}

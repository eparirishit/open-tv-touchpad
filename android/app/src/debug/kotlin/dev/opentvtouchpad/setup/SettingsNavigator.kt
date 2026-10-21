package dev.opentvtouchpad.setup

import android.provider.Settings

internal class SettingsNavigator(
    private val launch: (String) -> Unit,
) {
    class ActivityUnavailableException(cause: Throwable? = null) : RuntimeException(cause)

    fun open(primaryAction: String) {
        try {
            launch(primaryAction)
        } catch (_: ActivityUnavailableException) {
            launch(Settings.ACTION_SETTINGS)
        }
    }
}

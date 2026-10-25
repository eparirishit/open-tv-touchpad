package dev.opentvtouchpad.setup

import android.provider.Settings

internal object SettingsActionPolicy {
    private const val GOOGLE_TV_FRAMEWORK_STUBS_PACKAGE =
        "com.google.android.tv.frameworkpackagestubs"

    fun actionFor(handlerPackageName: String?): String =
        if (handlerPackageName == null || handlerPackageName == GOOGLE_TV_FRAMEWORK_STUBS_PACKAGE) {
            Settings.ACTION_SETTINGS
        } else {
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        }
}

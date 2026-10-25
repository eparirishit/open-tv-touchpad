package dev.opentvtouchpad.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class AccessibilityManifestTest {
    private val serviceClassName =
        "dev.opentvtouchpad.accessibility.TouchAccessibilityService"

    @Test
    fun accessibilityServiceHasRestrictedGestureOnlyContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_PERMISSIONS,
        )
        val matchingServices = packageInfo.services.orEmpty().filter {
            it.name == serviceClassName
        }

        assertEquals(1, matchingServices.size)
        val service = matchingServices.single()
        assertTrue(service.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, service.permission)
        assertFalse(
            packageInfo.requestedPermissions.orEmpty().contains(
                Manifest.permission.SYSTEM_ALERT_WINDOW,
            ),
        )
        assertFalse(
            packageInfo.requestedPermissions.orEmpty().contains(
                Manifest.permission.WRITE_SECURE_SETTINGS,
            ),
        )

        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val installedService = accessibilityManager.installedAccessibilityServiceList.single {
            it.resolveInfo.serviceInfo.name == serviceClassName
        }

        assertFalse(installedService.canRetrieveWindowContent)
        assertEquals(0, installedService.eventTypes)
        assertTrue(
            installedService.capabilities and
                AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0,
        )
        assertFalse(
            installedService.capabilities and
                AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0,
        )
        assertFalse(
            installedService.flags and
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0,
        )
    }

    @Test
    fun calibrationActivityIsDebugOnlyAndNotExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES,
        )
        val calibration = packageInfo.activities.orEmpty().single {
            it.name == "dev.opentvtouchpad.setup.CalibrationActivity"
        }

        assertFalse(calibration.exported)
        assertEquals(null, calibration.permission)
    }

    @Test
    fun serviceIsOnlyExportedNonLauncherComponent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS,
        )
        val launchers = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setPackage(context.packageName)
            },
            0,
        )
        assertEquals(1, launchers.size)
        val launcher = launchers.single().activityInfo
        val launcherComponent = ComponentName(launcher.packageName, launcher.name)

        val exportedNonLauncherComponents = buildSet {
            packageInfo.activities.orEmpty()
                .filter { it.exported && ComponentName(it.packageName, it.name) != launcherComponent }
                .mapTo(this) { it.name }
            packageInfo.services.orEmpty()
                .filter { it.exported }
                .mapTo(this) { it.name }
            packageInfo.receivers.orEmpty()
                .filter { it.exported }
                .mapTo(this) { it.name }
            packageInfo.providers.orEmpty()
                .filter { it.exported }
                .mapTo(this) { it.name }
        }

        assertEquals(setOf(serviceClassName), exportedNonLauncherComponents)
    }
}

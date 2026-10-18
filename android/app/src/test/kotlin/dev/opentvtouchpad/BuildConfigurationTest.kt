package dev.opentvtouchpad

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.inputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildConfigurationTest {
    @Test
    fun androidTvBaselineMatchesApprovedConfiguration() {
        assertEquals("dev.opentvtouchpad", BuildConfig.APPLICATION_ID)
        assertEquals(26, BuildConfig.MIN_SDK)
        assertEquals(36, BuildConfig.TARGET_SDK)

        val manifest = Path.of(requireNotNull(System.getProperty("mainManifest")))
        val document = manifest.inputStream().use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val usesFeatures = document.getElementsByTagName("uses-feature")
        val touchscreenRequired = (0 until usesFeatures.length)
            .map(usesFeatures::item)
            .first { it.attributes.getNamedItem("android:name").nodeValue == "android.hardware.touchscreen" }
            .attributes
            .getNamedItem("android:required")
            .nodeValue
            .toBooleanStrict()

        assertFalse(touchscreenRequired)
    }
}

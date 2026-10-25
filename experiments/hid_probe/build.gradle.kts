plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.opentvtouchpad.hidprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.opentvtouchpad.hidprobe"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.5-diagnostic"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

dependencies {
    testImplementation(libs.junit4)
}

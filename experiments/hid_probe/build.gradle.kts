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
        versionCode = 19
        versionName = "0.19-diagnostic"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].java.srcDir("src/generated/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

dependencies {
    implementation(libs.protobuf.javalite)
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

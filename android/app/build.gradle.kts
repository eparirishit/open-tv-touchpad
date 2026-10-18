plugins {
    alias(libs.plugins.android.application)
}

val baselineApplicationId = "dev.opentvtouchpad"
val baselineMinSdk = 26
val baselineTargetSdk = 36
val releaseDebuggable = false

android {
    namespace = baselineApplicationId
    compileSdk = 36

    defaultConfig {
        applicationId = baselineApplicationId
        minSdk = baselineMinSdk
        targetSdk = baselineTargetSdk
        versionCode = 1
        versionName = "0.1.0-m0"

        buildConfigField("int", "MIN_SDK", baselineMinSdk.toString())
        buildConfigField("int", "TARGET_SDK", baselineTargetSdk.toString())
        buildConfigField("boolean", "RELEASE_DEBUGGABLE", releaseDebuggable.toString())
    }

    buildTypes {
        release {
            isDebuggable = releaseDebuggable
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "mainManifest",
        layout.projectDirectory.file("src/main/AndroidManifest.xml").asFile.absolutePath,
    )
}

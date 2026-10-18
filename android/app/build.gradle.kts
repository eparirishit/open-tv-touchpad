plugins {
    alias(libs.plugins.android.application)
}

val baselineApplicationId = "dev.opentvtouchpad"
val baselineMinSdk = 26
val baselineTargetSdk = 36

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
    }

    buildTypes {
        release {
            isDebuggable = false
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

val verifyReleaseBuildConfiguration = tasks.register("verifyReleaseBuildConfiguration") {
    group = "verification"
    description = "Verifies the actual Android release build-type model."

    doLast {
        check(!android.buildTypes.getByName("release").isDebuggable) {
            "Release build type must be non-debuggable."
        }
    }
}

dependencies {
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    dependsOn(verifyReleaseBuildConfiguration)
    systemProperty(
        "mainManifest",
        layout.projectDirectory.file("src/main/AndroidManifest.xml").asFile.absolutePath,
    )
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "open-tv-touchpad"

include(":android:app")

// Explicit opt-in: the disposable phone probe is absent from ordinary builds.
if (providers.gradleProperty("hidProbe").orNull == "true") {
    include(":experiments:hid_probe")
}

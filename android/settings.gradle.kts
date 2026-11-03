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

rootProject.name = "ClimbingFinder"

// core: plain Kotlin (pricing, data, logbook), testable without Android.
// app: the Jetpack Compose app.
include(":core", ":app")

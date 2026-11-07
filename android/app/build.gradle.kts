plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// One signing key for every shared build, so a newer APK installs over an older one
// whichever computer or CI run built it. The key is never in git: the maintainer's
// ~/.gradle/gradle.properties points to it, and CI gets it from secrets. Without
// it, builds use Android's per-computer debug key.
val sharedKeystore = providers.gradleProperty("climbingFinderKeystore").orNull
val sharedKeystorePassword = providers.gradleProperty("climbingFinderKeystorePassword").orNull

android {
    namespace = "io.github.tutu2652.climbingfinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tutu2652.climbingfinder"
        minSdk = 26
        targetSdk = 35
        // Raise both for every shared release, and set gyms.json _meta.android_app to
        // match once the release is published: that is what tells older copies to update.
        versionCode = 3
        versionName = "0.3"
    }

    signingConfigs {
        if (sharedKeystore != null) {
            create("shared") {
                storeFile = file(sharedKeystore)
                storePassword = sharedKeystorePassword
                keyAlias = "climbing-finder"
                keyPassword = sharedKeystorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (sharedKeystore != null) signingConfig = signingConfigs.getByName("shared")
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // gyms.json, bank_holidays.json and the logos are copied in from the repo
    // root at build time, so the app works offline and there is no second copy.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/repoData"))
}

val copyRepoData = tasks.register<Sync>("copyRepoData") {
    val repo = rootDir.parentFile
    from(repo.resolve("gyms.json"), repo.resolve("bank_holidays.json"))
    from(repo.resolve("assets/logos")) { into("logos") }
    into(layout.buildDirectory.dir("generated/repoData"))
}
tasks.named("preBuild") { dependsOn(copyRepoData) }

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "io.github.tutu2652.climbingfinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tutu2652.climbingfinder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

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

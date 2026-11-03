import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test-junit"))
}

// The repo root, where gyms.json lives.
val repoRoot = rootDir.parentFile

tasks.test {
    systemProperty("repoRoot", repoRoot.absolutePath)
}

// Used by tests/test_pricing.py: prices every gym on fixed dates and writes
// the results as JSON, so Python can check Kotlin agrees with the other copies.
//   ./gradlew -q :core:parityDump --args=<input.json>
tasks.register<JavaExec>("parityDump") {
    description = "Writes Kotlin pricing results for the parity test in tests/."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.tutu2652.climbingfinder.core.ParityDumpKt")
}

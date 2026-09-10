plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Deliberately framework-independent: no Android Gradle Plugin, no Android SDK dependency.
// Everything here (sign classification math, temporal decoding, the Qwen prompt/JSON contract,
// and the Mock/real language reasoners) is plain Kotlin so it can be unit-tested on a plain JVM.
// The Android app module (:app) depends on this module and adds the Android-only glue
// (CameraX, MediaPipe, Room, DataStore, Compose, JNI) around it.

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

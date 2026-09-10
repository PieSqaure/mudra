// Intentionally empty: plugin versions are resolved via pluginManagement.plugins in
// settings.gradle.kts instead of `apply false` here. That way, Gradle only needs to touch
// Google's Maven repo (for the Android Gradle Plugin) when a task actually configures the
// :app module - `./gradlew :core:test` never does, so the pure-Kotlin :core module builds
// and tests even in environments without access to the Android SDK/AGP.

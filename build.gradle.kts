// Every plugin is declared here with `apply false` so the versions are resolved once.
// `kotlin-jvm` is REQUIRED here: it lifts the Kotlin Gradle Plugin on the classpath to the
// catalog version (AGP 9 ships an older KGP for its built-in Kotlin support).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

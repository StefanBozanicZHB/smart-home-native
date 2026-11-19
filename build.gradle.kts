buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin with a runtime dependency on an older KGP; pin the
        // project's Kotlin version so the compiler matches the Compose/serialization plugins.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

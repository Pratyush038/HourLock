// build.gradle.kts — PROJECT-level (root)
// Only contains the plugin classpath declarations; no app-specific config here.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// app/build.gradle.kts — MODULE-level build script
// This is the primary Gradle config for the :app module. All plugin, SDK,
// and dependency declarations live here in Kotlin DSL format.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // kotlin.plugin.compose enables the Compose compiler plugin (required for
    // Kotlin 2.x where it's decoupled from the AGP Compose flag)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hourlock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hourlock.app"
        minSdk = 26          // API 26 required for NotificationChannel, DataStore compat
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Opt-in to coroutine experimental APIs used by DataStore flows
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        // Disable unused features to keep build fast
        buildConfig = true
        viewBinding = false
    }

    // Compose compiler plugin config (Kotlin 2.x style via the compose plugin)
    // No explicit composeOptions.kotlinCompilerExtensionVersion needed — the
    // kotlin.plugin.compose plugin handles this automatically.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    // Google Material — provides Theme.Material3.DayNight.NoActionBar for themes.xml
    implementation(libs.google.material)
    implementation(libs.androidx.appcompat)

    // ── Lifecycle ─────────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Activity ──────────────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)

    // ── Compose (BOM pins all Compose versions together) ──────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Extended icons: used for Lock, Timer, Settings, CheckCircle icons
    implementation(libs.androidx.material.icons.extended)

    // ── Navigation ────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── DataStore (Preferences) ───────────────────────────────────────────
    // Replaces SharedPreferences with a coroutine/Flow-based API.
    // We use it to persist: per-app usage seconds, hour-window timestamp,
    // limit settings, and master on/off state. All state is written on every
    // timer tick so nothing is lost if the process is killed abruptly.
    implementation(libs.androidx.datastore.preferences)

    // ── Glance AppWidget ──────────────────────────────────────────────────
    implementation(libs.androidx.glance.appwidget)

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Debug ─────────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ── Tests ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

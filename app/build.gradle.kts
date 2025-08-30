// Za local.properties -> TWELVEDATA_API_KEY
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    kotlin("android")                          // Kotlin 2.x
    id("org.jetbrains.kotlin.plugin.compose")  // Compose compiler plugin za Kotlin 2.x
}

android {
    namespace = "hr.zvargovic.goldbtcwear"
    compileSdk = 35

    defaultConfig {
        applicationId = "hr.zvargovic.goldbtcwear"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Učitaj API key iz local.properties (TWELVEDATA_API_KEY=xxxx)
        val tdKey = gradleLocalProperties(rootDir, providers)
            .getProperty("TWELVEDATA_API_KEY") ?: ""
        buildConfigField("String", "TWELVEDATA_API_KEY", "\"$tdKey\"")
    }

    // Uskladi Java/Kotlin na 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Compose 1.6.8 ↔ compiler ext 1.5.15
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    // Jetpack Compose (core)
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Wear Compose (umjesto material3)
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking (za kasnije)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
}
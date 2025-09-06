// Za local.properties -> TWELVEDATA_API_KEY
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") // ← OVO koristimo, bez compose plugina
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

    // Java/Kotlin 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Compose 1.6.8 ↔ compiler ext 1.5.15 (radi s Kotlin 1.9.24)
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Jetpack Compose (core)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.material:material:1.6.8")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Wear Compose
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking (za kasnije)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
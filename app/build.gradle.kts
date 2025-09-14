// Za local.properties -> TWELVEDATA_API_KEY
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

        val tdKey = gradleLocalProperties(rootDir, providers)
            .getProperty("TWELVEDATA_API_KEY") ?: ""
        buildConfigField("String", "TWELVEDATA_API_KEY", "\"$tdKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    // Datastore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Compose
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.material:material:1.6.8")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Wear Compose
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking / utils
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Tiles + ProtoLayout
    implementation("androidx.wear.tiles:tiles:1.3.0")
    implementation("androidx.wear.tiles:tiles-material:1.3.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("com.google.guava:guava:33.2.1-android")


    // ListenableFuture helper (za CallbackToFutureAdapter)
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
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

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            // za sad bez minify; kasnije ćemo uključiti i dodati ProGuard pravila
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // omogućava Java 8+ API-je na starijim API razinama
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // usklađeno s Compose pluginom
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }
}

dependencies {
    // Wear Compose
    val wearCompose = "1.3.0"
    implementation("androidx.wear.compose:compose-material:$wearCompose")
    implementation("androidx.wear.compose:compose-foundation:$wearCompose")
    implementation("androidx.wear.compose:compose-navigation:$wearCompose")

    // Osnovni Wear widgeti (po potrebi)
    implementation("androidx.wear:wear:1.3.0")

    // Activity/Compose glue
    implementation("androidx.activity:activity-compose:1.9.2")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // WorkManager (za periodične zadatke)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON (Moshi)
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi-adapters:1.15.1")

    // Desugar (Java 8+ API-je)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Testovi (ostavi kako je iz templatea, po želji nadogradit ćemo kasnije)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
}
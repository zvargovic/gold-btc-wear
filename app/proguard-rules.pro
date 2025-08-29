# -------- WorkManager --------
# (koristi refleksiju i različite implementacije scheduler-a)
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# -------- OkHttp / Okio --------
-dontwarn okhttp3.**
-dontwarn okio.**

# -------- Moshi (JSON) --------
# Zadrži Kotlin metapodatke i Moshi adaptere
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class kotlin.Metadata { *; }

# Ako ćemo kasnije koristiti @JsonClass(generateAdapter = true) s codegenom:
-keep class **$$JsonAdapter { *; }

# -------- Kotlin stdlib / coroutines --------
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# -------- AndroidX Compose (općenito sigurno) --------
# Compose je uglavnom proguard-friendly; izbjegni nepotrebna upozorenja
-dontwarn androidx.compose.**
package hr.zvargovic.goldbtcwear.data.net

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object Network {
    val client: OkHttpClient by lazy {
        val log = HttpLoggingInterceptor().apply {
            // Promijeni na BODY ako želiš debugati JSON u Logcat-u
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(log)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
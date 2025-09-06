package hr.zvargovic.goldbtcwear.data.api

import hr.zvargovic.goldbtcwear.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object HttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logger = HttpLoggingInterceptor { msg ->
                        android.util.Log.d("HTTP", msg)
                    }
                    logger.level = HttpLoggingInterceptor.Level.BODY
                    addInterceptor(logger)
                }
            }
            .build()
    }
}
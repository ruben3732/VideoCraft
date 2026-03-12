package com.videocraft.editor.data.api

import android.content.Context
import com.videocraft.editor.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
    }

    private fun buildClient(vararg headers: Pair<String, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Pexels ──────────────────────────────────────────────────────────────
    val pexelsApi: PexelsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.pexels.com/")
            .client(buildClient("Authorization" to BuildConfig.PEXELS_API_KEY))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PexelsApiService::class.java)
    }

    // ── Pixabay ─────────────────────────────────────────────────────────────
    val pixabayApi: PixabayApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://pixabay.com/")
            .client(buildClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PixabayApiService::class.java)
    }

    // ── Giphy ───────────────────────────────────────────────────────────────
    val giphyApi: GiphyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.giphy.com/")
            .client(buildClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiphyApiService::class.java)
    }
}

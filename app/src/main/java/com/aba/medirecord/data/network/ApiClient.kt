package com.aba.medirecord.data.network

import android.content.Context
import com.aba.medirecord.data.local.TokenManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"
    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var tokenManager: TokenManager? = null

    fun init(context: Context) {
        if (tokenManager == null) {
            tokenManager = TokenManager(context.applicationContext)
        }
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun getCurrentToken(): String? = tokenManager?.getToken()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .addInterceptor(
                JwtAuthInterceptor(
                    tokenProvider = { tokenManager?.getToken() }
                )
            )
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

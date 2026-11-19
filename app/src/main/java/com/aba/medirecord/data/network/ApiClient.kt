package com.aba.medirecord.data.network

import android.content.Context
import android.os.Build
import com.aba.medirecord.data.local.TokenManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"
    const val PHYSICAL_BASE_URL = "http://192.168.100.15:8080/"

    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var tokenManager: TokenManager? = null
    private var _apiService: ApiService? = null

    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""
        val hardware = Build.HARDWARE ?: ""

        return (brand.startsWith("generic") && device.startsWith("generic"))
                || fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk", ignoreCase = true)
                || model.contains("Emulator", ignoreCase = true)
                || model.contains("Android SDK built for x86", ignoreCase = true)
                || manufacturer.contains("Genymotion", ignoreCase = true)
                || hardware.contains("goldfish", ignoreCase = true)
                || hardware.contains("ranchu", ignoreCase = true)
                || product.contains("sdk_google", ignoreCase = true)
                || product.contains("google_sdk", ignoreCase = true)
                || product.contains("sdk", ignoreCase = true)
                || product.contains("vbox86p", ignoreCase = true)
                || product.contains("emulator", ignoreCase = true)
                || product.contains("simulator", ignoreCase = true)
    }

    fun init(context: Context) {
        if (tokenManager == null) {
            tokenManager = TokenManager(context.applicationContext)
        }
        val recommendedUrl = if (isEmulator()) DEFAULT_BASE_URL else PHYSICAL_BASE_URL
        if (currentBaseUrl == DEFAULT_BASE_URL && !isEmulator()) {
            setBaseUrl(recommendedUrl)
        }
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun setBaseUrl(newUrl: String) {
        val sanitized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        currentBaseUrl = sanitized
        _apiService = null
    }

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

    val apiService: ApiService
        get() {
            if (_apiService == null) {
                _apiService = Retrofit.Builder()
                    .baseUrl(currentBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return _apiService!!
        }
}

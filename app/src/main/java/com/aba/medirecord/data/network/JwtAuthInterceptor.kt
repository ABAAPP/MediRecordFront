package com.aba.medirecord.data.network

import com.aba.medirecord.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class JwtAuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: (() -> Unit)? = null,
) : Interceptor {

    companion object {
        private val PUBLIC_AUTH_PATHS = listOf(
            "api/auth/login",
            "api/auth/register"
        )

        fun isPublicEndpoint(encodedPath: String): Boolean {
            return PUBLIC_AUTH_PATHS.any { encodedPath.endsWith(it, ignoreCase = true) }
        }

        fun applyHeadersToRequest(
            originalRequest: Request,
            rawToken: String?,
        ): Request {
            val builder = originalRequest.newBuilder()
                .header("Accept", "application/json")

            val path = originalRequest.url().encodedPath()
            if (!isPublicEndpoint(path)) {
                val bearer = TokenManager.formatBearerHeader(rawToken)
                if (!bearer.isNullOrBlank()) {
                    builder.header("Authorization", bearer)
                }
            }

            return builder.build()
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val authenticatedRequest = applyHeadersToRequest(
            originalRequest = chain.request(),
            rawToken = tokenProvider()
        )
        val response = chain.proceed(authenticatedRequest)
        if (response.code() == 401) {
            onUnauthorized?.invoke()
        }
        return response
    }
}

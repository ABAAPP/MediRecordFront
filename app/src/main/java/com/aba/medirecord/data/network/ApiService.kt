package com.aba.medirecord.data.network

import com.aba.medirecord.data.model.LoginRequest
import com.aba.medirecord.data.model.LoginResponse
import com.aba.medirecord.data.model.RegisterRequest
import com.aba.medirecord.data.model.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}

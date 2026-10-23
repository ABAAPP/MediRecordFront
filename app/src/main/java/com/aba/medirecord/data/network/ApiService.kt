package com.aba.medirecord.data.network

import com.aba.medirecord.data.model.LoginRequest
import com.aba.medirecord.data.model.LoginResponse
import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.MedicationResponse
import com.aba.medirecord.data.model.RegisterRequest
import com.aba.medirecord.data.model.RegisterResponse
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.model.ScheduleResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/medications/today")
    suspend fun getTodayMedications(): Response<List<MedicationDose>>

    @POST("api/medications/{id}/toggle")
    suspend fun toggleDoseStatus(@Path("id") doseId: String): Response<MedicationDose>

    @POST("api/medications")
    suspend fun registerMedication(@Body request: MedicationRequest): Response<MedicationResponse>

    @POST("api/schedules")
    suspend fun saveMedicationSchedule(@Body request: ScheduleRequest): Response<ScheduleResponse>
}

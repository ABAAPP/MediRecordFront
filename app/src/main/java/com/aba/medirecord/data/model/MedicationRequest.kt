package com.aba.medirecord.data.model

data class MedicationRequest(
    val name: String,
    val dosage: String,
    val startDate: String,
    val endDate: String? = null,
    val frequency: String,
    val scheduledTime: String,
    val instructions: String? = null
)

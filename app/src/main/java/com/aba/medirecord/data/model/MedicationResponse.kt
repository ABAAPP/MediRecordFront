package com.aba.medirecord.data.model

data class MedicationResponse(
    val success: Boolean,
    val message: String,
    val medicationId: String? = null,
    val medication: MedicationDose? = null
)

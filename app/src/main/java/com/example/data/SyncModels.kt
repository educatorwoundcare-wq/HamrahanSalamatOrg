package com.example.data

import com.squareup.moshi.JsonClass
import com.example.data.Patient
import com.example.data.ServiceRegistration
import com.example.data.Expense

@JsonClass(generateAdapter = true)
data class PushData(
    val patients: List<Patient>? = null,
    val services: List<ServiceRegistration>? = null,
    val expenses: List<Expense>? = null
)

@JsonClass(generateAdapter = true)
data class PullData(
    val patients: List<Patient>? = null,
    val services: List<ServiceRegistration>? = null,
    val expenses: List<Expense>? = null
)

@JsonClass(generateAdapter = true)
data class SyncRequest(
    val device_id: String,
    val workspace_id: String,
    val last_sync_at: Long,
    val limit: Int = 500,
    val push_data: PushData? = null
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val status: String,
    val server_timestamp: Long,
    val has_more: Boolean,
    val next_sync_at: Long,
    val pull_data: PullData? = null
)

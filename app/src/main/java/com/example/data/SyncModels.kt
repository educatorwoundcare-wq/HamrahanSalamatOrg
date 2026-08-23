package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceRegistrationInsertPayload(
    val device_id: String,
    val company_id: String,
    val uid: String,
    val device_name: String,
    val device_type: String,
    val app_version: String = "v2.0.0",
    val role: String,
    val status: String,
    val requested_role: String? = null,
    val last_online_time: Long = System.currentTimeMillis(),
    val last_successful_sync: Long = 0L,
    val last_seen: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class DeviceStatusUpdatePayload(
    val device_name: String? = null,
    val device_type: String? = null,
    val app_version: String? = null,
    val status: String? = null,
    val role: String? = null,
    val requested_role: String? = null,
    val last_online_time: Long? = null,
    val last_successful_sync: Long? = null,
    val last_seen: Long? = null
)


package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateConfig(
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0",
    val forceUpdate: Boolean = false,
    val downloadUrl: String = ""
)

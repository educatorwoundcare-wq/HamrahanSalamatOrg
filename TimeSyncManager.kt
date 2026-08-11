package com.example.data

import android.content.Context
import android.content.SharedPreferences

class TimeSyncManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("time_sync_prefs", Context.MODE_PRIVATE)

    fun saveServerTime(serverTime: Long) {
        val deviceTime = System.currentTimeMillis()
        val offset = serverTime - deviceTime
        prefs.edit().putLong("time_offset", offset).apply()
    }

    fun getCurrentSyncedTime(): Long {
        val offset = prefs.getLong("time_offset", 0L)
        return System.currentTimeMillis() + offset
    }
}

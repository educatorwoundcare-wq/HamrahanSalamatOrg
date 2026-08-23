package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.UUID

object DeviceIdentityProvider {

    private const val PREFS_NAME = "device_identity_prefs"
    private const val KEY_INSTALLATION_DEVICE_ID = "installation_device_id"
    private const val PREFIX = "DEV-"

    @Volatile
    private var cachedDeviceId: String? = null
    private val lock = Any()

    /**
     * Checks if a deviceId represents a valid stable UUID-based installation ID.
     */
    fun isValidUuidDeviceId(deviceId: String?): Boolean {
        if (deviceId.isNullOrBlank()) return false
        val trimmed = deviceId.trim()
        // Reject known legacy hardcoded fallback IDs
        if (isLegacyHardcodedId(trimmed)) return false
        if (!trimmed.startsWith(PREFIX)) return false
        val uuidPart = trimmed.removePrefix(PREFIX)
        // Check if uuidPart is a valid standard UUID or at least 16 hex chars
        return if (uuidPart.length >= 16) {
            try {
                if (uuidPart.contains("-")) {
                    UUID.fromString(uuidPart)
                }
                true
            } catch (e: Exception) {
                // If it's alphanumeric and long enough, allow
                uuidPart.length >= 16 && uuidPart.all { it.isLetterOrDigit() || it == '-' }
            }
        } else {
            false
        }
    }

    private fun isLegacyHardcodedId(id: String): Boolean {
        val uppercase = id.uppercase().trim()
        return uppercase == "DEVICE-MGR" ||
                uppercase == "DEVICE-CEO" ||
                uppercase == "DEVICE-SEC" ||
                uppercase == "DEVICE-ACC" ||
                uppercase == "UNKNOWN-DEVICE" ||
                uppercase == "DEV-UNKNOWN" ||
                uppercase == "DEV-LOCAL" ||
                uppercase == "COMP-LOCAL" ||
                uppercase == "DEFAULT-DEVICE" ||
                uppercase == "MANAGER" ||
                uppercase == "STAFF"
    }

    /**
     * Single canonical entrypoint to obtain the stable unique device ID for this app installation.
     */
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { if (isValidUuidDeviceId(it)) return it }

        synchronized(lock) {
            cachedDeviceId?.let { if (isValidUuidDeviceId(it)) return it }

            val appContext = context.applicationContext
            val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedPrefId = prefs.getString(KEY_INSTALLATION_DEVICE_ID, null)?.trim()

            if (isValidUuidDeviceId(storedPrefId)) {
                cachedDeviceId = storedPrefId
                Log.i("DEVICE_ID", "[DEVICE_ID]\ndeviceId=$storedPrefId\nsource=stored")
                return storedPrefId!!
            }

            // Migration or First-time Generation
            val newDeviceId = "$PREFIX${UUID.randomUUID()}"
            prefs.edit().putString(KEY_INSTALLATION_DEVICE_ID, newDeviceId).apply()
            cachedDeviceId = newDeviceId

            if (!storedPrefId.isNullOrBlank()) {
                Log.i("DEVICE_ID_MIGRATION", "[DEVICE_ID_MIGRATION]\noldDeviceId=$storedPrefId\nnewDeviceId=$newDeviceId")
                Log.i("DEVICE_ID", "[DEVICE_ID]\ndeviceId=$newDeviceId\nsource=migrated")
            } else {
                Log.i("DEVICE_ID", "[DEVICE_ID]\ndeviceId=$newDeviceId\nsource=generated")
            }

            return newDeviceId
        }
    }

    /**
     * Ensures Room system_settings table contains the canonical device ID and logs migration if an old ID was present.
     */
    suspend fun syncWithRoomDatabase(context: Context, dao: HamrahanDao): String {
        val roomStoredId = dao.getSystemSettingByKey("active_device_id")?.trim()
        val appContext = context.applicationContext
        val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedPrefId = prefs.getString(KEY_INSTALLATION_DEVICE_ID, null)?.trim()

        val canonicalId = if (!roomStoredId.isNullOrBlank() && !isLegacyHardcodedId(roomStoredId)) {
            // Room already has a valid non-legacy canonical device ID, ensure prefs and cache match
            if (storedPrefId != roomStoredId) {
                prefs.edit().putString(KEY_INSTALLATION_DEVICE_ID, roomStoredId).apply()
                synchronized(lock) { cachedDeviceId = roomStoredId }
            }
            roomStoredId
        } else {
            getDeviceId(context)
        }

        if (roomStoredId != canonicalId) {
            if (!roomStoredId.isNullOrBlank() && isLegacyHardcodedId(roomStoredId)) {
                Log.i("DEVICE_ID_MIGRATION", "[DEVICE_ID_MIGRATION]\noldDeviceId=$roomStoredId\nnewDeviceId=$canonicalId")
            }
            dao.insertSystemSetting(SystemSetting("active_device_id", canonicalId))
        }
        return canonicalId
    }
}

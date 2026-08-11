package com.example.ui

import android.content.Context
import android.os.Environment
import com.example.data.HamrahanDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsBackup {
    companion object {
        suspend fun exportDatabaseToJson(context: Context, dao: HamrahanDao): Result<File> = withContext(Dispatchers.IO) {
            try {
                // Fetch all data
                val patients = dao.getAllPatients().first()
                // Skip CloudSyncRecords or other heavy tables if not needed
                val employees = dao.getAllEmployees().first()
                val services = dao.getAllServices().first()
                val registrations = dao.getAllServiceRegistrations().first()
                val expenses = dao.getAllExpenses().first()

                // Prepare Backup Object
                val backupData = mapOf(
                    "patients" to patients,
                    "employees" to employees,
                    "services" to services,
                    "registrations" to registrations,
                    "expenses" to expenses
                )

                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val jsonAdapter = moshi.adapter(Map::class.java)
                val jsonString = jsonAdapter.toJson(backupData)

                // Create File in Downloads Directory
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Hamrahan_Backup_$timestamp.json"
                val file = File(downloadsDir, fileName)

                FileOutputStream(file).use { output ->
                    output.write(jsonString.toByteArray())
                }

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

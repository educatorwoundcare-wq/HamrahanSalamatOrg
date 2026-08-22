package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.HamrahanApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? HamrahanApplication
        val syncEngine = app?.container?.syncEngine ?: run {
            val database = HamrahanDatabase.getDatabase(applicationContext)
            val dao = database.hamrahanDao()
            val sessionManager = com.example.data.auth.SessionManager(
                com.example.data.auth.TokenManager(applicationContext, dao)
            )
            val cloudClient = CloudClient(dao, applicationContext, sessionManager)
            SyncEngine(applicationContext, dao, cloudClient)
        }

        Log.i("SyncWorker", "[WorkManager] Triggering background sync via canonical SyncEngine...")

        try {
            val syncSuccess = syncEngine.sync()
            if (syncSuccess) {
                Log.i("SyncWorker", "[WorkManager] Background sync completed successfully.")
                Result.success()
            } else {
                Log.w("SyncWorker", "[WorkManager] Background sync returned false or encountered issue. Scheduling retry.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "[WorkManager] Background sync failed with exception", e)
            Result.retry()
        }
    }
}



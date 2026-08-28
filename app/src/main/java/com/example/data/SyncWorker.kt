package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.HamrahanApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? HamrahanApplication
        val syncEngine = app?.container?.syncEngine ?: run {
            val database = HamrahanDatabase.getDatabase(applicationContext)
            val dao = database.hamrahanDao()
            val cloudClient = CloudClient(dao, applicationContext)
            SyncEngine(applicationContext, dao, cloudClient)
        }

        Log.i("SyncWorker", "[WorkManager] Triggering background sync via canonical SyncEngine...")

        try {
            val syncSuccess = syncEngine.sync()
            if (syncSuccess) {
                Log.i("SyncWorker", "[WorkManager] Background sync completed successfully.")
                Result.success()
            } else {
                if (!syncEngine.isOnline.value) {
                    Log.w("SyncWorker", "[WorkManager] Device is offline. Scheduling retry.")
                    Result.retry()
                } else {
                    Log.w("SyncWorker", "[WorkManager] Background sync returned false. Ending work cycle.")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            Log.w("SyncWorker", "[WorkManager] Network I/O error during background sync. Scheduling retry.", e)
            Result.retry()
        } catch (e: SocketTimeoutException) {
            Log.w("SyncWorker", "[WorkManager] Socket timeout during background sync. Scheduling retry.", e)
            Result.retry()
        } catch (e: UnknownHostException) {
            Log.w("SyncWorker", "[WorkManager] Unknown host during background sync. Scheduling retry.", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e("SyncWorker", "[WorkManager] Non-retryable background sync failure", e)
            Result.failure()
        }
    }
}



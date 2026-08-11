package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = HamrahanDatabase.getDatabase(applicationContext)
        val dao = database.hamrahanDao()
        val workspaceManager = WorkspaceManager.getInstance(applicationContext)
        val tenantId = workspaceManager.currentTenantId

        if (tenantId.isNullOrEmpty()) {
            Log.e("SyncWorker", "No tenant ID found. Aborting sync.")
            return@withContext Result.failure()
        }
        
        val supabaseClient = SupabaseSyncClient(workspaceManager, dao)
        val conflictResolver = ConflictResolver(dao)

        try {
            // 1. Push pending local changes to cloud
            val pendingChanges = dao.getPendingSyncTasks(100) // Batch of 100 changes
            if (pendingChanges.isNotEmpty()) {
                
                // Mark as processing
                val processingIds = pendingChanges.map { it.id }
                dao.updateSyncStatuses(processingIds, "PROCESSING")
                
                val result = supabaseClient.pushBatchedChanges(tenantId, pendingChanges)
                
                when (result) {
                    is SyncResult.Success -> {
                        // Mark as completed
                        dao.updateSyncStatuses(processingIds, "COMPLETED")
                    }
                    is SyncResult.AuthError -> {
                        Log.e("SyncWorker", "Auth Error during sync")
                        pendingChanges.forEach { task ->
                            dao.updateSyncQueue(task.copy(status = "FAILED", retryCount = task.retryCount + 1))
                        }
                        // Stop sync if auth failed, no point retrying immediately
                        return@withContext Result.failure() 
                    }
                    is SyncResult.NetworkError, is SyncResult.ServerError -> {
                        Log.e("SyncWorker", "Network or Server error pushing changes: $result")
                        pendingChanges.forEach { task ->
                            val newRetry = task.retryCount + 1
                            val newStatus = if (newRetry > 3) "FAILED" else "PENDING"
                            dao.updateSyncQueue(task.copy(retryCount = newRetry, status = newStatus))
                        }
                        return@withContext Result.retry() 
                    }
                }
            }
            
            // 2. Cleanup completed sync tasks
            dao.deleteCompletedSyncTasks()
            
            // 3. Pull remote changes from cloud
            // We use a mock timestamp here, normally read from SharedPreferences
            val lastSyncTime = 0L 
            val remoteChanges = supabaseClient.pullRemoteChanges(tenantId, lastSyncTime)
            
            remoteChanges?.changes?.forEach { remoteChange ->
                // Basic Conflict Resolution for incoming updates
                if (remoteChange.operationType == "UPDATE") {
                    val localTask = dao.getPendingSyncTasks(1000).find { 
                        it.tableName == remoteChange.tableName && it.recordId == remoteChange.recordId 
                    }
                    
                    val localTimestamp = localTask?.timestamp ?: 0L
                    
                    val shouldApply = conflictResolver.resolveConflict(
                        tableName = remoteChange.tableName,
                        recordId = remoteChange.recordId,
                        localTimestamp = localTimestamp,
                        remoteTimestamp = remoteChange.timestamp,
                        deviceId = "Cloud",
                        user = "System"
                    )
                    
                    if (shouldApply) {
                        // Normally: apply payload to local database (e.g. deserialize JSON and call dao.update...)
                        // dao.updateEntityFromJson(...)
                    }
                } else if (remoteChange.operationType == "INSERT" || remoteChange.operationType == "DELETE") {
                     // Apply insert/delete without strict LWW unless it's a soft-delete
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background Sync failed entirely", e)
            Result.retry()
        }
    }
}


package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.CloudClient
import com.example.data.HamrahanDatabase
import com.example.data.HamrahanRepository
import com.example.data.SyncEngine
import com.example.data.SyncWorker
import com.example.data.WorkspaceManager
import java.util.concurrent.TimeUnit

class AppContainer(val context: Context) {
    val database: HamrahanDatabase by lazy {
        HamrahanDatabase.getDatabase(context)
    }
    val workspaceManager: WorkspaceManager by lazy {
        WorkspaceManager.getInstance(context)
    }
    val cloudClient: CloudClient by lazy {
        CloudClient(dao = database.hamrahanDao(), context = context)
    }
    val syncEngine: SyncEngine by lazy {
        SyncEngine(context, database.hamrahanDao(), cloudClient)
    }
    val supabaseClientManager: com.example.data.supabase.SupabaseClientManager by lazy {
        com.example.data.supabase.SupabaseClientManager(workspaceManager)
    }
    val supabaseAuthRepository: com.example.data.supabase.SupabaseAuthRepository by lazy {
        com.example.data.supabase.SupabaseAuthRepository(supabaseClientManager, workspaceManager)
    }
    val repository: HamrahanRepository by lazy {
        HamrahanRepository(context, database.hamrahanDao(), syncEngine, cloudClient)
    }
    val registerServiceAndGenerateLedgerUseCase: com.example.domain.usecase.RegisterServiceAndGenerateLedgerUseCase by lazy {
        com.example.domain.usecase.RegisterServiceAndGenerateLedgerUseCase(database.hamrahanDao(), syncEngine)
    }
    val settleEmployeeCommissionUseCase: com.example.domain.usecase.SettleEmployeeCommissionUseCase by lazy {
        com.example.domain.usecase.SettleEmployeeCommissionUseCase(database.hamrahanDao(), syncEngine)
    }
}

class HamrahanApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        } catch (_: Exception) {
            // Firebase or Crashlytics not initialized
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        container = AppContainer(this)
        
        setupBackgroundSync()
    }
    
    private fun setupBackgroundSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "HamrahanBackgroundSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            Log.e("HamrahanApp", "WorkManager background sync setup failed", e)
        }
    }

    companion object {
        lateinit var instance: HamrahanApplication
            private set
    }
}

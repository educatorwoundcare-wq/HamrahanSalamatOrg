package com.example

import android.app.Application
import android.content.Context
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
import com.example.data.auth.SessionManager
import com.example.data.auth.TokenManager
import java.util.concurrent.TimeUnit

class AppContainer(val context: Context) {
    val database: HamrahanDatabase by lazy {
        HamrahanDatabase.getDatabase(context)
    }
    val workspaceManager: WorkspaceManager by lazy {
        WorkspaceManager.getInstance(context)
    }
    val tokenManager: TokenManager by lazy {
        TokenManager(context, database.hamrahanDao())
    }
    val sessionManager: SessionManager by lazy {
        SessionManager(tokenManager)
    }
    val cloudClient: CloudClient by lazy {
        CloudClient(dao = database.hamrahanDao(), context = context, sessionManager = sessionManager)
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
        HamrahanRepository(context, database.hamrahanDao(), syncEngine, cloudClient, sessionManager)
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
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        container = AppContainer(this)
        
        setupBackgroundSync()
    }
    
    private fun setupBackgroundSync() {
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
    }

    companion object {
        lateinit var instance: HamrahanApplication
            private set
    }
}

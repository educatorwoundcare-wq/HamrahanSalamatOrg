package com.example.data.supabase

import com.example.data.TenantInterceptor
import com.example.data.WorkspaceManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized Supabase configuration manager.
 * Hosts the HTTP client with optimal timeout settings for the West EU region
 * and handles base Project Reference ID and API keys.
 */
class SupabaseClientManager(private val workspaceManager: WorkspaceManager) {

    // Supabase project hosted in West EU (Ireland)
    // TODO: Load these from BuildConfig or Secrets panel in production
    val supabaseUrl = "https://qfbjkdhhgeomrbamkpnn.supabase.co"
    val supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk"

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Injects X-Tenant-ID and Authorization headers for RLS compatibility
            .addInterceptor(TenantInterceptor(workspaceManager))
            // Base Anon Key required for all Supabase API calls
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", supabaseAnonKey)
                    .build()
                chain.proceed(request)
            }
            // Optimal timeout settings for West EU region routing
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

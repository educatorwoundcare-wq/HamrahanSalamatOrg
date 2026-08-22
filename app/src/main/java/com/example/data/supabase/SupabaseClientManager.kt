package com.example.data.supabase

import android.util.Log
import com.example.data.TenantInterceptor
import com.example.data.WorkspaceManager
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Custom DNS-over-HTTPS (DoH) resolver for Supabase OkHttp client.
 * Bypasses regional ISP DNS filtering and lookup failures on target devices
 * by performing encrypted DNS resolution via Cloudflare and Google DoH endpoints.
 */
class SupabaseDns(
    bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
) : Dns {

    private val cloudflareDns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByAddress("1.1.1.1", byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress("1.0.0.1", byteArrayOf(1, 0, 0, 1))
        )
        .build()

    private val googleDns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByAddress("8.8.8.8", byteArrayOf(8, 8, 8, 8)),
            InetAddress.getByAddress("8.8.4.4", byteArrayOf(8, 8, 4, 4))
        )
        .build()

    private data class CachedResult(
        val addresses: List<InetAddress>,
        val timestampMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedResult>()
    private val ttlMs = 5 * 60 * 1000L // 5 minutes TTL

    override fun lookup(hostname: String): List<InetAddress> {
        Log.d("SupabaseDNS", "[SupabaseDNS] Resolving host=$hostname")

        val now = System.currentTimeMillis()
        val cached = cache[hostname]
        if (cached != null && (now - cached.timestampMs) < ttlMs) {
            Log.d("SupabaseDNS", "[SupabaseDNS] Cache hit for host=$hostname, addresses=${cached.addresses}")
            return cached.addresses
        }

        // 1. Primary DoH: Cloudflare
        try {
            val addresses = cloudflareDns.lookup(hostname)
            if (addresses.isNotEmpty()) {
                Log.i("SupabaseDNS", "[SupabaseDNS] Resolution successful (Cloudflare DoH) addresses=$addresses")
                cache[hostname] = CachedResult(addresses, now)
                return addresses
            }
        } catch (e: Exception) {
            Log.w("SupabaseDNS", "[SupabaseDNS] Cloudflare DoH lookup failed for $hostname: ${e.message}")
        }

        // 2. Secondary DoH: Google
        try {
            val addresses = googleDns.lookup(hostname)
            if (addresses.isNotEmpty()) {
                Log.i("SupabaseDNS", "[SupabaseDNS] Resolution successful (Google DoH) addresses=$addresses")
                cache[hostname] = CachedResult(addresses, now)
                return addresses
            }
        } catch (e: Exception) {
            Log.w("SupabaseDNS", "[SupabaseDNS] Google DoH lookup failed for $hostname: ${e.message}")
        }

        // 3. Fallback: System DNS
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) {
                Log.i("SupabaseDNS", "[SupabaseDNS] Resolution successful (System DNS) addresses=$addresses")
                cache[hostname] = CachedResult(addresses, now)
                return addresses
            }
        } catch (e: Exception) {
            Log.e("SupabaseDNS", "[SupabaseDNS] Resolution failed for $hostname via all providers")
            throw e
        }

        throw java.net.UnknownHostException("Unable to resolve host: $hostname")
    }
}

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

    val supabaseDns: Dns by lazy { SupabaseDns() }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Custom DNS-over-HTTPS resolution to bypass regional ISP DNS failures
            .dns(supabaseDns)
            // Base Anon Key required for all Supabase API calls
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", supabaseAnonKey)
                    .build()
                chain.proceed(request)
            }
            // Injects X-Tenant-ID and Authorization headers for RLS compatibility
            .addInterceptor(TenantInterceptor(workspaceManager))
            // Optimal timeout settings for West EU region routing
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

package com.example.data

import okhttp3.Interceptor
import okhttp3.Response

class TenantInterceptor(private val workspaceManager: WorkspaceManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        
        workspaceManager.currentTenantId?.let {
            requestBuilder.addHeader("X-Tenant-ID", it)
        }
        workspaceManager.currentAuthToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        return chain.proceed(requestBuilder.build())
    }
}

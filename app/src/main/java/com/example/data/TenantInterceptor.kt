package com.example.data

import okhttp3.Interceptor
import okhttp3.Response

class TenantInterceptor(private val workspaceManager: WorkspaceManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()
        
        val tenantId = workspaceManager.currentTenantId
        if (!tenantId.isNullOrBlank() && tenantId != "COMP-LOCAL") {
            requestBuilder.header("X-Tenant-ID", tenantId)
        } else {
            requestBuilder.removeHeader("X-Tenant-ID")
        }
        
        val token = workspaceManager.currentAuthToken
        if (!token.isNullOrBlank() && !workspaceManager.isTokenExpired(token)) {
            requestBuilder.header("Authorization", "Bearer $token")
        } else {
            requestBuilder.removeHeader("Authorization")
        }
        
        return chain.proceed(requestBuilder.build())
    }
}

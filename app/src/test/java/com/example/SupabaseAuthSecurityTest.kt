package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.TenantInterceptor
import com.example.data.WorkspaceManager
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseAuthSecurityTest {

    private lateinit var context: Context
    private lateinit var workspaceManager: WorkspaceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workspaceManager = WorkspaceManager.getInstance(context)
        runBlocking {
            workspaceManager.clearIdentity()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            workspaceManager.clearIdentity()
        }
    }

    private class FakeInterceptorChain(private val initialRequest: Request) : Interceptor.Chain {
        var interceptedRequest: Request? = null

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            interceptedRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 10000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 10000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 10000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun createJwtWithExpiry(expiryEpochSeconds: Long, sub: String = "test-user-uid"): String {
        val headerJson = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }.toString()
        val payloadJson = JSONObject().apply {
            put("sub", sub)
            put("exp", expiryEpochSeconds)
            put("role", "authenticated")
        }.toString()

        val headerEncoded = Base64.encodeToString(headerJson.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payloadEncoded = Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return "$headerEncoded.$payloadEncoded.dummySignature"
    }

    /**
     * TEST 1 — No Token
     * WorkspaceManager without Token:
     * Result: Authorization header = absent
     */
    @Test
    fun test1_NoToken_AuthorizationHeaderAbsent() {
        runBlocking {
            workspaceManager.clearIdentity()
        }

        val interceptor = TenantInterceptor(workspaceManager)
        val initialRequest = Request.Builder()
            .url("https://supabase.example.com/rest/v1/workspaces")
            .build()

        val chain = FakeInterceptorChain(initialRequest)
        interceptor.intercept(chain)

        val processedRequest = chain.interceptedRequest
        assertNotNull(processedRequest)
        val authHeader = processedRequest?.header("Authorization")
        assertNull("Authorization header must be absent when no token is present", authHeader)
    }

    /**
     * TEST 2 — Valid JWT
     * A valid test JWT with exp > 10 minutes in the future:
     * Result: Authorization: Bearer <JWT>
     */
    @Test
    fun test2_ValidJwt_AuthorizationHeaderPresent() {
        val tenMinutesFutureSeconds = (System.currentTimeMillis() / 1000) + (15 * 60)
        val validJwt = createJwtWithExpiry(tenMinutesFutureSeconds, sub = "user-valid-123")

        runBlocking {
            workspaceManager.saveIdentity(
                tenantId = "COMP-TEST-TENANT",
                syncCode = "SYNC-VALID",
                authToken = validJwt,
                authUid = "user-valid-123"
            )
        }

        assertFalse("Token expiring in 15 minutes must not be marked expired", workspaceManager.isTokenExpired(validJwt))

        val interceptor = TenantInterceptor(workspaceManager)
        val initialRequest = Request.Builder()
            .url("https://supabase.example.com/rest/v1/workspaces")
            .build()

        val chain = FakeInterceptorChain(initialRequest)
        interceptor.intercept(chain)

        val processedRequest = chain.interceptedRequest
        assertNotNull(processedRequest)
        val authHeader = processedRequest?.header("Authorization")
        assertNotNull("Authorization header must be present for valid JWT", authHeader)
        assertEquals("Bearer $validJwt", authHeader)
    }

    /**
     * TEST 3 — Expired JWT
     * An expired JWT (exp in the past):
     * Result: Authorization header = absent
     */
    @Test
    fun test3_ExpiredJwt_AuthorizationHeaderAbsent() {
        val pastSeconds = (System.currentTimeMillis() / 1000) - (60 * 60) // 1 hour ago
        val expiredJwt = createJwtWithExpiry(pastSeconds, sub = "user-expired-123")

        runBlocking {
            workspaceManager.saveIdentity(
                tenantId = "COMP-TEST-TENANT",
                syncCode = "SYNC-EXPIRED",
                authToken = expiredJwt,
                authUid = "user-expired-123"
            )
        }

        assertTrue("Past exp token must be marked as expired", workspaceManager.isTokenExpired(expiredJwt))

        val interceptor = TenantInterceptor(workspaceManager)
        val initialRequest = Request.Builder()
            .url("https://supabase.example.com/rest/v1/workspaces")
            .build()

        val chain = FakeInterceptorChain(initialRequest)
        interceptor.intercept(chain)

        val processedRequest = chain.interceptedRequest
        assertNotNull(processedRequest)
        val authHeader = processedRequest?.header("Authorization")
        assertNull("Authorization header must be absent when token is expired", authHeader)
    }

    /**
     * TEST 4 — Near Expiry (< 5 minutes left)
     * JWT expiring in less than 5 minutes:
     * Result: Authorization header = absent
     */
    @Test
    fun test4_NearExpiryJwt_AuthorizationHeaderAbsent() {
        val nearExpirySeconds = (System.currentTimeMillis() / 1000) + (2 * 60) // 2 minutes in future (< 5 min threshold)
        val nearExpiryJwt = createJwtWithExpiry(nearExpirySeconds, sub = "user-near-123")

        runBlocking {
            workspaceManager.saveIdentity(
                tenantId = "COMP-TEST-TENANT",
                syncCode = "SYNC-NEAR",
                authToken = nearExpiryJwt,
                authUid = "user-near-123"
            )
        }

        assertTrue("Token with < 5 mins remaining must be treated as expired", workspaceManager.isTokenExpired(nearExpiryJwt))

        val interceptor = TenantInterceptor(workspaceManager)
        val initialRequest = Request.Builder()
            .url("https://supabase.example.com/rest/v1/workspaces")
            .build()

        val chain = FakeInterceptorChain(initialRequest)
        interceptor.intercept(chain)

        val processedRequest = chain.interceptedRequest
        assertNotNull(processedRequest)
        val authHeader = processedRequest?.header("Authorization")
        assertNull("Authorization header must be absent when token is near expiry (< 5 min)", authHeader)
    }

    /**
     * TEST 5 — Supabase Authentication
     * After successful authentication:
     * Verify currentAuthToken != null, currentAuthUid != null, !isTokenExpired
     */
    @Test
    fun test5_SupabaseAuthenticationFlow() {
        val futureExpiry = (System.currentTimeMillis() / 1000) + (3600 * 24) // 24 hours
        val validToken = createJwtWithExpiry(futureExpiry, sub = "supabase-auth-user-999")

        runBlocking {
            workspaceManager.saveIdentity(
                tenantId = "COMP-AUTH-999",
                syncCode = "SYNC-AUTH-999",
                authToken = validToken,
                authUid = "supabase-auth-user-999"
            )
        }

        assertNotNull("currentAuthToken must not be null", workspaceManager.currentAuthToken)
        assertNotNull("currentAuthUid must not be null", workspaceManager.currentAuthUid)
        assertEquals("COMP-AUTH-999", workspaceManager.currentTenantId)
        assertEquals("SYNC-AUTH-999", workspaceManager.currentSyncCode)
        assertEquals("supabase-auth-user-999", workspaceManager.currentAuthUid)
        assertEquals(validToken, workspaceManager.currentAuthToken)
        assertFalse("Valid token must not be expired", workspaceManager.isTokenExpired(workspaceManager.currentAuthToken))
    }
}

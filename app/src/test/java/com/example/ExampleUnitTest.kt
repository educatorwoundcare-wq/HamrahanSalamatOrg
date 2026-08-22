package com.example

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import java.util.concurrent.TimeUnit

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        org.junit.Assert.assertEquals(4, 2 + 2)
    }

    @Test
    fun testAnonymousAuthDiagnostic() {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=AIzaSyAP9unwcFCRgnwwvHed5tvfmoo_MxQgjps"
        val requestJson = "{\"returnSecureToken\":true}"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody(mediaType))
            .build()
        
        println("=== DIAGNOSTIC START ===")
        println("Target URL: $url")
        println("Request Body: $requestJson")
        
        try {
            client.newCall(request).execute().use { response ->
                println("Response Code: ${response.code}")
                println("Response Headers:")
                response.headers.forEach { pair ->
                    println("  ${pair.first}: ${pair.second}")
                }
                val body = response.body?.string()
                println("Response Body: $body")
            }
        } catch (e: Exception) {
            println("Exception occurred:")
            e.printStackTrace()
        }
        println("=== DIAGNOSTIC END ===")
    }

    @Test
    fun testFirebaseDatabaseAndPermissionDiagnostic() {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        
        // 1. SignUp Anonymously
        val authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=AIzaSyAP9unwcFCRgnwwvHed5tvfmoo_MxQgjps"
        val authRequestJson = "{\"returnSecureToken\":true}"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val authRequest = Request.Builder()
            .url(authUrl)
            .post(authRequestJson.toRequestBody(mediaType))
            .build()
            
        var idToken = ""
        var localId = ""
        
        try {
            client.newCall(authRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                println("Auth Response Code: ${response.code}")
                println("Auth Response Body: $body")
                if (response.isSuccessful) {
                    val parser = com.squareup.moshi.Moshi.Builder()
                        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                        .build()
                    val adapter = parser.adapter(Map::class.java)
                    val map = adapter.fromJson(body)
                    idToken = map?.get("idToken") as? String ?: ""
                    localId = map?.get("localId") as? String ?: ""
                }
            }
        } catch (e: Exception) {
            println("Auth Exception:")
            e.printStackTrace()
        }
        
        if (idToken.isEmpty() || localId.isEmpty()) {
            println("Cannot continue database diagnostics without auth.")
            return
        }
        
        val companyId = "COMP-DIAGTEST1"
        val baseUrl = "https://hamrahan-salamat-prod-default-rtdb.europe-west1.firebasedatabase.app"
        
        // 2. GET /companies/COMP-DIAGTEST1/info.json
        val getInfoUrl = "$baseUrl/companies/$companyId/info.json?auth=$idToken"
        println("GET Info URL: $getInfoUrl")
        val getInfoReq = Request.Builder().url(getInfoUrl).get().build()
        try {
            client.newCall(getInfoReq).execute().use { response ->
                println("GET Info Response Code: ${response.code}")
                println("GET Info Response Body: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 3. PUT /companies/COMP-DIAGTEST1/devices/$localId.json (Device Registration)
        val putDeviceUrl = "$baseUrl/companies/$companyId/devices/$localId.json?auth=$idToken"
        println("PUT Device URL: $putDeviceUrl")
        val deviceJson = """
            {
                "deviceId": "$localId",
                "deviceName": "Diagnostic Device",
                "deviceType": "Phone",
                "appVersion": "v2.0.0",
                "lastOnlineTime": ${System.currentTimeMillis()},
                "lastSuccessfulSync": 0,
                "status": "Pending",
                "uid": "$localId",
                "role": "Nurse",
                "lastSeen": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        val putDeviceReq = Request.Builder()
            .url(putDeviceUrl)
            .put(deviceJson.toRequestBody(mediaType))
            .build()
        try {
            client.newCall(putDeviceReq).execute().use { response ->
                println("PUT Device Response Code: ${response.code}")
                println("PUT Device Response Body: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 4. PUT /companies/COMP-DIAGTEST1/info.json
        val putInfoUrl = "$baseUrl/companies/$companyId/info.json?auth=$idToken"
        println("PUT Info URL: $putInfoUrl")
        val infoJson = """
            {
                "companyId": "$companyId",
                "companySyncCode": "HAMRAHAN-DIAG-TEST",
                "centerName": "Diagnostic Center",
                "nationalCode": "1234567890",
                "supportPhone": "09121111111",
                "centerAddress": "Diagnostic Address",
                "createdTimestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        val putInfoReq = Request.Builder()
            .url(putInfoUrl)
            .put(infoJson.toRequestBody(mediaType))
            .build()
        try {
            client.newCall(putInfoReq).execute().use { response ->
                println("PUT Info Response Code: ${response.code}")
                println("PUT Info Response Body: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

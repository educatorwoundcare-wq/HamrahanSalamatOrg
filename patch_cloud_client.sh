sed -i '/val json = JSONObject().apply {/i \
        // Forensic Auth Check\
        try {\
            val authCheckRequest = Request.Builder()\
                .url("$baseUrl/auth/v1/user")\
                .get()\
                .build()\
            client.newCall(authCheckRequest).execute().use { response ->\
                val authBody = response.body?.string() ?: ""\
                Log.i("AUTH_TRACE", "auth/v1/user HTTP ${response.code} body: $authBody")\
            }\
        } catch (e: Exception) {\
            Log.e("AUTH_TRACE", "auth/v1/user check failed", e)\
        }\
' app/src/main/java/com/example/data/CloudClient.kt

with open('app/src/main/java/com/example/data/CloudClient.kt', 'r') as f:
    content = f.read()

# Remove the trailing "\n}\n" I added earlier
content = content.rstrip()
if content.endswith('}'):
    content = content[:-1]

# Add SyncPullResponse class at the top if not exists
if "data class SyncPullResponse" not in content:
    content = content.replace("data class SyncPushResponse", 
"""data class SyncPullResponse(
    val changes: List<SyncPullRow>,
    val nextCursor: Long,
    val hasMore: Boolean
)

data class SyncPushResponse""")

sync_pull_code = """
    suspend fun syncPull(companyId: String, lastServerVersion: Long): Result<SyncPullResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = org.json.JSONObject().apply {
                put("p_company_id", companyId)
                put("p_last_server_version", lastServerVersion)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/rpc/sync_pull")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use Result.success(SyncPullResponse(emptyList(), lastServerVersion, false))
                    val json = org.json.JSONObject(bodyString)
                    
                    val array = json.optJSONArray("changes") ?: org.json.JSONArray()
                    val nextCursor = json.optLong("next_cursor", lastServerVersion)
                    val hasMore = json.optBoolean("has_more", false)
                    
                    val rows = mutableListOf<SyncPullRow>()
                    for (i in 0 until array.length()) {
                        val row = array.getJSONObject(i)
                        rows.add(SyncPullRow(
                            entityType = row.optString("entityType"),
                            uuid = row.optString("uuid"),
                            companyId = row.optString("companyId"),
                            payload = row.optJSONObject("payload")?.toString() ?: "{}",
                            serverUpdatedAt = row.optLong("serverUpdatedAt", 0L),
                            deletedAt = row.optLong("deletedAt", 0L),
                            serverVersion = row.optLong("serverVersion", 0L)
                        ))
                    }
                    return@use Result.success(SyncPullResponse(rows, nextCursor, hasMore))
                } else {
                    return@use Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
"""

with open('app/src/main/java/com/example/data/CloudClient.kt', 'w') as f:
    f.write(content + "\n" + sync_pull_code)

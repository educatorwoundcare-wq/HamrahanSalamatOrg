import re

with open('app/src/main/java/com/example/data/CloudClient.kt', 'r') as f:
    content = f.read()

# Replace SyncPullRow definition (if it's not matching)
# It's already matching the row, but we need a response wrapper.
new_classes = """
data class SyncPullResponse(
    val changes: List<SyncPullRow>,
    val nextCursor: Long,
    val hasMore: Boolean
)
"""
content = content.replace("data class SyncPushResponse", new_classes + "\ndata class SyncPushResponse")

# Rewrite syncPull to return Result<SyncPullResponse>
sync_pull_replacement = """
    suspend fun syncPull(companyId: String, lastServerVersion: Long): Result<SyncPullResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
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
                    val json = JSONObject(bodyString)
                    
                    val array = json.optJSONArray("changes") ?: JSONArray()
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
"""

content = re.sub(r'    suspend fun syncPull\(.*?\)\: Result\<List\<SyncPullRow\>\> \= withContext\(Dispatchers\.IO\) \{.*?\}', sync_pull_replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/CloudClient.kt', 'w') as f:
    f.write(content)

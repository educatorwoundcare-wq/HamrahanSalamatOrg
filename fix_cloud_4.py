with open('app/src/main/java/com/example/data/CloudClient.kt', 'r') as f:
    lines = f.readlines()

# find "class CloudClient"
# find "suspend fun syncPushBatch"
# The last '}' of syncPushBatch. Then we inject syncPull inside the class.

start_push = -1
for i, l in enumerate(lines):
    if "suspend fun syncPushBatch" in l:
        start_push = i
        break

# find the matching closing brace for syncPushBatch
brace_count = 0
end_push = -1
for i in range(start_push, len(lines)):
    l = lines[i]
    brace_count += l.count('{')
    brace_count -= l.count('}')
    if brace_count == 0 and '{' in ''.join(lines[start_push:i+1]):
        end_push = i
        break

sync_pull_code = """
    suspend fun syncPull(companyId: String, lastServerVersion: Long): Result<SyncPullResponse> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val requestBody = org.json.JSONObject().apply {
                put("p_company_id", companyId)
                put("p_last_server_version", lastServerVersion)
            }.toString()
            
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/rpc/sync_pull")
                .post(okhttp3.RequestBody.create(jsonMediaType, requestBody))
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

new_lines = lines[:end_push+1]
new_content = "".join(new_lines) + sync_pull_code

# Remove duplicate data class SyncPullResponse
# Keep only one
import re
new_content = re.sub(r'data class SyncPullResponse\(.*?\}\n\n', '', new_content, flags=re.DOTALL)
new_content = new_content.replace('data class SyncPullResponse(', 'data class SyncPullResponse_dup(')
new_content = new_content.replace('data class SyncPullResponse_dup', 'data class SyncPullResponse', 1)

with open('app/src/main/java/com/example/data/CloudClient.kt', 'w') as f:
    f.write(new_content)

import re

with open('app/src/main/java/com/example/data/CloudClient.kt', 'r') as f:
    content = f.read()

# Add SyncPullRow and SyncPushResponse
data_classes = """
data class SyncPullRow(
    val entityType: String,
    val uuid: String,
    val companyId: String,
    val payload: String,
    val serverUpdatedAt: Long,
    val deletedAt: Long,
    val serverVersion: Long
)

data class SyncPushError(
    val operationUuid: String,
    val error: String
)

data class SyncPushResponse(
    val processed: List<String>,
    val errors: List<SyncPushError>
)
"""
content = content.replace("data class WorkspaceInfo", data_classes + "\ndata class WorkspaceInfo")

# Add the syncPushBatch and syncPull functions
new_functions = """
    // --- RPC Record Sync ---
    
    suspend fun syncPushBatch(operations: List<Map<String, Any>>): Result<SyncPushResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            operations.forEach { op ->
                val jsonObj = JSONObject()
                jsonObj.put("operationUuid", op["operationUuid"])
                jsonObj.put("companyId", op["companyId"])
                jsonObj.put("tableName", op["tableName"])
                jsonObj.put("operationType", op["operationType"])
                val payloadStr = op["payload"] as String
                jsonObj.put("payload", JSONObject(payloadStr))
                jsonArray.put(jsonObj)
            }
            
            val requestBody = JSONObject().apply {
                put("p_operations", jsonArray)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/rpc/sync_push_batch")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use Result.failure(Exception("Empty body"))
                    val json = JSONObject(bodyString)
                    val processedArray = json.optJSONArray("processed") ?: JSONArray()
                    val processed = mutableListOf<String>()
                    for (i in 0 until processedArray.length()) {
                        processed.add(processedArray.getString(i))
                    }
                    
                    val errorsArray = json.optJSONArray("errors") ?: JSONArray()
                    val errors = mutableListOf<SyncPushError>()
                    for (i in 0 until errorsArray.length()) {
                        val errObj = errorsArray.getJSONObject(i)
                        errors.add(SyncPushError(
                            operationUuid = errObj.optString("operationUuid"),
                            error = errObj.optString("error")
                        ))
                    }
                    return@use Result.success(SyncPushResponse(processed, errors))
                } else {
                    return@use Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun syncPull(companyId: String, lastServerVersion: Long): Result<List<SyncPullRow>> = withContext(Dispatchers.IO) {
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
                    val bodyString = response.body?.string() ?: return@use Result.success(emptyList())
                    // sync_pull returns an array of objects
                    val array = JSONArray(bodyString)
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
                    return@use Result.success(rows)
                } else {
                    return@use Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
"""

content = re.sub(r'// --- Record Sync ---.*', new_functions, content, flags=re.DOTALL)
content = content + "\n}\n"

with open('app/src/main/java/com/example/data/CloudClient.kt', 'w') as f:
    f.write(content)


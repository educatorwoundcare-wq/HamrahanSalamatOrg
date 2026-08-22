sed -i '/suspend fun patchDeviceHeartbeat/a \
        ensureAuthSession(companyId)' app/src/main/java/com/example/data/CloudClient.kt
sed -i '/suspend fun patchDeviceAuthorization/a \
        ensureAuthSession(companyId)' app/src/main/java/com/example/data/CloudClient.kt
sed -i '/suspend fun uploadRecord/a \
        ensureAuthSession(companyId)' app/src/main/java/com/example/data/CloudClient.kt
sed -i '/suspend fun getCloudRecords/a \
        ensureAuthSession(companyId)' app/src/main/java/com/example/data/CloudClient.kt

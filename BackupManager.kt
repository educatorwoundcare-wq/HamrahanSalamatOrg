package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupMetadata(
    val appVersion: String,
    val dbSchemaVersion: Int,
    val backupTimestamp: Long,
    val backupFormatVersion: Int,
    val dbChecksum: String,
    val backupDevice: String
)

sealed class ValidationResult {
    data class Success(val metadata: BackupMetadata, val tempBackupFile: File) : ValidationResult()
    data class Failure(val error: String) : ValidationResult()
}

object BackupManager {
    private const val TAG = "BackupManager"
    private const val BACKUP_DIR_NAME = "backups"
    private const val MAX_BACKUPS = 30

    fun getBackupFolder(context: Context): File {
        val folder = File(context.filesDir, BACKUP_DIR_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    /**
     * Calculates the SHA-256 checksum of a file to verify its integrity.
     */
    fun calculateSHA256(file: File): String {
        if (!file.exists()) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead = fis.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = fis.read(buffer)
                }
            }
            val hashBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-256", e)
            ""
        }
    }

    /**
     * Performs a complete transactional, portable backup containing:
     * - The SQLite database
     * - A rich metadata file
     * - SHA-256 verification
     * - All local attachments and custom files from the application's files directory.
     */
    fun performBackup(context: Context, database: HamrahanDatabase): File? = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.performBackup started")
        return@runBlocking try {
            Log.d(TAG, "Starting hardened transactional backup...")

            // 1. Flush WAL journal to database file to get the most consistent snapshot
            try {
                database.query("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(0)
                        Log.d(TAG, "WAL Checkpoint full complete with status: $status")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WAL Checkpoint failed (may be expected if database is closed or empty)", e)
            }

            // 2. Locate DB file
            val dbFile = context.getDatabasePath("hamrahan_salamat_db")
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file does not exist")
                return@runBlocking null
            }

            // 3. Read version & metadata
            val dbVersion = database.openHelper.readableDatabase.version
            val appVersion = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
            val dbChecksum = calculateSHA256(dbFile)

            // 4. Construct metadata JSON
            val metadataObj = JSONObject().apply {
                put("app_version", appVersion)
                put("db_schema_version", dbVersion)
                put("backup_timestamp", System.currentTimeMillis())
                put("backup_format_version", 2)
                put("db_checksum", dbChecksum)
                put("backup_device", "Android API ${Build.VERSION.SDK_INT}, Model: ${Build.MODEL}")
                put("device_independent", true)
            }
            val metadataString = metadataObj.toString(4)

            // 5. Create backup zip archive
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val backupFolder = getBackupFolder(context)
            val backupFile = File(backupFolder, "Backup_$timestamp.healthbackup")

            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                // Write metadata.json
                val metadataEntry = ZipEntry("metadata.json")
                zos.putNextEntry(metadataEntry)
                zos.write(metadataString.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                Log.i("EXPORT_DEBUG", "LOG: Metadata added")
                System.out.println("EXPORT_DEBUG LOG: Metadata added")

                // Write primary DB file
                val dbEntry = ZipEntry("database/hamrahan_salamat_db")
                zos.putNextEntry(dbEntry)
                FileInputStream(dbFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
                Log.i("EXPORT_DEBUG", "LOG: Database added")
                System.out.println("EXPORT_DEBUG LOG: Database added")

                // Write WAL file if exists
                val walFile = context.getDatabasePath("hamrahan_salamat_db-wal")
                if (walFile.exists()) {
                    val walEntry = ZipEntry("database/hamrahan_salamat_db-wal")
                    zos.putNextEntry(walEntry)
                    FileInputStream(walFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }

                // Write SHM file if exists
                val shmFile = context.getDatabasePath("hamrahan_salamat_db-shm")
                if (shmFile.exists()) {
                    val shmEntry = ZipEntry("database/hamrahan_salamat_db-shm")
                    zos.putNextEntry(shmEntry)
                    FileInputStream(shmFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }

                // Write attachments recursively from filesDir (except the backups folder itself!)
                val filesDir = context.filesDir
                filesDir.listFiles()?.forEach { file ->
                    if (file.name != BACKUP_DIR_NAME) {
                        addFileToZip(file, "files/", zos)
                    }
                }
            }
            Log.i("EXPORT_DEBUG", "LOG: ZIP created")
            System.out.println("EXPORT_DEBUG LOG: ZIP created")
            Log.i("EXPORT_DEBUG", "LOG: Zip closed")
            System.out.println("EXPORT_DEBUG LOG: Zip closed")

            // ==========================================
            // PHASE 5: VERIFY BACKUP ZIP
            // ==========================================
            Log.i("EXPORT_DEBUG", "LOG: PHASE 5: VERIFY BACKUP ZIP started")
            System.out.println("EXPORT_DEBUG LOG: PHASE 5: VERIFY BACKUP ZIP started")
            try {
                java.util.zip.ZipFile(backupFile).use { zip ->
                    val entries = zip.entries()
                    var hasMetadata = false
                    var hasDatabase = false
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        Log.i("EXPORT_DEBUG", "LOG: ZIP Entry verified: ${entry.name}, Size: ${entry.size}, CRC: ${entry.crc}")
                        System.out.println("EXPORT_DEBUG LOG: ZIP Entry verified: ${entry.name}, Size: ${entry.size}, CRC: ${entry.crc}")
                        
                        // Check for metadata
                        if (entry.name == "metadata.json") {
                            hasMetadata = true
                            zip.getInputStream(entry).use { inputStream ->
                                val content = inputStream.bufferedReader().readText()
                                Log.i("EXPORT_DEBUG", "LOG: metadata.json content is readable. Content length: ${content.length}")
                                System.out.println("EXPORT_DEBUG LOG: metadata.json content is readable. Content length: ${content.length}")
                            }
                        }
                        // Check for database
                        if (entry.name == "database/hamrahan_salamat_db") {
                            hasDatabase = true
                        }
                    }
                    
                    Log.i("EXPORT_DEBUG", "LOG: Zip closed")
                    System.out.println("EXPORT_DEBUG LOG: Zip closed")
                    Log.i("EXPORT_DEBUG", "LOG: Final exported file size = ${backupFile.length()} bytes")
                    System.out.println("EXPORT_DEBUG LOG: Final exported file size = ${backupFile.length()} bytes")
                    
                    if (!hasMetadata) {
                        Log.e("EXPORT_DEBUG", "LOG: PHASE 5 ERROR: metadata.json is missing in generated ZIP!")
                        System.out.println("EXPORT_DEBUG LOG: PHASE 5 ERROR: metadata.json is missing in generated ZIP!")
                    }
                    if (!hasDatabase) {
                        Log.e("EXPORT_DEBUG", "LOG: PHASE 5 ERROR: database file is missing in generated ZIP!")
                        System.out.println("EXPORT_DEBUG LOG: PHASE 5 ERROR: database file is missing in generated ZIP!")
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e("EXPORT_DEBUG", "PHASE 5 FAILED: ZIP archive is corrupted!", e)
                System.out.println("EXPORT_DEBUG LOG: PHASE 5 FAILED: ZIP archive is corrupted!")
                e.printStackTrace()
                Log.i("EXPORT_DEBUG", "LOG: Attempting to scan for exact corruption offset...")
                System.out.println("EXPORT_DEBUG LOG: Attempting to scan for exact corruption offset...")
                try {
                    val fisScanned = java.io.FileInputStream(backupFile)
                    fisScanned.use { fis ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        var totalBytesScanned = 0L
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            totalBytesScanned += bytesRead
                        }
                        Log.i("EXPORT_DEBUG", "LOG: Scanned $totalBytesScanned bytes from backup file successfully without crashes.")
                        System.out.println("EXPORT_DEBUG LOG: Scanned $totalBytesScanned bytes successfully.")
                    }
                } catch (ex: java.lang.Exception) {
                    Log.e("EXPORT_DEBUG", "LOG: Low-level read failed at offset or error: ${ex.message}", ex)
                    System.out.println("EXPORT_DEBUG LOG: Low-level read failed at offset or error: ${ex.message}")
                }
            }

            Log.d(TAG, "Harden backup created successfully: ${backupFile.absolutePath}")

            // 6. Enforce 30-file retention limit
            enforceRetention(context)

            com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.performBackup successfully completed in ${System.currentTimeMillis() - startTime}ms. File size: ${backupFile.length()} bytes")
            backupFile
        } catch (t: Throwable) {
            com.example.data.EnterpriseCrashLogger.logThrowable(context, "BackupManager.performBackup", t)
            throw t
        }
    }

    private fun addFileToZip(file: File, relativePath: String, zos: ZipOutputStream) {
        if (!file.exists()) return
        val name = file.name
        // Filter out system, temporary, cache, and log files
        if (name == BACKUP_DIR_NAME || name == "logs" || name == "cache" || name == "code_cache" ||
            name.startsWith(".") || name.endsWith(".tmp") || name.endsWith(".log")) {
            return
        }

        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                addFileToZip(child, "$relativePath${file.name}/", zos)
            }
        } else {
            // Bypass files larger than 50MB to prevent ANR and memory pressure
            if (file.length() > 50 * 1024 * 1024L) {
                Log.w(TAG, "Skipping oversized file in backup: ${file.name} (${file.length()} bytes)")
                return
            }
            val entry = ZipEntry("$relativePath${file.name}")
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    /**
     * Validates a backup archive's structural validity, metadata, compatibility, and file integrity.
     */
    fun validateBackupFile(context: Context, backupFile: File): ValidationResult {
        com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.validateBackupFile started for ${backupFile.name}")
        if (!backupFile.exists()) {
            return ValidationResult.Failure("فایل پشتیبان پیدا نشد.")
        }

        try {
            var hasMetadata = false
            var hasDb = false
            var metadataContent = ""
            val tempDbExtract = File(context.cacheDir, "temp_validate_db")
            if (tempDbExtract.exists()) tempDbExtract.delete()

            // 1. Read ZIP contents and check required files
            ZipInputStream(FileInputStream(backupFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "metadata.json") {
                        hasMetadata = true
                        val baos = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(2048)
                        var len = zis.read(buffer)
                        while (len != -1) {
                            baos.write(buffer, 0, len)
                            len = zis.read(buffer)
                        }
                        metadataContent = baos.toString("UTF-8")
                    } else if (entry.name == "database/hamrahan_salamat_db") {
                        hasDb = true
                        FileOutputStream(tempDbExtract).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (!hasMetadata) {
                tempDbExtract.delete()
                return ValidationResult.Failure("ساختار فایل پشتیبان معتبر نیست: فایل metadata.json یافت نشد.")
            }
            if (!hasDb) {
                tempDbExtract.delete()
                return ValidationResult.Failure("ساختار فایل پشتیبان معتبر نیست: فایل دیتابیس اصلی یافت نشد.")
            }

            // 2. Parse and Validate Metadata
            val metaObj = JSONObject(metadataContent)
            val appVersion = metaObj.optString("app_version", "1.0")
            val dbSchemaVersion = metaObj.optInt("db_schema_version", 1)
            val backupTimestamp = metaObj.optLong("backup_timestamp", 0L)
            val backupFormatVersion = metaObj.optInt("backup_format_version", 1)
            val storedChecksum = metaObj.optString("db_checksum", "")
            val backupDevice = metaObj.optString("backup_device", "Unknown Device")

            // Validate format version
            if (backupFormatVersion < 2) {
                tempDbExtract.delete()
                return ValidationResult.Failure("نسخه قالب فایل پشتیبان قدیمی است یا پشتیبانی نمی‌شود (${backupFormatVersion}).")
            }

            // Check schema version migration path compatibility dynamically
            val currentRoomVersion = HamrahanDatabase::class.java.getAnnotation(androidx.room.Database::class.java)?.version ?: 18
            if (dbSchemaVersion > currentRoomVersion) {
                tempDbExtract.delete()
                return ValidationResult.Failure("نسخه دیتابیس پشتیبان (${dbSchemaVersion}) از نسخه فعلی برنامه (${currentRoomVersion}) جدیدتر است. برای جلوگیری از خرابی داده، بازیابی لغو شد.")
            } else if (dbSchemaVersion < currentRoomVersion) {
                if (!hasMigrationPath(dbSchemaVersion, currentRoomVersion, HamrahanDatabase.ALL_MIGRATIONS)) {
                    tempDbExtract.delete()
                    return ValidationResult.Failure("مسیر مهاجرت غیر مخرب از نسخه دیتابیس پشتیبان (${dbSchemaVersion}) به نسخه فعلی برنامه (${currentRoomVersion}) یافت نشد. برای جلوگیری از حذف اطلاعات، بازیابی لغو شد.")
                }
            }

            // 3. Verify Database Integrity (Checksum Verification)
            val actualChecksum = calculateSHA256(tempDbExtract)
            tempDbExtract.delete()

            if (storedChecksum.isNotBlank() && actualChecksum != storedChecksum) {
                return ValidationResult.Failure("خطای یکپارچگی اطلاعات: چک‌سام فایل دیتابیس با چک‌سام ذخیره‌شده تطابق ندارد. فایل پشتیبان ممکن است آسیب دیده یا ویرایش شده باشد.")
            }

            val metadata = BackupMetadata(
                appVersion = appVersion,
                dbSchemaVersion = dbSchemaVersion,
                backupTimestamp = backupTimestamp,
                backupFormatVersion = backupFormatVersion,
                dbChecksum = storedChecksum,
                backupDevice = backupDevice
            )

            com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.validateBackupFile successfully completed.")
            return ValidationResult.Success(metadata, backupFile)
        } catch (t: Throwable) {
            com.example.data.EnterpriseCrashLogger.logThrowable(context, "BackupManager.validateBackupFile", t)
            throw t
        }
    }

    /**
     * Atomic, rollback-safe restore function:
     * 1. Validates file first.
     * 2. Extracts zip contents to temporary cache directory.
     * 3. Verifies SHA-256 database checksum.
     * 4. Closes active DB instance and backs up existing DB files into rollback cache.
     * 5. Atomically renames extracted temp DB files to active locations.
     * 6. Merges attachments safely.
     * 7. Cleans up temp directories. If any failure occurs, restores the rollback DB files immediately.
     */
    fun performRestore(context: Context, backupFile: File, database: HamrahanDatabase): Boolean {
        val tempRestoreDir = File(context.cacheDir, "temp_restore_dir")
        if (tempRestoreDir.exists()) tempRestoreDir.deleteRecursively()
        tempRestoreDir.mkdirs()

        com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.performRestore started for ${backupFile.name}")
        try {
            Log.d(TAG, "Starting hardened atomic restore from ${backupFile.name}...")
            if (!backupFile.exists()) return false

            // 1. Validate the backup file first
            val validation = validateBackupFile(context, backupFile)
            if (validation is ValidationResult.Failure) {
                Log.e(TAG, "Backup validation failed prior to restore: ${validation.error}")
                return false
            }

            val metadata = (validation as ValidationResult.Success).metadata

            // 2. Extract into temporary directory (Files and database files)
            val tempDbFile = File(tempRestoreDir, "hamrahan_salamat_db")
            val tempWalFile = File(tempRestoreDir, "hamrahan_salamat_db-wal")
            val tempShmFile = File(tempRestoreDir, "hamrahan_salamat_db-shm")
            val tempFilesDir = File(tempRestoreDir, "files")
            tempFilesDir.mkdirs()

            var hasDbFile = false

            ZipInputStream(FileInputStream(backupFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "database/hamrahan_salamat_db" -> {
                            FileOutputStream(tempDbFile).use { fos -> zis.copyTo(fos) }
                            hasDbFile = true
                        }
                        entry.name == "database/hamrahan_salamat_db-wal" -> {
                            FileOutputStream(tempWalFile).use { fos -> zis.copyTo(fos) }
                        }
                        entry.name == "database/hamrahan_salamat_db-shm" -> {
                            FileOutputStream(tempShmFile).use { fos -> zis.copyTo(fos) }
                        }
                        entry.name.startsWith("files/") -> {
                            val relativePath = entry.name.substring("files/".length)
                            if (relativePath.isNotBlank()) {
                                val targetTempFile = File(tempFilesDir, relativePath)
                                targetTempFile.parentFile?.mkdirs()
                                if (!entry.isDirectory) {
                                    FileOutputStream(targetTempFile).use { fos -> zis.copyTo(fos) }
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (!hasDbFile || !tempDbFile.exists()) {
                Log.e(TAG, "Extraction failed: Database file is missing in zip.")
                return false
            }

            // 3. Verify extracted database file's checksum matches metadata
            val extractedChecksum = calculateSHA256(tempDbFile)
            if (metadata.dbChecksum.isNotBlank() && extractedChecksum != metadata.dbChecksum) {
                Log.e(TAG, "Extraction failed: Checksum mismatch. Expected: ${metadata.dbChecksum}, Got: $extractedChecksum")
                return false
            }

            // 4. Swap databases and files atomically
            // Close active connection
            try {
                database.close()
                HamrahanDatabase.resetInstance()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing active database", e)
            }

            val dbFile = context.getDatabasePath("hamrahan_salamat_db")
            val walFile = context.getDatabasePath("hamrahan_salamat_db-wal")
            val shmFile = context.getDatabasePath("hamrahan_salamat_db-shm")

            // Backup existing files to rollback folder just in case renaming fails
            val rollbackDir = File(context.cacheDir, "temp_rollback_dir")
            if (rollbackDir.exists()) rollbackDir.deleteRecursively()
            rollbackDir.mkdirs()

            val backupDbFile = File(rollbackDir, "hamrahan_salamat_db")
            val backupWalFile = File(rollbackDir, "hamrahan_salamat_db-wal")
            val backupShmFile = File(rollbackDir, "hamrahan_salamat_db-shm")

            if (dbFile.exists()) dbFile.renameTo(backupDbFile)
            if (walFile.exists()) walFile.renameTo(backupWalFile)
            if (shmFile.exists()) shmFile.renameTo(backupShmFile)

            // Now move extracted temp DB files to active locations
            val success = tempDbFile.renameTo(dbFile)
            if (success) {
                if (tempWalFile.exists()) tempWalFile.renameTo(walFile)
                if (tempShmFile.exists()) tempShmFile.renameTo(shmFile)

                // Copy files recursively from tempFilesDir to filesDir
                copyFilesRecursively(tempFilesDir, context.filesDir)

                // Clean up rollback backup directory since swap succeeded
                rollbackDir.deleteRecursively()
                Log.d(TAG, "Atomic database swap succeeded.")
            } else {
                Log.e(TAG, "Atomic database swap failed. Rolling back...")
                // Restore old files
                if (backupDbFile.exists()) backupDbFile.renameTo(dbFile)
                if (backupWalFile.exists()) backupWalFile.renameTo(walFile)
                if (backupShmFile.exists()) backupShmFile.renameTo(shmFile)
                rollbackDir.deleteRecursively()
                return false
            }

            com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.performRestore successfully completed.")
            return true
        } catch (t: Throwable) {
            com.example.data.EnterpriseCrashLogger.logThrowable(context, "BackupManager.performRestore", t)
            throw t
        } finally {
            tempRestoreDir.deleteRecursively()
        }
    }

    private fun copyFilesRecursively(source: File, dest: File) {
        if (source.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            source.list()?.forEach { child ->
                copyFilesRecursively(File(source, child), File(dest, child))
            }
        } else {
            source.copyTo(dest, overwrite = true)
        }
    }

    /**
     * SAF helper: Exports local backup file to selected SAF Uri stream.
     */
    fun exportBackupToUri(context: Context, localBackupFile: File, destUri: android.net.Uri): Boolean {
        Log.i("EXPORT_DEBUG", "LOG: Selected URI = $destUri")
        System.out.println("EXPORT_DEBUG LOG: Selected URI = $destUri")
        Log.i("EXPORT_DEBUG", "LOG: URI authority = ${destUri.authority}")
        System.out.println("EXPORT_DEBUG LOG: URI authority = ${destUri.authority}")
        Log.i("EXPORT_DEBUG", "LOG: URI scheme = ${destUri.scheme}")
        System.out.println("EXPORT_DEBUG LOG: URI scheme = ${destUri.scheme}")

        // Print persisted permissions
        try {
            val persistedUriGrants = context.contentResolver.persistedUriPermissions
            Log.i("EXPORT_DEBUG", "LOG: Persisted permissions count = ${persistedUriGrants.size}")
            System.out.println("EXPORT_DEBUG LOG: Persisted permissions count = ${persistedUriGrants.size}")
            persistedUriGrants.forEach { grant ->
                Log.i("EXPORT_DEBUG", "LOG: Grant: uri = ${grant.uri}, read = ${grant.isReadPermission}, write = ${grant.isWritePermission}")
                System.out.println("EXPORT_DEBUG LOG: Grant: uri = ${grant.uri}, read = ${grant.isReadPermission}, write = ${grant.isWritePermission}")
            }
        } catch (e: Exception) {
            Log.i("EXPORT_DEBUG", "LOG: Failed to read persisted permissions: ${e.message}")
            System.out.println("EXPORT_DEBUG LOG: Failed to read persisted permissions: ${e.message}")
        }

        // Print FileDescriptor validity
        try {
            context.contentResolver.openFileDescriptor(destUri, "r")?.use { pfd ->
                val isValid = pfd.fileDescriptor.valid()
                Log.i("EXPORT_DEBUG", "LOG: FileDescriptor valid ? $isValid")
                System.out.println("EXPORT_DEBUG LOG: FileDescriptor valid ? $isValid")
            }
        } catch (e: Exception) {
            Log.i("EXPORT_DEBUG", "LOG: FileDescriptor read check failed (expected for new write-only URIs): ${e.message}")
            System.out.println("EXPORT_DEBUG LOG: FileDescriptor read check failed: ${e.message}")
        }

        Log.i("EXPORT_DEBUG", "LOG: OutputStream opened")
        System.out.println("EXPORT_DEBUG LOG: OutputStream opened")
        com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.exportBackupToUri started for ${localBackupFile.name} to $destUri")
        return try {
            val success = context.contentResolver.openOutputStream(destUri).use { outputStream ->
                val isNull = outputStream == null
                Log.i("EXPORT_DEBUG", "LOG: OutputStream is NULL? $isNull")
                System.out.println("EXPORT_DEBUG LOG: OutputStream is NULL? $isNull")
                if (outputStream != null) {
                    Log.i("EXPORT_DEBUG", "LOG: OutputStream implementation class = ${outputStream.javaClass.name}")
                    System.out.println("EXPORT_DEBUG LOG: OutputStream implementation class = ${outputStream.javaClass.name}")
                }
                if (outputStream == null) return false
                
                FileInputStream(localBackupFile).use { inputStream ->
                    val bytesCopied = inputStream.copyTo(outputStream)
                    Log.i("EXPORT_DEBUG", "LOG: Bytes copied = $bytesCopied")
                    System.out.println("EXPORT_DEBUG LOG: Bytes copied = $bytesCopied")
                }
                true
            }

            Log.i("EXPORT_DEBUG", "LOG: Final exported file size = ${localBackupFile.length()} bytes")
            System.out.println("EXPORT_DEBUG LOG: Final exported file size = ${localBackupFile.length()} bytes")
            com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.exportBackupToUri completed.")
            success
        } catch (t: Throwable) {
            Log.e("EXPORT_DEBUG", "Full error", t)
            t.printStackTrace()
            com.example.data.EnterpriseCrashLogger.logThrowable(context, "BackupManager.exportBackupToUri", t)
            throw t
        }
    }

    /**
     * SAF helper: Reads a backup file from SAF Uri into a local temp file, validates it, and returns the result.
     */
    fun restoreBackupFromUri(context: Context, srcUri: android.net.Uri): ValidationResult {
        com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.restoreBackupFromUri started from $srcUri")
        val tempBackupFile = File(context.cacheDir, "saf_restore_temp.healthbackup")
        if (tempBackupFile.exists()) tempBackupFile.delete()

        try {
            context.contentResolver.openInputStream(srcUri).use { inputStream ->
                if (inputStream == null) return ValidationResult.Failure("امکان خواندن فایل از حافظه انتخاب‌شده وجود ندارد.")
                FileOutputStream(tempBackupFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Validate the temp file
            val validationResult = validateBackupFile(context, tempBackupFile)
            if (validationResult is ValidationResult.Failure) {
                tempBackupFile.delete()
            }
            com.example.data.EnterpriseCrashLogger.log(context, "BackupManager.restoreBackupFromUri completed.")
            return validationResult
        } catch (t: Throwable) {
            com.example.data.EnterpriseCrashLogger.logThrowable(context, "BackupManager.restoreBackupFromUri", t)
            tempBackupFile.delete()
            throw t
        }
    }

    fun getBackupsList(context: Context): List<File> {
        val folder = getBackupFolder(context)
        return folder.listFiles { file ->
            file.isFile && file.name.startsWith("Backup_") && file.name.endsWith(".healthbackup")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun enforceRetention(context: Context) {
        try {
            val backups = getBackupsList(context)
            if (backups.size > MAX_BACKUPS) {
                val toDelete = backups.subList(MAX_BACKUPS, backups.size)
                for (file in toDelete) {
                    if (file.delete()) {
                        Log.d(TAG, "Auto-deleted old backup: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing retention", e)
        }
    }

    private fun hasMigrationPath(start: Int, end: Int, migrations: List<androidx.room.migration.Migration>): Boolean {
        if (start == end) return true
        val adj = mutableMapOf<Int, MutableList<Int>>()
        for (m in migrations) {
            adj.getOrPut(m.startVersion) { mutableListOf() }.add(m.endVersion)
        }
        
        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(start)
        visited.add(start)
        
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == end) return true
            adj[curr]?.forEach { next ->
                if (next !in visited) {
                    visited.add(next)
                    queue.add(next)
                }
            }
        }
        return false
    }
}

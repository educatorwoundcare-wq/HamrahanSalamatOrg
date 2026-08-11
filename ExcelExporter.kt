package com.example.data

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import com.example.ui.formatDate
import com.example.ui.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
    var bytesWritten = 0L
        private set
    var writeCount = 0
        private set
    var closeCount = 0
        private set
    var flushCount = 0
        private set

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten++
        writeCount++
    }

    override fun write(b: ByteArray) {
        delegate.write(b)
        bytesWritten += b.size
        writeCount++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len
        writeCount++
    }

    override fun flush() {
        flushCount++
        delegate.flush()
    }

    override fun close() {
        closeCount++
        delegate.close()
    }
}

object EnterpriseCrashLogger {
    fun getLogFile(context: Context): File {
        val baseDir = context.getExternalFilesDir("logs") ?: File(context.filesDir, "logs")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return File(baseDir, "export_log.txt")
    }

    fun log(context: Context, message: String) {
        try {
            val file = getLogFile(context)
            file.appendText("[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $message\n")
            Log.i("EnterpriseLogger", message)
        } catch (t: Throwable) {
            Log.e("EnterpriseLogger", "Failed to write to log file", t)
        }
    }

    fun logStart(
        context: Context,
        patientsCount: Int,
        transactionsCount: Int,
        workbookType: String,
        outputUri: String
    ) {
        val runtime = Runtime.getRuntime()
        val freeMem = runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val totalMem = runtime.totalMemory()
        val usedMem = totalMem - freeMem
        
        val sb = StringBuilder()
        sb.append("\n========== EXPORT START ==========\n")
        sb.append("Patients : $patientsCount\n")
        sb.append("Transactions : $transactionsCount\n")
        sb.append("Memory free : ${freeMem / (1024 * 1024)} MB\n")
        sb.append("Memory max : ${maxMem / (1024 * 1024)} MB\n")
        sb.append("Memory total : ${totalMem / (1024 * 1024)} MB\n")
        sb.append("Memory used : ${usedMem / (1024 * 1024)} MB\n")
        sb.append("Android Version : API ${android.os.Build.VERSION.SDK_INT}, Release ${android.os.Build.VERSION.RELEASE}\n")
        sb.append("Workbook Type : $workbookType\n")
        sb.append("Output Uri : $outputUri\n")
        sb.append("==================================\n")
        log(context, sb.toString())
    }

    fun logPhase(context: Context, phase: String, durationMs: Long? = null, additionalInfo: String? = null) {
        val runtime = Runtime.getRuntime()
        val freeMem = runtime.freeMemory()
        val totalMem = runtime.totalMemory()
        val usedMem = totalMem - freeMem
        
        val durationStr = if (durationMs != null) " (Duration: ${durationMs}ms)" else ""
        val infoStr = if (additionalInfo != null) " [$additionalInfo]" else ""
        log(context, "Phase: $phase$durationStr$infoStr | Used Heap: ${usedMem / (1024 * 1024)} MB / Total Heap: ${totalMem / (1024 * 1024)} MB")
    }

    fun logThrowable(context: Context, phase: String, t: Throwable) {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        val stackTraceStr = sw.toString()
        
        val sb = StringBuilder()
        sb.append("\n!!! CRITICAL THROWABLE IN PHASE: $phase !!!\n")
        sb.append("Throwable Class: ${t.javaClass.name}\n")
        sb.append("Message: ${t.message}\n")
        sb.append("Cause: ${t.cause}\n")
        sb.append("Stacktrace:\n$stackTraceStr\n")
        sb.append("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n")
        
        log(context, sb.toString())
    }
}

object ForensicAuditor {
    fun performForensicVerification(
        context: Context,
        tempFile: File,
        destUri: android.net.Uri,
        laterUri: android.net.Uri? = null
    ): Boolean {
        val tag = "FORENSIC_AUDIT"
        Log.i(tag, "=== STARTING BYTE-FOR-BYTE FORENSIC COMPARISON ===")
        System.out.println("=== STARTING BYTE-FOR-BYTE FORENSIC COMPARISON ===")
        EnterpriseCrashLogger.log(context, "=== STARTING BYTE-FOR-BYTE FORENSIC COMPARISON ===")

        try {
            val mimeType = context.contentResolver.getType(destUri)
            Log.i(tag, "1. Destination MIME Type: $mimeType")
            System.out.println("1. Destination MIME Type: $mimeType")
            EnterpriseCrashLogger.log(context, "1. Destination MIME Type: $mimeType")

            val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, destUri)
            val docLength = docFile?.length() ?: -1L
            val docLastModified = docFile?.lastModified() ?: 0L
            Log.i(tag, "2. DocumentFile Length: $docLength bytes")
            System.out.println("2. DocumentFile Length: $docLength bytes")
            EnterpriseCrashLogger.log(context, "2. DocumentFile Length: $docLength bytes")
            Log.i(tag, "3. DocumentFile Last Modified: $docLastModified")
            System.out.println("3. DocumentFile Last Modified: $docLastModified")
            EnterpriseCrashLogger.log(context, "3. DocumentFile Last Modified: $docLastModified")

            Log.i(tag, "4. Canonical SAF URI: $destUri")
            System.out.println("4. Canonical SAF URI: $destUri")
            EnterpriseCrashLogger.log(context, "4. Canonical SAF URI: $destUri")

            if (laterUri != null) {
                Log.i(tag, "5. Later Shared/Opened URI: $laterUri")
                System.out.println("5. Later Shared/Opened URI: $laterUri")
                EnterpriseCrashLogger.log(context, "5. Later Shared/Opened URI: $laterUri")
                val areUrisIdentical = destUri.toString() == laterUri.toString()
                Log.i(tag, "6. Are URIs Identical? $areUrisIdentical")
                System.out.println("6. Are URIs Identical? $areUrisIdentical")
                EnterpriseCrashLogger.log(context, "6. Are URIs Identical? $areUrisIdentical")
            } else {
                Log.i(tag, "5. Later Shared/Opened URI: N/A (Direct Save)")
                System.out.println("5. Later Shared/Opened URI: N/A (Direct Save)")
                EnterpriseCrashLogger.log(context, "5. Later Shared/Opened URI: N/A (Direct Save)")
            }

            val tempSha = calculateFileSHA256(tempFile)
            val destSha = context.contentResolver.openInputStream(destUri)?.use { calculateStreamSHA256(it) } ?: ""
            Log.i(tag, "7. Temp File SHA-256: $tempSha")
            System.out.println("7. Temp File SHA-256: $tempSha")
            EnterpriseCrashLogger.log(context, "7. Temp File SHA-256: $tempSha")
            Log.i(tag, "8. Destination URI SHA-256: $destSha")
            System.out.println("8. Destination URI SHA-256: $destSha")
            EnterpriseCrashLogger.log(context, "8. Destination URI SHA-256: $destSha")

            val tempCrc = calculateFileCRC32(tempFile)
            val destCrc = context.contentResolver.openInputStream(destUri)?.use { calculateStreamCRC32(it) } ?: 0L
            Log.i(tag, "9. Temp File CRC32: $tempCrc")
            System.out.println("9. Temp File CRC32: $tempCrc")
            EnterpriseCrashLogger.log(context, "9. Temp File CRC32: $tempCrc")
            Log.i(tag, "10. Destination URI CRC32: $destCrc")
            System.out.println("10. Destination URI CRC32: $destCrc")
            EnterpriseCrashLogger.log(context, "10. Destination URI CRC32: $destCrc")

            val byteMatch = tempSha.isNotEmpty() && tempSha == destSha && (docLength < 0 || docLength == tempFile.length())
            Log.i(tag, "11. Byte-for-byte match: $byteMatch")
            System.out.println("11. Byte-for-byte match: $byteMatch")
            EnterpriseCrashLogger.log(context, "11. Byte-for-byte match: $byteMatch")

            var excelValid = false
            var zipValid = false
            if (tempFile.name.endsWith(".xlsx")) {
                try {
                    context.contentResolver.openInputStream(destUri).use { ins ->
                        if (ins != null) {
                            WorkbookFactory.create(ins).use { wb ->
                                excelValid = wb.numberOfSheets > 0
                            }
                        }
                    }
                    Log.i(tag, "12. WorkbookFactory.create on Destination Input Stream: VALID")
                    System.out.println("12. WorkbookFactory.create on Destination Input Stream: VALID")
                    EnterpriseCrashLogger.log(context, "12. WorkbookFactory.create on Destination Input Stream: VALID")
                } catch (e: Exception) {
                    Log.e(tag, "12. WorkbookFactory.create on Destination Input Stream: CORRUPTED!", e)
                    System.out.println("12. WorkbookFactory.create on Destination Input Stream: CORRUPTED!")
                    EnterpriseCrashLogger.logThrowable(context, "12. WorkbookFactory.create on Destination Input Stream: CORRUPTED!", e)
                }
            } else {
                try {
                    val validationFile = File(context.cacheDir, "forensic_dest_validation.zip")
                    if (validationFile.exists()) validationFile.delete()
                    try {
                        context.contentResolver.openInputStream(destUri)?.use { ins ->
                            validationFile.outputStream().use { outs ->
                                ins.copyTo(outs)
                            }
                        }
                        java.util.zip.ZipFile(validationFile).use { zip ->
                            zipValid = zip.size() > 0
                        }
                    } finally {
                        if (validationFile.exists()) validationFile.delete()
                    }
                    Log.i(tag, "12. ZipFile verification on Destination File: VALID")
                    System.out.println("12. ZipFile verification on Destination File: VALID")
                    EnterpriseCrashLogger.log(context, "12. ZipFile verification on Destination File: VALID")
                } catch (e: Exception) {
                    Log.e(tag, "12. ZipFile verification on Destination File: CORRUPTED!", e)
                    System.out.println("12. ZipFile verification on Destination File: CORRUPTED!")
                    EnterpriseCrashLogger.logThrowable(context, "12. ZipFile verification on Destination File: CORRUPTED!", e)
                }
            }

            val allValid = byteMatch && (excelValid || zipValid || (!tempFile.name.endsWith(".xlsx") && !tempFile.name.endsWith(".zip") && !tempFile.name.endsWith(".healthbackup")))
            Log.i(tag, "13. Overall Forensic Validity Check: ${if (allValid) "SUCCESS" else "FAILED"}")
            System.out.println("13. Overall Forensic Validity Check: ${if (allValid) "SUCCESS" else "FAILED"}")
            EnterpriseCrashLogger.log(context, "13. Overall Forensic Validity Check: ${if (allValid) "SUCCESS" else "FAILED"}")
            Log.i(tag, "=== FORENSIC COMPARISON COMPLETED ===")
            System.out.println("=== FORENSIC COMPARISON COMPLETED ===")
            EnterpriseCrashLogger.log(context, "=== FORENSIC COMPARISON COMPLETED ===")
            return allValid
        } catch (e: Exception) {
            Log.e(tag, "ERROR DURING FORENSIC COMPARISON", e)
            System.out.println("ERROR DURING FORENSIC COMPARISON: ${e.message}")
            EnterpriseCrashLogger.logThrowable(context, "ERROR DURING FORENSIC COMPARISON", e)
            return false
        }
    }

    private fun calculateFileSHA256(file: File): String {
        return file.inputStream().use { calculateStreamSHA256(it) }
    }

    private fun calculateStreamSHA256(stream: InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { String.format("%02x", it) }
    }

    private fun calculateFileCRC32(file: File): Long {
        return file.inputStream().use { calculateStreamCRC32(it) }
    }

    private fun calculateStreamCRC32(stream: InputStream): Long {
        val crc = java.util.zip.CRC32()
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            crc.update(buffer, 0, bytesRead)
        }
        return crc.value
    }
}

class SheetGenerationException(
    val sheetName: String,
    val operation: String,
    val originalException: Throwable
) : Exception("Failed generating sheet '$sheetName' during '$operation': [${originalException.javaClass.name}] ${originalException.message}", originalException)

object ExcelExporter {

    private inline fun executeSheetGeneration(
        context: Context,
        sheetName: String,
        operationName: String,
        block: () -> Unit
    ) {
        try {
            block()
            Log.i("EXPORT_DEBUG", "LOG: Sheet '$sheetName' ($operationName) generated successfully")
        } catch (e: Throwable) {
            val details = "Sheet Generation Error -> Sheet: '$sheetName' | Operation: '$operationName' | ExceptionType: ${e.javaClass.name} | Message: ${e.message}"
            Log.e("EXPORT_DEBUG", details, e)
            EnterpriseCrashLogger.log(context, details)
            EnterpriseCrashLogger.logThrowable(context, "Failed Sheet Generation: $sheetName", e)
            throw SheetGenerationException(sheetName, operationName, e)
        }
    }

    // Helper extension to safely write cell contents with appropriate styles and null safety
    private fun Row.createSafeCell(colIndex: Int, value: Any?, style: CellStyle) {
        val cell = this.createCell(colIndex)
        cell.cellStyle = style
        if (value == null) {
            cell.setCellValue("")
            return
        }
        when (value) {
            is Double -> cell.setCellValue(value)
            is Int -> cell.setCellValue(value.toDouble())
            is Long -> cell.setCellValue(value.toDouble())
            is Float -> cell.setCellValue(value.toDouble())
            is Boolean -> cell.setCellValue(if (value) "بله" else "خیر")
            else -> cell.setCellValue(value.toString())
        }
    }

    /**
     * Phase 4 & 5: Modern high-performance .xlsx export engine compatible with Microsoft Excel and Google Sheets.
     * Memory-optimized, IO-bound execution with strictly pre-allocated, reusable CellStyle instances.
     */
    fun exportSnapshotToExcel(
        context: Context,
        outputStream: OutputStream,
        snapshot: ReportingLayer.BusinessReportSnapshot
    ): Boolean = runBlocking(Dispatchers.IO) {
        Log.i("EXPORT_DEBUG", "LOG: Export started on Dispatchers.IO")
        System.out.println("EXPORT_DEBUG LOG: Export started on Dispatchers.IO")
        Log.i("EXPORT_DEBUG", "LOG: Database snapshot started")
        System.out.println("EXPORT_DEBUG LOG: Database snapshot started")
        Log.i("EXPORT_DEBUG", "LOG: Patients count = ${snapshot.patients.size}")
        System.out.println("EXPORT_DEBUG LOG: Patients count = ${snapshot.patients.size}")
        Log.i("EXPORT_DEBUG", "LOG: Staff count = ${snapshot.personnel.size}")
        System.out.println("EXPORT_DEBUG LOG: Staff count = ${snapshot.personnel.size}")
        Log.i("EXPORT_DEBUG", "LOG: Services count = ${snapshot.services.size}")
        System.out.println("EXPORT_DEBUG LOG: Services count = ${snapshot.services.size}")
        Log.i("EXPORT_DEBUG", "LOG: Registrations count = ${snapshot.serviceRegistrations.size}")
        System.out.println("EXPORT_DEBUG LOG: Registrations count = ${snapshot.serviceRegistrations.size}")
        Log.i("EXPORT_DEBUG", "LOG: Expenses count = ${snapshot.expenses.size}")
        System.out.println("EXPORT_DEBUG LOG: Expenses count = ${snapshot.expenses.size}")
        Log.i("EXPORT_DEBUG", "LOG: Financial Transactions count = ${snapshot.financialTransactions.size}")
        System.out.println("EXPORT_DEBUG LOG: Financial Transactions count = ${snapshot.financialTransactions.size}")

        val firstPatientId = snapshot.patients.firstOrNull()?.id ?: "N/A"
        val lastPatientId = snapshot.patients.lastOrNull()?.id ?: "N/A"
        val firstStaffId = snapshot.personnel.firstOrNull()?.id ?: "N/A"
        val lastStaffId = snapshot.personnel.lastOrNull()?.id ?: "N/A"
        val firstServiceId = snapshot.services.firstOrNull()?.id ?: "N/A"
        val lastServiceId = snapshot.services.lastOrNull()?.id ?: "N/A"

        Log.i("EXPORT_DEBUG", "LOG: First Patient ID = $firstPatientId, Last Patient ID = $lastPatientId")
        System.out.println("EXPORT_DEBUG LOG: First Patient ID = $firstPatientId, Last Patient ID = $lastPatientId")
        Log.i("EXPORT_DEBUG", "LOG: First Staff ID = $firstStaffId, Last Staff ID = $lastStaffId")
        System.out.println("EXPORT_DEBUG LOG: First Staff ID = $firstStaffId, Last Staff ID = $lastStaffId")
        Log.i("EXPORT_DEBUG", "LOG: First Service ID = $firstServiceId, Last Service ID = $lastServiceId")
        System.out.println("EXPORT_DEBUG LOG: First Service ID = $firstServiceId, Last Service ID = $lastServiceId")

        val isSnapshotNotEmpty = snapshot.patients.isNotEmpty() || snapshot.personnel.isNotEmpty() || snapshot.services.isNotEmpty()
        Log.i("EXPORT_DEBUG", "LOG: Verify snapshot is NOT empty? $isSnapshotNotEmpty")
        System.out.println("EXPORT_DEBUG LOG: Verify snapshot is NOT empty? $isSnapshotNotEmpty")

        val startTime = System.currentTimeMillis()
        EnterpriseCrashLogger.logStart(
            context = context,
            patientsCount = snapshot.patients.size,
            transactionsCount = snapshot.financialTransactions.size,
            workbookType = "XSSFWorkbook (.xlsx Modern)",
            outputUri = outputStream.toString()
        )

        var activeWorkbook: XSSFWorkbook? = null
        var tempFile: File? = null

        try {
            val workbook = XSSFWorkbook()
            activeWorkbook = workbook
            Log.i("EXPORT_DEBUG", "LOG: Workbook created")
            System.out.println("EXPORT_DEBUG LOG: Workbook created")

            // --- REUSABLE WORKBOOK-LEVEL DATA FORMAT & CELL STYLES (CREATED ONCE) ---
            val dataFormatter = workbook.createDataFormat()

            val titleFont = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 14
                fontName = "Tahoma"
                color = IndexedColors.WHITE.index
            }
            val titleStyle = workbook.createCellStyle().apply {
                setFont(titleFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = IndexedColors.TEAL.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val headerFont = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 11
                fontName = "Tahoma"
                color = IndexedColors.WHITE.index
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = IndexedColors.DARK_TEAL.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                borderTop = BorderStyle.MEDIUM
                borderBottom = BorderStyle.MEDIUM
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val dataFont = workbook.createFont().apply {
                fontHeightInPoints = 10
                fontName = "Tahoma"
            }

            val dataStyle = workbook.createCellStyle().apply {
                setFont(dataFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val stripeStyle = workbook.createCellStyle().apply {
                setFont(dataFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val dateStyle = workbook.createCellStyle().apply {
                setFont(dataFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                dataFormat = dataFormatter.getFormat("yyyy/mm/dd")
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val currencyStyle = workbook.createCellStyle().apply {
                setFont(dataFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                dataFormat = dataFormatter.getFormat("#,##0")
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val currencyStripeStyle = workbook.createCellStyle().apply {
                setFont(dataFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                dataFormat = dataFormatter.getFormat("#,##0")
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val percentageStyle = workbook.createCellStyle().apply {
                setFont(dataFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                dataFormat = dataFormatter.getFormat("0.00%")
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            val totalsFont = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 11
                fontName = "Tahoma"
            }
            val totalsLabelStyle = workbook.createCellStyle().apply {
                setFont(totalsFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.DOUBLE
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }
            val totalsValueStyle = workbook.createCellStyle().apply {
                setFont(totalsFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                dataFormat = dataFormatter.getFormat("#,##0")
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.DOUBLE
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }

            // HELPER: Sheet Initialization
            fun createCustomSheet(sheetName: String, titleText: String, headers: List<String>): Sheet {
                val sheet = workbook.createSheet(sheetName)
                sheet.setRightToLeft(true)

                // Title row
                val titleRow = sheet.createRow(0)
                titleRow.heightInPoints = 38f
                val titleCell = titleRow.createCell(0)
                titleCell.setCellValue(titleText)
                titleCell.cellStyle = titleStyle

                if (headers.isNotEmpty()) {
                    sheet.addMergedRegion(CellRangeAddress(0, 0, 0, headers.size - 1))
                }

                sheet.createRow(1).heightInPoints = 12f // Empty divider row

                // Header row
                val headerRow = sheet.createRow(2)
                headerRow.heightInPoints = 30f
                headers.forEachIndexed { i, text ->
                    val cell = headerRow.createCell(i)
                    cell.setCellValue(text)
                    cell.cellStyle = headerStyle
                }

                sheet.createFreezePane(0, 3) // Freeze title and headers
                return sheet
            }

            // HELPER: Sheet Finalization (Autofit columns and filters safely)
            fun finalizeCustomSheet(sheet: Sheet, headersSize: Int, lastRowIndex: Int) {
                if (lastRowIndex >= 2 && headersSize > 0) {
                    try {
                        sheet.setAutoFilter(CellRangeAddress(2, lastRowIndex, 0, headersSize - 1))
                    } catch (e: Exception) {
                        Log.e("ExcelExporter", "Failed to set auto filter on ${sheet.sheetName}", e)
                    }
                }
                for (i in 0 until headersSize) {
                    try {
                        // Android-safe column width setting without java.awt FontRenderContext dependency
                        sheet.setColumnWidth(i, 5500)
                    } catch (e: Exception) {
                        sheet.setColumnWidth(i, 4800)
                    }
                }
            }

            // ==========================================
            // 1. Sheet: اطلاعات مرکز (Company Profile)
            // ==========================================
            executeSheetGeneration(context, "اطلاعات مرکز", "Populating Company Profile") {
                val infoSheet = createCustomSheet(
                    "اطلاعات مرکز",
                    "مشخصات، وضعیت تنظیمات و پیکربندی سیستم - همراهان سلامت",
                    listOf("کلید تنظیمات", "مقدار تنظیمات", "توضیحات عملکردی")
                )
                val settingsDesc = mapOf(
                    "company_name" to "نام مرکز درمانی فعال",
                    "company_id" to "شناسه هویتی مرکز در سرور ابری",
                    "company_sync_code" to "کد احراز هویت همگام‌سازی",
                    "company_is_setup" to "وضعیت فعال‌سازی و راه‌اندازی مرکز",
                    "active_device_id" to "شناسه سخت‌افزاری دستگاه فعال",
                    "active_device_name" to "نام دستگاه همراه ثبت‌شده",
                    "active_device_status" to "وضعیت اتصال دستگاه همراه"
                )
                val infoRows = snapshot.systemSettings.map { setting ->
                    Triple(setting.key, setting.value, settingsDesc[setting.key] ?: "تنظیمات سیستمی عمومی")
                }
                infoRows.forEachIndexed { index, item ->
                    val row = infoSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    row.createSafeCell(0, item.first, style)
                    row.createSafeCell(1, item.second, style)
                    row.createSafeCell(2, item.third, style)
                }
                finalizeCustomSheet(infoSheet, 3, infoRows.size + 2)
            }


            // ==========================================
            // 2. Sheet: پرسنل (Personnel)
            // ==========================================
            executeSheetGeneration(context, "پرسنل", "Populating Personnel Rows and Totals") {
                val empSheet = createCustomSheet(
                    "پرسنل",
                    "مشخصات عمومی، نوع استخدام، مدل و وضعیت پورسانت همکاران - همراهان سلامت",
                    listOf(
                        "شناسه همکار", "نام کامل پرسنل", "کد ملی", "شماره تماس", "تخصص/حرفه", 
                        "سمت سازمانی", "نوع استخدام", "وضعیت فعالیت", "مدل محاسبه کارمزد", 
                        "مقدار پایه پورسانت", "کل پورسانت مکتسبه", "کل تسویه‌شده (تومان)", 
                        "پورسانت معوقه (طلبکار)", "کل پرداختی‌های دریافتی", "اطلاعات حساب بانکی", "شماره شبا (IBAN)"
                    )
                )
                snapshot.personnel.forEachIndexed { index, emp ->
                    val row = empSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle

                    val ibanVal = if (emp.bankInfo.contains("IR")) {
                        val irIdx = emp.bankInfo.indexOf("IR")
                        emp.bankInfo.substring(irIdx).takeWhile { it.isLetterOrDigit() }
                    } else if (emp.bankInfo.isNotBlank()) {
                        emp.bankInfo
                    } else "-"

                    row.createSafeCell(0, emp.id, style)
                    row.createSafeCell(1, emp.fullName, style)
                    row.createSafeCell(2, emp.nationalId, style)
                    row.createSafeCell(3, emp.phone, style)
                    row.createSafeCell(4, emp.profession, style)
                    row.createSafeCell(5, emp.position, style)
                    row.createSafeCell(6, emp.employmentType, style)
                    row.createSafeCell(7, emp.status, style)
                    row.createSafeCell(8, emp.commissionModel, style)
                    row.createSafeCell(9, emp.commissionValue, if (emp.commissionModel == "درصدی") style else curStyle)
                    row.createSafeCell(10, emp.totalSettledCommissions + emp.totalPendingCommissions, curStyle)
                    row.createSafeCell(11, emp.totalSettledCommissions, curStyle)
                    row.createSafeCell(12, emp.totalPendingCommissions, curStyle)
                    row.createSafeCell(13, emp.totalPaymentsReceived, curStyle)
                    row.createSafeCell(14, emp.bankInfo, style)
                    row.createSafeCell(15, ibanVal, style)
                }
                val empTotalsRowIndex = snapshot.personnel.size + 3
                val empTotalsRow = empSheet.createRow(empTotalsRowIndex)
                empTotalsRow.heightInPoints = 24f
                empTotalsRow.createSafeCell(0, "جمع کل:", totalsLabelStyle)
                for (col in 1..9) empTotalsRow.createSafeCell(col, "", totalsLabelStyle)
                empTotalsRow.createSafeCell(10, snapshot.personnel.sumOf { it.totalSettledCommissions + it.totalPendingCommissions }, totalsValueStyle)
                empTotalsRow.createSafeCell(11, snapshot.personnel.sumOf { it.totalSettledCommissions }, totalsValueStyle)
                empTotalsRow.createSafeCell(12, snapshot.personnel.sumOf { it.totalPendingCommissions }, totalsValueStyle)
                empTotalsRow.createSafeCell(13, snapshot.personnel.sumOf { it.totalPaymentsReceived }, totalsValueStyle)
                empTotalsRow.createSafeCell(14, "", totalsLabelStyle)
                empTotalsRow.createSafeCell(15, "", totalsLabelStyle)

                finalizeCustomSheet(empSheet, 16, empTotalsRowIndex)
            }


            // Create lookup maps to optimize performance (O(N) instead of O(N^2))
            val employeesMap = snapshot.personnel.associateBy { it.id }
            val patientsMap = snapshot.patients.associateBy { it.id }
            val servicesMap = snapshot.services.associateBy { it.id }

            // ==========================================
            // 3. Sheet: بیماران (Patients)
            // ==========================================
            executeSheetGeneration(context, "بیماران", "Populating Patients Rows and Totals") {
                val patSheet = createCustomSheet(
                    "بیماران",
                    "دفتر ثبت مشخصات دموگرافیک، سوابق و وضعیت مالی بیماران - همراهان سلامت",
                    listOf(
                        "شناسه", "نام کامل بیمار", "جنسیت", "سن (سال)", "شماره تماس", 
                        "آدرس محل سکونت", "منبع ارجاع", "شناسه معرف / کد ارجاع", "وضعیت پرونده", "کل مبالغ فاکتورها (تومان)", 
                        "مبالغ پرداخت‌شده (تومان)", "باقیمانده بدهی (تومان)", "تعداد دفعات دریافت خدمت", 
                        "تاریخ ثبت‌نام در مرکز", "توضیحات و یادداشت‌های بالینی"
                    )
                )
                Log.i("EXPORT_DEBUG", "LOG: Sheet Patients created")
                System.out.println("EXPORT_DEBUG LOG: Sheet Patients created")
                
                snapshot.patients.forEachIndexed { index, p ->
                    val row = patSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle

                    val refId = snapshot.referralCommissions.find { it.patientId == p.id }?.referralId
                        ?: snapshot.referrals.find { it.fullName == p.referralSource }?.id
                        ?: "-"

                    row.createSafeCell(0, p.id, style)
                    row.createSafeCell(1, p.fullName, style)
                    row.createSafeCell(2, p.gender, style)
                    row.createSafeCell(3, p.age, style)
                    row.createSafeCell(4, p.phone, style)
                    row.createSafeCell(5, p.address, style)
                    row.createSafeCell(6, p.referralSource, style)
                    row.createSafeCell(7, refId, style)
                    row.createSafeCell(8, p.status, style)
                    row.createSafeCell(9, p.totalInvoiced, curStyle)
                    row.createSafeCell(10, p.totalPaid, curStyle)
                    row.createSafeCell(11, p.remainingBalance, curStyle)
                    row.createSafeCell(12, p.servicesCount, style)
                    row.createSafeCell(13, p.registrationDate.formatDate(), dateStyle)
                    row.createSafeCell(14, p.notes, style)
                }
                Log.i("EXPORT_DEBUG", "LOG: Rows written = ${snapshot.patients.size}")
                System.out.println("EXPORT_DEBUG LOG: Rows written = ${snapshot.patients.size}")
                val patTotalsRowIndex = snapshot.patients.size + 3
                val patTotalsRow = patSheet.createRow(patTotalsRowIndex)
                patTotalsRow.heightInPoints = 24f
                patTotalsRow.createSafeCell(0, "جمع کل:", totalsLabelStyle)
                for (col in 1..8) patTotalsRow.createSafeCell(col, "", totalsLabelStyle)
                patTotalsRow.createSafeCell(9, snapshot.patients.sumOf { it.totalInvoiced }, totalsValueStyle)
                patTotalsRow.createSafeCell(10, snapshot.patients.sumOf { it.totalPaid }, totalsValueStyle)
                patTotalsRow.createSafeCell(11, snapshot.patients.sumOf { it.remainingBalance }, totalsValueStyle)
                patTotalsRow.createSafeCell(12, snapshot.patients.sumOf { it.servicesCount }, totalsValueStyle)
                patTotalsRow.createSafeCell(13, "", totalsLabelStyle)
                patTotalsRow.createSafeCell(14, "", totalsLabelStyle)
                finalizeCustomSheet(patSheet, 15, patTotalsRowIndex)
            }


            // ==========================================
            // 4. Sheet: خدمات (Services)
            // ==========================================
            executeSheetGeneration(context, "خدمات", "Populating Services Catalog") {
                val srvSheet = createCustomSheet(
                    "خدمات",
                    "کاتالوگ خدمات درمانی مرکز، تعرفه‌نامه‌ها و پورسانت‌های همکار - همراهان سلامت",
                    listOf(
                        "شناسه خدمت", "نام نمایشی خدمت", "دسته‌بندی/گروه", "قیمت فروش مرکز (تومان)", 
                        "دستمزد پیش‌فرض همکار", "مدت زمان تقریبی (دقیقه)", "دفعات انجام موفق", 
                        "دفعات برنامه‌ریزی‌شده", "دفعات لغو شده", "وضعیت پذیرش خدمت"
                    )
                )
                snapshot.services.forEachIndexed { index, s ->
                    val row = srvSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle

                    row.createSafeCell(0, s.id, style)
                    row.createSafeCell(1, s.name, style)
                    row.createSafeCell(2, s.category, style)
                    row.createSafeCell(3, s.sellingPrice, curStyle)
                    row.createSafeCell(4, s.defaultCost, curStyle)
                    row.createSafeCell(5, s.durationMinutes, style)
                    row.createSafeCell(6, s.timesCompleted, style)
                    row.createSafeCell(7, s.timesScheduled, style)
                    row.createSafeCell(8, s.timesCancelled, style)
                    row.createSafeCell(9, s.status, style)
                }
                finalizeCustomSheet(srvSheet, 10, snapshot.services.size + 2)
            }


            // ==========================================
            // 5. Sheet: ثبت خدمات (Service Registrations)
            // ==========================================
            executeSheetGeneration(context, "ثبت خدمات", "Populating Service Registrations") {
                val regSheet = createCustomSheet(
                    "ثبت خدمات",
                    "دفتر جامع خدمات ارائه‌شده به بیماران توسط کادر درمان - همراهان سلامت",
                    listOf(
                        "شناسه فاکتور", "نام بیمار", "نام کادر درمان", "نام خدمت ارائه‌شده", "تاریخ انجام خدمت (Service Date)",
                        "مبلغ توافقی بیمار", "دستمزد مصوب همکار", "هزینه ایاب‌ذهاب", "سایر هزینه‌ها", "مبلغ تخفیف", 
                        "مبلغ نهایی فاکتور", "روش پرداخت فاکتور", "شماره فاکتور", "یادداشت‌ها و جزئیات", "وضعیت تسویه فاکتور", "وضعیت فرآیند (Workflow)"
                    )
                )
                snapshot.serviceRegistrations.forEachIndexed { index, reg ->
                    val row = regSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle

                    val pName = patientsMap[reg.patientId]?.fullName ?: "بیمار شناسه ${reg.patientId}"
                    val eName = employeesMap[reg.employeeId]?.fullName ?: "کادر درمان شناسه ${reg.employeeId}"
                    val sName = servicesMap[reg.serviceId]?.name ?: "خدمت شناسه ${reg.serviceId}"

                    row.createSafeCell(0, reg.id, style)
                    row.createSafeCell(1, pName, style)
                    row.createSafeCell(2, eName, style)
                    row.createSafeCell(3, sName, style)
                    row.createSafeCell(4, reg.dateTime.formatDateTime(), dateStyle)
                    row.createSafeCell(5, reg.sellingPrice, curStyle)
                    row.createSafeCell(6, reg.employeeCost, curStyle)
                    row.createSafeCell(7, reg.transportationCost, curStyle)
                    row.createSafeCell(8, reg.otherCosts, curStyle)
                    row.createSafeCell(9, reg.discount, curStyle)
                    row.createSafeCell(10, reg.finalPrice, curStyle)
                    row.createSafeCell(11, reg.paymentMethod, style)
                    row.createSafeCell(12, reg.invoiceNumber, style)
                    row.createSafeCell(13, reg.notes, style)
                    row.createSafeCell(14, if (reg.isPaid) "تسویه شده" else "در انتظار پرداخت", style)
                    row.createSafeCell(15, reg.workflowStatus, style)
                }
                val regTotalsRowIndex = snapshot.serviceRegistrations.size + 3
                val regTotalsRow = regSheet.createRow(regTotalsRowIndex)
                regTotalsRow.heightInPoints = 24f
                regTotalsRow.createSafeCell(0, "جمع کل فاکتورها:", totalsLabelStyle)
                for (col in 1..4) regTotalsRow.createSafeCell(col, "", totalsLabelStyle)
                regTotalsRow.createSafeCell(5, snapshot.serviceRegistrations.sumOf { it.sellingPrice }, totalsValueStyle)
                regTotalsRow.createSafeCell(6, snapshot.serviceRegistrations.sumOf { it.employeeCost }, totalsValueStyle)
                regTotalsRow.createSafeCell(7, snapshot.serviceRegistrations.sumOf { it.transportationCost }, totalsValueStyle)
                regTotalsRow.createSafeCell(8, snapshot.serviceRegistrations.sumOf { it.otherCosts }, totalsValueStyle)
                regTotalsRow.createSafeCell(9, snapshot.serviceRegistrations.sumOf { it.discount }, totalsValueStyle)
                regTotalsRow.createSafeCell(10, snapshot.serviceRegistrations.sumOf { it.finalPrice }, totalsValueStyle)
                for (col in 11..15) regTotalsRow.createSafeCell(col, "", totalsLabelStyle)
                finalizeCustomSheet(regSheet, 16, regTotalsRowIndex)
            }


            // ==========================================
            // 6. Sheet: مالی و تراکنشها (Financial Summary, Cashboxes, Transactions)
            // ==========================================
            executeSheetGeneration(context, "مالی و تراکنشها", "Populating Financial Summary, Cashboxes, and Transactions") {
                val finSheet = workbook.createSheet("مالی و تراکنشها")
                finSheet.setRightToLeft(true)

                // Main Title
                val titleRow = finSheet.createRow(0)
                titleRow.heightInPoints = 38f
                titleRow.createSafeCell(0, "دفتر کل حسابداری مالی، صندوق‌ها و تراکنش‌های جاری - همراهان سلامت", titleStyle)
                finSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 8))

                // SECTION 1: FINANCIAL SUMMARY KPIs
                val section1Row = finSheet.createRow(2)
                section1Row.heightInPoints = 26f
                section1Row.createSafeCell(0, "۱. خلاصه وضعیت شاخص‌های کلیدی عملکرد مالی (KPIs)", headerStyle)
                finSheet.addMergedRegion(CellRangeAddress(2, 2, 0, 8))

                val kpiHeaderRow = finSheet.createRow(3)
                kpiHeaderRow.heightInPoints = 24f
                listOf("عنوان شاخص مالی", "مقدار مکتسبه (تومان)", "توضیحات تکمیلی عملکرد").forEachIndexed { i, h ->
                    kpiHeaderRow.createSafeCell(i, h, headerStyle)
                }
                finSheet.addMergedRegion(CellRangeAddress(3, 3, 2, 8))

                val kpiList = listOf(
                    Triple("درآمد کل مرکز", snapshot.financialSummary.totalIncome, "مجموع کلیه دریافتی‌ها و درآمدهای ثبت‌شده در سیستم"),
                    Triple("هزینه‌های کل", snapshot.financialSummary.totalExpenses, "مجموع هزینه‌های جاری، حقوق پرسنل و مصارف اداری"),
                    Triple("سود خالص شرکت", snapshot.financialSummary.netProfit, "تفاضل درآمد کل و هزینه‌های کل (سود عملیاتی مرکز)"),
                    Triple("مطالبات پرداخت‌نشده (در انتظار دریافت)", snapshot.financialSummary.totalReceivables, "کل مبالغ فاکتورهایی که هنوز توسط بیماران تسویه نشده‌اند"),
                    Triple("بدهی‌های معوق همکاران (کارمزدها)", snapshot.financialSummary.totalPayables, "مجموع کارمزدهای کادر درمان که در انتظار تسویه هستند")
                )
                kpiList.forEachIndexed { i, kpi ->
                    val r = finSheet.createRow(i + 4)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (i % 2 == 1) currencyStripeStyle else currencyStyle
                    r.createSafeCell(0, kpi.first, style)
                    r.createSafeCell(1, kpi.second, curStyle)
                    r.createSafeCell(2, kpi.third, style)
                    finSheet.addMergedRegion(CellRangeAddress(i + 4, i + 4, 2, 8))
                }

                // SECTION 2: CASHBOXES
                val startRowCashbox = 10
                val section2Row = finSheet.createRow(startRowCashbox)
                section2Row.heightInPoints = 26f
                section2Row.createSafeCell(0, "۲. مانده حساب صندوق‌ها و حساب‌های بانکی جاری مرکز", headerStyle)
                finSheet.addMergedRegion(CellRangeAddress(startRowCashbox, startRowCashbox, 0, 8))

                val cbHeaderRow = finSheet.createRow(startRowCashbox + 1)
                cbHeaderRow.heightInPoints = 24f
                val cbHeaders = listOf("شناسه صندوق", "نام صندوق / حساب", "نوع حساب", "شماره کارت / حساب", "موجودی فعلی (تومان)")
                cbHeaders.forEachIndexed { i, h ->
                    cbHeaderRow.createSafeCell(i, h, headerStyle)
                }
                finSheet.addMergedRegion(CellRangeAddress(startRowCashbox + 1, startRowCashbox + 1, 4, 8))

                snapshot.cashboxes.forEachIndexed { i, cb ->
                    val r = finSheet.createRow(startRowCashbox + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (i % 2 == 1) currencyStripeStyle else currencyStyle
                    r.createSafeCell(0, cb.id, style)
                    r.createSafeCell(1, cb.name, style)
                    r.createSafeCell(2, cb.type, style)
                    r.createSafeCell(3, cb.accountNumber, style)
                    r.createSafeCell(4, cb.balance, curStyle)
                    finSheet.addMergedRegion(CellRangeAddress(startRowCashbox + 2 + i, startRowCashbox + 2 + i, 4, 8))
                }
                val cbTotalIdx = startRowCashbox + 2 + snapshot.cashboxes.size
                val cbTotalRow = finSheet.createRow(cbTotalIdx)
                cbTotalRow.heightInPoints = 24f
                cbTotalRow.createSafeCell(0, "مجموع کل نقدینگی:", totalsLabelStyle)
                for (c in 1..3) cbTotalRow.createSafeCell(c, "", totalsLabelStyle)
                cbTotalRow.createSafeCell(4, snapshot.cashboxes.sumOf { it.balance }, totalsValueStyle)
                finSheet.addMergedRegion(CellRangeAddress(cbTotalIdx, cbTotalIdx, 4, 8))

                // SECTION 3: ALL TRANSACTIONS
                val startRowTx = cbTotalIdx + 2
                val section3Row = finSheet.createRow(startRowTx)
                section3Row.heightInPoints = 26f
                section3Row.createSafeCell(0, "۳. ریز تراکنش‌های حسابداری دفتر معین نقدینگی", headerStyle)
                finSheet.addMergedRegion(CellRangeAddress(startRowTx, startRowTx, 0, 8))

                val txHeaderRow = finSheet.createRow(startRowTx + 1)
                txHeaderRow.heightInPoints = 24f
                val txHeaders = listOf("شناسه تراکنش", "نوع تراکنش", "دسته‌بندی", "مبلغ تراکنش (تومان)", "تاریخ تراکنش", "توضیحات و شرح آرتیکل", "روش پرداخت", "شماره فاکتور / مرجع", "شناسه سند روزنامه حسابداری")
                txHeaders.forEachIndexed { i, h ->
                    txHeaderRow.createSafeCell(i, h, headerStyle)
                }

                snapshot.financialTransactions.forEachIndexed { i, tx ->
                    val r = finSheet.createRow(startRowTx + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (i % 2 == 1) currencyStripeStyle else currencyStyle

                    val invoiceRefNo = snapshot.serviceRegistrations.find { it.id == tx.referenceId }?.invoiceNumber?.ifBlank { "INV-${tx.referenceId}" }
                        ?: (tx.referenceId?.let { "REF-$it" } ?: "-")
                    val journalEntryId = "JE-${tx.id}"

                    r.createSafeCell(0, tx.id, style)
                    r.createSafeCell(1, tx.type, style)
                    r.createSafeCell(2, tx.category, style)
                    r.createSafeCell(3, tx.amount, curStyle)
                    r.createSafeCell(4, tx.date.formatDateTime(), dateStyle)
                    r.createSafeCell(5, tx.description, style)
                    r.createSafeCell(6, tx.paymentMethod, style)
                    r.createSafeCell(7, invoiceRefNo, style)
                    r.createSafeCell(8, journalEntryId, style)
                }
                val txTotalIdx = startRowTx + 2 + snapshot.financialTransactions.size
                val txTotalRow = finSheet.createRow(txTotalIdx)
                txTotalRow.heightInPoints = 24f
                txTotalRow.createSafeCell(0, "تراز سود عملیاتی (درآمد - هزینه):", totalsLabelStyle)
                for (c in 1..2) txTotalRow.createSafeCell(c, "", totalsLabelStyle)
                val netSum = snapshot.financialTransactions.filter { it.type == "درآمد" }.sumOf { it.amount } - 
                             snapshot.financialTransactions.filter { it.type == "هزینه" }.sumOf { it.amount }
                txTotalRow.createSafeCell(3, netSum, totalsValueStyle)
                for (c in 4..8) txTotalRow.createSafeCell(c, "", totalsLabelStyle)

                finSheet.createFreezePane(0, 1)
                finalizeCustomSheet(finSheet, 9, txTotalIdx)
            }


            // ==========================================
            // 7. Sheet: هزینهها (Expenses)
            // ==========================================
            executeSheetGeneration(context, "هزینهها", "Populating Expenses Log") {
                val expSheet = createCustomSheet(
                    "هزینهها",
                    "دفتر تفصیلی هزینه‌های جاری، اداری، متغیر و عملیاتی مرکز - همراهان سلامت",
                    listOf("شناسه", "عنوان هزینه", "دسته‌بندی موضوعی", "مبلغ هزینه (تومان)", "تاریخ ثبت", "تاریخ واقعی پرداخت", "روش پرداخت", "شخص ثبت‌کننده", "شرح و توضیحات", "وضعیت پرداخت")
                )
                snapshot.expenses.forEachIndexed { index, exp ->
                    val row = expSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle

                    row.createSafeCell(0, exp.id, style)
                    row.createSafeCell(1, exp.title, style)
                    row.createSafeCell(2, exp.category, style)
                    row.createSafeCell(3, exp.amount, curStyle)
                    row.createSafeCell(4, exp.registrationDate.formatDateTime(), dateStyle)
                    row.createSafeCell(5, exp.paymentDate.formatDateTime(), dateStyle)
                    row.createSafeCell(6, exp.paymentMethod, style)
                    row.createSafeCell(7, exp.submitterName, style)
                    row.createSafeCell(8, exp.description, style)
                    row.createSafeCell(9, exp.status, style)
                }
                val expTotalsRowIndex = snapshot.expenses.size + 3
                val expTotalsRow = expSheet.createRow(expTotalsRowIndex)
                expTotalsRow.heightInPoints = 24f
                expTotalsRow.createSafeCell(0, "جمع کل هزینه‌ها:", totalsLabelStyle)
                for (col in 1..2) expTotalsRow.createSafeCell(col, "", totalsLabelStyle)
                expTotalsRow.createSafeCell(3, snapshot.expenses.sumOf { it.amount }, totalsValueStyle)
                for (col in 4..9) expTotalsRow.createSafeCell(col, "", totalsLabelStyle)

                finalizeCustomSheet(expSheet, 10, expTotalsRowIndex)
            }


            // ==========================================
            // 8. Sheet: معرفین (Referrals)
            // ==========================================
            executeSheetGeneration(context, "معرفین", "Populating Referrals List") {
                val refSheet = createCustomSheet(
                    "معرفین",
                    "دفتر ثبت پزشکان و کادر درمانی ارجاع‌دهنده بیمار - همراهان سلامت",
                    listOf("شناسه معرف", "نام و نام خانوادگی", "شماره تلفن همراه", "تخصص / زمینه کاری", "توضیحات و یادداشت‌ها")
                )
                snapshot.referrals.forEachIndexed { index, ref ->
                    val row = refSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle

                    row.createSafeCell(0, ref.id, style)
                    row.createSafeCell(1, ref.fullName, style)
                    row.createSafeCell(2, ref.phone, style)
                    row.createSafeCell(3, ref.specialty, style)
                    row.createSafeCell(4, ref.notes, style)
                }
                finalizeCustomSheet(refSheet, 5, snapshot.referrals.size + 2)
            }


            // ==========================================
            // 9. Sheet: کمیسیونها (Commission Settlements & Referrals)
            // ==========================================
            executeSheetGeneration(context, "کمیسیونها", "Populating Commission Settlements and Referral Commissions") {
                val comSheet = workbook.createSheet("کمیسیونها")
                comSheet.setRightToLeft(true)

                val comTitleRow = comSheet.createRow(0)
                comTitleRow.heightInPoints = 38f
                comTitleRow.createSafeCell(0, "دفتر کل ممیزی کارمزد همکاران کادر درمان و پزشکان معرف - همراهان سلامت", titleStyle)
                comSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 12))

                // SECTION 1: COMMISSION SETTLEMENTS
                val comSec1Row = comSheet.createRow(2)
                comSec1Row.heightInPoints = 26f
                comSec1Row.createSafeCell(0, "۱. تسویه حساب پورسانت و کارمزدهای پرداختی همکاران کادر درمان", headerStyle)
                comSheet.addMergedRegion(CellRangeAddress(2, 2, 0, 12))

                val setHeaderRow = comSheet.createRow(3)
                setHeaderRow.heightInPoints = 24f
                val setHeaders = listOf("شناسه تسویه", "نام همکار کادر درمان", "مدل پورسانت همکار", "نرخ/مقدار پایه پورسانت", "مبلغ تسویه (تومان)", "تاریخ تسویه حساب", "شروع دوره محاسباتی", "پایان دوره محاسباتی", "تعداد روزهای دوره", "توضیحات و یادداشت‌ها")
                setHeaders.forEachIndexed { i, h ->
                    setHeaderRow.createSafeCell(i, h, headerStyle)
                }
                comSheet.addMergedRegion(CellRangeAddress(3, 3, 9, 12))

                snapshot.commissionSettlements.forEachIndexed { i, cs ->
                    val r = comSheet.createRow(i + 4)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (i % 2 == 1) currencyStripeStyle else currencyStyle
                    val emp = employeesMap[cs.employeeId]
                    val empName = emp?.fullName ?: "همکار شناسه ${cs.employeeId}"
                    val empModel = emp?.commissionModel ?: "ثابت"
                    val empValue = emp?.commissionValue ?: 0.0
                    val periodDays = ((cs.periodEnd - cs.periodStart) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)

                    r.createSafeCell(0, cs.id, style)
                    r.createSafeCell(1, empName, style)
                    r.createSafeCell(2, empModel, style)
                    r.createSafeCell(3, empValue, if (empModel == "درصدی") style else curStyle)
                    r.createSafeCell(4, cs.amount, curStyle)
                    r.createSafeCell(5, cs.settlementDate.formatDateTime(), dateStyle)
                    r.createSafeCell(6, cs.periodStart.formatDate(), dateStyle)
                    r.createSafeCell(7, cs.periodEnd.formatDate(), dateStyle)
                    r.createSafeCell(8, "$periodDays روز", style)
                    r.createSafeCell(9, cs.notes, style)
                    comSheet.addMergedRegion(CellRangeAddress(i + 4, i + 4, 9, 12))
                }
                val setTotalIdx = snapshot.commissionSettlements.size + 4
                val setTotalRow = comSheet.createRow(setTotalIdx)
                setTotalRow.heightInPoints = 24f
                setTotalRow.createSafeCell(0, "مجموع تسویه‌های کادر درمان:", totalsLabelStyle)
                for (col in 1..3) setTotalRow.createSafeCell(col, "", totalsLabelStyle)
                setTotalRow.createSafeCell(4, snapshot.commissionSettlements.sumOf { it.amount }, totalsValueStyle)
                for (col in 5..12) setTotalRow.createSafeCell(col, "", totalsLabelStyle)
                comSheet.addMergedRegion(CellRangeAddress(setTotalIdx, setTotalIdx, 9, 12))

                // SECTION 2: REFERRAL COMMISSIONS
                val startRowRc = setTotalIdx + 2
                val comSec2Row = comSheet.createRow(startRowRc)
                comSec2Row.heightInPoints = 26f
                comSec2Row.createSafeCell(0, "۲. ممیزی پورسانت‌های پزشکان و اشخاص ارجاع‌دهنده (معرفین)", headerStyle)
                comSheet.addMergedRegion(CellRangeAddress(startRowRc, startRowRc, 0, 12))

                val rcHeaderRow = comSheet.createRow(startRowRc + 1)
                rcHeaderRow.heightInPoints = 24f
                val rcHeaders = listOf(
                    "شناسه پورسانت", "نام معرف (ID)", "نام بیمار (ID)", "شناسه فاکتور", 
                    "خدمت انجام‌شده", "مبلغ کل خدمت", "درصد پورسانت", "مبلغ پورسانت (تومان)", 
                    "تاریخ ثبت", "وضعیت پرداخت پورسانت", "تاریخ تسویه", "شماره سند پرداخت", "توضیحات"
                )
                rcHeaders.forEachIndexed { i, h ->
                    rcHeaderRow.createSafeCell(i, h, headerStyle)
                }

                snapshot.referralCommissions.forEachIndexed { i, rc ->
                    val r = comSheet.createRow(startRowRc + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (i % 2 == 1) currencyStripeStyle else currencyStyle

                    r.createSafeCell(0, rc.id, style)
                    r.createSafeCell(1, "معرف ID: ${rc.referralId}", style)
                    r.createSafeCell(2, "بیمار ID: ${rc.patientId}", style)
                    r.createSafeCell(3, rc.serviceRegistrationId, style)
                    r.createSafeCell(4, rc.serviceName, style)
                    r.createSafeCell(5, rc.serviceAmount, curStyle)
                    r.createSafeCell(6, rc.commissionPercentage, percentageStyle)
                    r.createSafeCell(7, rc.commissionAmount, curStyle)
                    r.createSafeCell(8, rc.date.formatDateTime(), dateStyle)
                    r.createSafeCell(9, rc.status, style)
                    r.createSafeCell(10, rc.paymentDate?.formatDateTime() ?: "-", dateStyle)
                    r.createSafeCell(11, rc.documentNumber, style)
                    r.createSafeCell(12, rc.notes, style)
                }
                val rcTotalIdx = startRowRc + 2 + snapshot.referralCommissions.size
                val rcTotalRow = comSheet.createRow(rcTotalIdx)
                rcTotalRow.heightInPoints = 24f
                rcTotalRow.createSafeCell(0, "مجموع پورسانت معرفین:", totalsLabelStyle)
                for (col in 1..4) rcTotalRow.createSafeCell(col, "", totalsLabelStyle)
                rcTotalRow.createSafeCell(5, snapshot.referralCommissions.sumOf { it.serviceAmount }, totalsValueStyle)
                rcTotalRow.createSafeCell(6, "", totalsLabelStyle)
                rcTotalRow.createSafeCell(7, snapshot.referralCommissions.sumOf { it.commissionAmount }, totalsValueStyle)
                for (col in 8..12) rcTotalRow.createSafeCell(col, "", totalsLabelStyle)

                comSheet.createFreezePane(0, 1)
                finalizeCustomSheet(comSheet, 13, rcTotalIdx)
            }


            // ==========================================
            // 9. Sheet: قراردادها (Contracts)
            // ==========================================
            executeSheetGeneration(context, "قراردادها", "Populating Contracts Ledger") {
                val conSheet = createCustomSheet(
                    "قراردادها",
                    "دفتر ثبت قراردادهای رسمی و توافق‌نامه‌های کاری همکاران کادر درمان - همراهان سلامت",
                    listOf("شناسه قرارداد", "نام پرسنل / کادر درمان", "عنوان قرارداد کاری", "تاریخ شروع قرارداد", "تاریخ پایان قرارداد", "سهم بیمار", "سهم مرکز", "سهم پرسنل / کادر درمان", "شرایط پرداخت و تسویه", "وضعیت تایید پذیرش", "شرح توافقات و یادداشت‌های ممیزی")
                )
                snapshot.contracts.forEachIndexed { index, con ->
                    val row = conSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle

                    val emp = employeesMap[con.employeeId]
                    val personnelShare = emp?.let {
                        if (it.commissionModel == "درصدی") "${it.commissionValue}%" else "${it.commissionValue.toInt()} تومان (ثابت)"
                    } ?: "توافقی"
                    val centerShare = emp?.let {
                        if (it.commissionModel == "درصدی") "${(100.0 - it.commissionValue).coerceAtLeast(0.0)}%" else "تفاضل مابه‌التفاوت"
                    } ?: "توافقی"
                    val patientShare = "۱۰۰٪ (تعرفه مصوب)"
                    val paymentTerms = if (con.comment.isNotBlank()) con.comment else "تسویه دوره‌ای کارمزد بر اساس مدل ${emp?.commissionModel ?: "توافقی"}"

                    row.createSafeCell(0, con.id, style)
                    row.createSafeCell(1, con.employeeName, style)
                    row.createSafeCell(2, con.title, style)
                    row.createSafeCell(3, con.startDate.formatDate(), dateStyle)
                    row.createSafeCell(4, con.endDate.formatDate(), dateStyle)
                    row.createSafeCell(5, patientShare, style)
                    row.createSafeCell(6, centerShare, style)
                    row.createSafeCell(7, personnelShare, style)
                    row.createSafeCell(8, paymentTerms, style)
                    row.createSafeCell(9, con.status, style)
                    row.createSafeCell(10, con.comment, style)
                }
                finalizeCustomSheet(conSheet, 11, snapshot.contracts.size + 2)
            }


            // ==========================================
            // 10. Sheet: گزارشات بالینی (Clinical Reports)
            // ==========================================
            executeSheetGeneration(context, "گزارشات بالینی", "Populating Clinical Reports, Vitals, and Wound Records") {
                val clinicalSheet = workbook.createSheet("گزارشات بالینی")
                clinicalSheet.setRightToLeft(true)

                val clTitleRow = clinicalSheet.createRow(0)
                clTitleRow.heightInPoints = 38f
                clTitleRow.createSafeCell(0, "دفتر کل گزارشات بالینی، ثبت پرونده زخم و علائم حیاتی بیماران - همراهان سلامت", titleStyle)
                clinicalSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 7))

                // SECTION 1: NURSING REPORTS
                val clSec1Row = clinicalSheet.createRow(2)
                clSec1Row.heightInPoints = 26f
                clSec1Row.createSafeCell(0, "۱. گزارشات پرستاری دوره‌ای ثبت‌شده کادر درمان", headerStyle)
                clinicalSheet.addMergedRegion(CellRangeAddress(2, 2, 0, 7))

                val nrHeaderRow = clinicalSheet.createRow(3)
                nrHeaderRow.heightInPoints = 24f
                val nrHeaders = listOf("شناسه", "نام بیمار (ID)", "پرستار مسئول", "تاریخ و زمان گزارش", "متن گزارش بالینی")
                nrHeaders.forEachIndexed { i, h ->
                    nrHeaderRow.createSafeCell(i, h, headerStyle)
                }
                clinicalSheet.addMergedRegion(CellRangeAddress(3, 3, 4, 7))

                snapshot.nursingReports.forEachIndexed { i, nr ->
                    val r = clinicalSheet.createRow(i + 4)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val pName = patientsMap[snapshot.serviceRegistrations.find { it.id == nr.registrationId }?.patientId ?: 0]?.fullName ?: "بیمار ID: ${nr.registrationId}"

                    r.createSafeCell(0, nr.id, style)
                    r.createSafeCell(1, pName, style)
                    r.createSafeCell(2, nr.reporterName, style)
                    r.createSafeCell(3, nr.date.formatDateTime(), dateStyle)
                    r.createSafeCell(4, nr.description, style)
                    clinicalSheet.addMergedRegion(CellRangeAddress(i + 4, i + 4, 4, 7))
                }
                val nrEndIdx = snapshot.nursingReports.size + 4

                // SECTION 2: VITAL SIGNS
                val startRowVs = nrEndIdx + 2
                val clSec2Row = clinicalSheet.createRow(startRowVs)
                clSec2Row.heightInPoints = 26f
                clSec2Row.createSafeCell(0, "۲. جدول علائم حیاتی دوره‌ای ثبت‌نامی بیماران", headerStyle)
                clinicalSheet.addMergedRegion(CellRangeAddress(startRowVs, startRowVs, 0, 7))

                val vsHeaderRow = clinicalSheet.createRow(startRowVs + 1)
                vsHeaderRow.heightInPoints = 24f
                val vsHeaders = listOf("شناسه", "نام بیمار", "سیستولیک BP", "دیاستولیک BP", "ضربان قلب (در دقیقه)", "دمای بدن (C°)", "سطح اکسیژن (%)", "تاریخ ثبت")
                vsHeaders.forEachIndexed { i, h ->
                    vsHeaderRow.createSafeCell(i, h, headerStyle)
                }

                snapshot.vitalSigns.forEachIndexed { i, vs ->
                    val r = clinicalSheet.createRow(startRowVs + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val pName = patientsMap[vs.patientId]?.fullName ?: "بیمار شناسه ${vs.patientId}"

                    r.createSafeCell(0, vs.id, style)
                    r.createSafeCell(1, pName, style)
                    r.createSafeCell(2, vs.bloodPressureSystolic, style)
                    r.createSafeCell(3, vs.bloodPressureDiastolic, style)
                    r.createSafeCell(4, vs.heartRate, style)
                    r.createSafeCell(5, vs.temperatureCelsius, style)
                    r.createSafeCell(6, vs.oxygenSaturation, style)
                    r.createSafeCell(7, vs.date.formatDateTime(), dateStyle)
                }
                val vsEndIdx = startRowVs + 2 + snapshot.vitalSigns.size

                // SECTION 3: WOUND RECORDS
                val startRowWr = vsEndIdx + 2
                val clSec3Row = clinicalSheet.createRow(startRowWr)
                clSec3Row.heightInPoints = 26f
                clSec3Row.createSafeCell(0, "۳. پرونده‌های مدیریت و بهبود زخم بیماران", headerStyle)
                clinicalSheet.addMergedRegion(CellRangeAddress(startRowWr, startRowWr, 0, 7))

                val wrHeaderRow = clinicalSheet.createRow(startRowWr + 1)
                wrHeaderRow.heightInPoints = 24f
                val wrHeaders = listOf("شناسه ارزیابی", "نام بیمار", "نوع زخم ارزیابی شده", "گرید/شدت زخم", "شرح و جزئیات ارزیابی بالینی زخم", "تاریخ ثبت ارزیابی")
                wrHeaders.forEachIndexed { i, h ->
                    wrHeaderRow.createSafeCell(i, h, headerStyle)
                }
                clinicalSheet.addMergedRegion(CellRangeAddress(startRowWr + 1, startRowWr + 1, 4, 5))

                snapshot.woundRecords.forEachIndexed { i, wr ->
                    val r = clinicalSheet.createRow(startRowWr + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val pName = patientsMap[wr.patientId]?.fullName ?: "بیمار شناسه ${wr.patientId}"

                    r.createSafeCell(0, wr.id, style)
                    r.createSafeCell(1, pName, style)
                    r.createSafeCell(2, wr.woundType, style)
                    r.createSafeCell(3, wr.stage, style)
                    r.createSafeCell(4, wr.description, style)
                    r.createSafeCell(5, wr.date.formatDateTime(), dateStyle)
                    clinicalSheet.addMergedRegion(CellRangeAddress(startRowWr + 2 + i, startRowWr + 2 + i, 4, 5))
                }
                val wrEndIdx = startRowWr + 2 + snapshot.woundRecords.size

                clinicalSheet.createFreezePane(0, 1)
                finalizeCustomSheet(clinicalSheet, 8, wrEndIdx)
            }


            // ==========================================
            // 11. Sheet: تاریخچه تغییرات (Audit History)
            // ==========================================
            executeSheetGeneration(context, "تاریخچه تغییرات", "Populating System Audit Logs, Edit Histories, and Sync Metadata") {
                val auditSheet = workbook.createSheet("تاریخچه تغییرات")
                auditSheet.setRightToLeft(true)

                val auTitleRow = auditSheet.createRow(0)
                auTitleRow.heightInPoints = 38f
                auTitleRow.createSafeCell(0, "دفتر جامع لاگ‌های ممیزی امنیتی، مانیتورینگ سیستم و تغییرات اسناد - همراهان سلامت", titleStyle)
                auditSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 6))

                // SECTION 1: SYSTEM AUDIT LOGS
                val auSec1Row = auditSheet.createRow(2)
                auSec1Row.heightInPoints = 26f
                auSec1Row.createSafeCell(0, "۱. لاگ‌های امنیتی ممیزی تراکنش‌های سیستمی مرکز (System Audit Logs)", headerStyle)
                auditSheet.addMergedRegion(CellRangeAddress(2, 2, 0, 6))

                val alHeaderRow = auditSheet.createRow(3)
                alHeaderRow.heightInPoints = 24f
                val alHeaders = listOf("شناسه لاگ", "تاریخ و زمان رویداد", "کاربر مسئول", "نام دستگاه همراه", "نوع عملیات (Action)", "بخش تحت تاثیر", "شرح و جزئیات ممیزی")
                alHeaders.forEachIndexed { i, h ->
                    alHeaderRow.createSafeCell(i, h, headerStyle)
                }

                snapshot.auditLogs.forEachIndexed { i, al ->
                    val r = auditSheet.createRow(i + 4)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    r.createSafeCell(0, al.id, style)
                    r.createSafeCell(1, al.timestamp.formatDateTime(), dateStyle)
                    r.createSafeCell(2, al.user, style)
                    r.createSafeCell(3, al.device, style)
                    r.createSafeCell(4, al.action, style)
                    r.createSafeCell(5, al.affectedModule, style)
                    r.createSafeCell(6, al.details, style)
                }
                val alEndIdx = snapshot.auditLogs.size + 4

                // SECTION 2: FINANCIAL EDIT HISTORY
                val startRowFe = alEndIdx + 2
                val auSec2Row = auditSheet.createRow(startRowFe)
                auSec2Row.heightInPoints = 26f
                auSec2Row.createSafeCell(0, "۲. تاریخچه ویرایش و اصلاح اسناد مالی (Financial Edit History)", headerStyle)
                auditSheet.addMergedRegion(CellRangeAddress(startRowFe, startRowFe, 0, 9))

                val feHeaderRow = auditSheet.createRow(startRowFe + 1)
                feHeaderRow.heightInPoints = 24f
                val feHeaders = listOf("شناسه ویرایش", "نوع سند مالی", "شناسه سند", "مقدار قبلی", "مقدار جدید", "مبلغ اختلاف", "کاربر مسئول", "تاریخ ویرایش", "علت ویرایش", "توضیحات تکمیلی")
                feHeaderRow.forEachIndexed { i, h ->
                    feHeaderRow.createSafeCell(i, h, headerStyle)
                }

                snapshot.editHistories.forEachIndexed { i, fe ->
                    val r = auditSheet.createRow(startRowFe + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (i % 2 == 1) currencyStripeStyle else currencyStyle

                    r.createSafeCell(0, fe.id, style)
                    r.createSafeCell(1, fe.entityType, style)
                    r.createSafeCell(2, fe.entityId, style)
                    r.createSafeCell(3, fe.previousValue, style)
                    r.createSafeCell(4, fe.newValue, style)
                    r.createSafeCell(5, fe.differenceAmount, curStyle)
                    r.createSafeCell(6, fe.editedBy, style)
                    r.createSafeCell(7, fe.timestamp.formatDateTime(), dateStyle)
                    r.createSafeCell(8, fe.reason, style)
                    r.createSafeCell(9, fe.comment, style)
                }
                val feEndIdx = startRowFe + 2 + snapshot.editHistories.size

                // SECTION 3: SYNC METADATA
                val startRowSm = feEndIdx + 2
                val auSec3Row = auditSheet.createRow(startRowSm)
                auSec3Row.heightInPoints = 26f
                auSec3Row.createSafeCell(0, "۳. متادیتای همگام‌سازی ابری اسناد محلی (Cloud Sync Metadata)", headerStyle)
                auditSheet.addMergedRegion(CellRangeAddress(startRowSm, startRowSm, 0, 6))

                val smHeaderRow = auditSheet.createRow(startRowSm + 1)
                smHeaderRow.heightInPoints = 24f
                val smHeaders = listOf("نوع موجودیت", "شناسه محلی موجودیت", "آخرین بروزرسانی", "وضعیت حذف فیزیکی", "شناسه آخرین ویرایش‌کننده", "وضعیت همگام‌سازی ابری")
                smHeaders.forEachIndexed { i, h ->
                    smHeaderRow.createSafeCell(i, h, headerStyle)
                }

                snapshot.syncMetadata.forEachIndexed { i, sm ->
                    val r = auditSheet.createRow(startRowSm + 2 + i)
                    r.heightInPoints = 22f
                    val style = if (i % 2 == 1) stripeStyle else dataStyle

                    r.createSafeCell(0, sm.entityType, style)
                    r.createSafeCell(1, sm.entityId, style)
                    r.createSafeCell(2, sm.updatedTimestamp.formatDateTime(), dateStyle)
                    r.createSafeCell(3, if (sm.deletedStatus) "بله" else "خیر", style)
                    r.createSafeCell(4, sm.lastModifiedDeviceId, style)
                    r.createSafeCell(5, sm.syncStatus, style)
                }
                val smEndIdx = startRowSm + 2 + snapshot.syncMetadata.size

                auditSheet.createFreezePane(0, 1)
                finalizeCustomSheet(auditSheet, 10, smEndIdx)
            }


            // ==========================================
            // 12. Sheet: پروفایل تخصصی پرسنل (Staff Profiles)
            // ==========================================
            executeSheetGeneration(context, "پروفایل تخصصی پرسنل", "Populating Staff Profile Records") {
                val spSheet = createCustomSheet(
                    "پروفایل تخصصی پرسنل",
                    "دفتر ثبت پروفایل و سوابق تخصصی پرسنل و کادر درمان - همراهان سلامت",
                    listOf("شناسه پرسنل", "نام کامل پرسنل", "تخصص / رده شغلی", "شماره تماس اضطراری / همراه", "کارت ملی", "مدرک تحصیلی / گواهی", "پروانه / مجوز فعالیت", "قرارداد کاری", "وضعیت مدارک", "توضیحات و ملاحظات")
                )
                snapshot.staffProfiles.forEachIndexed { index, sp ->
                    val row = spSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle

                    row.createSafeCell(0, sp.employeeId, style)
                    row.createSafeCell(1, sp.employeeName, style)
                    row.createSafeCell(2, sp.profession, style)
                    row.createSafeCell(3, sp.phone, style)
                    row.createSafeCell(4, if (sp.hasNationalIdCard) "ارائه شده" else "ارائه نشده", style)
                    row.createSafeCell(5, if (sp.hasDegree) "ارائه شده" else "ارائه نشده", style)
                    row.createSafeCell(6, if (sp.hasLicense) "ارائه شده" else "ارائه نشده", style)
                    row.createSafeCell(7, if (sp.hasContract) "دارد" else "ندارد", style)
                    row.createSafeCell(8, sp.status, style)
                    row.createSafeCell(9, sp.comment, style)
                }
                finalizeCustomSheet(spSheet, 10, snapshot.staffProfiles.size + 2)
            }


            // ==========================================
            // 13. Sheet: گزارش بالینی زخم (Wound Records)
            // ==========================================
            executeSheetGeneration(context, "گزارش بالینی زخم", "Populating Clinical Wound Records Archive") {
                val wrSheet = createCustomSheet(
                    "گزارش بالینی زخم",
                    "دفتر اختصاصی ارزیابی، مدیریت و سوابق بالینی زخم بیماران - همراهان سلامت",
                    listOf("شناسه ارزیابی", "شناسه بیمار", "نام کامل بیمار", "نوع زخم", "موقعیت / گرید زخم", "شرح، روند درمان و دستورات پانسمان", "تاریخ ثبت ارزیابی")
                )
                snapshot.woundRecords.forEachIndexed { index, wr ->
                    val row = wrSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle

                    row.createSafeCell(0, wr.id, style)
                    row.createSafeCell(1, wr.patientId, style)
                    row.createSafeCell(2, wr.patientName, style)
                    row.createSafeCell(3, wr.woundType, style)
                    row.createSafeCell(4, wr.stage, style)
                    row.createSafeCell(5, wr.description, style)
                    row.createSafeCell(6, wr.date.formatDateTime(), dateStyle)
                }
                finalizeCustomSheet(wrSheet, 7, snapshot.woundRecords.size + 2)
            }


            // ==========================================
            // 14. Sheet: نسخ و دستورات (Prescriptions)
            // ==========================================
            executeSheetGeneration(context, "نسخ و دستورات", "Populating Prescriptions Medical Archive") {
                val prSheet = createCustomSheet(
                    "نسخ و دستورات",
                    "دفتر ثبت نسخ، دستورات دارویی و سوابق پزشکی بیماران - همراهان سلامت",
                    listOf("شناسه نسخه", "شناسه بیمار", "نام کامل بیمار", "پزشک معالج / صادرکننده", "لیست داروها، دوز و دستورات مصرف", "تاریخ ثبت نسخه")
                )
                snapshot.prescriptions.forEachIndexed { index, pr ->
                    val row = prSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle

                    row.createSafeCell(0, pr.id, style)
                    row.createSafeCell(1, pr.patientId, style)
                    row.createSafeCell(2, pr.patientName, style)
                    row.createSafeCell(3, pr.doctorName, style)
                    row.createSafeCell(4, pr.medicineList, style)
                    row.createSafeCell(5, pr.date.formatDateTime(), dateStyle)
                }
                finalizeCustomSheet(prSheet, 6, snapshot.prescriptions.size + 2)
            }


            // ==========================================
            // 15. Sheet: رضایتنامه‌ها (Consent Forms)
            // ==========================================
            executeSheetGeneration(context, "رضایتنامه‌ها", "Populating Legal Consent Forms Archive") {
                val cfSheet = createCustomSheet(
                    "رضایتنامه‌ها",
                    "دفتر ثبت رضایت‌نامه‌ها و مستندات قانونی بیماران - همراهان سلامت",
                    listOf("شناسه رضایت‌نامه", "شناسه بیمار", "نام کامل بیمار / امضاکننده", "عنوان / نوع رضایت‌نامه", "مفاد و شرح رضایت‌نامه", "وضعیت امضا", "تاریخ ثبت / امضا")
                )
                snapshot.consentForms.forEachIndexed { index, cf ->
                    val row = cfSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle

                    row.createSafeCell(0, cf.id, style)
                    row.createSafeCell(1, cf.patientId, style)
                    row.createSafeCell(2, cf.patientName, style)
                    row.createSafeCell(3, cf.title, style)
                    row.createSafeCell(4, cf.content, style)
                    row.createSafeCell(5, if (cf.isSigned) "امضا شده" else "در انتظار امضا", style)
                    row.createSafeCell(6, cf.date.formatDateTime(), dateStyle)
                }
                finalizeCustomSheet(cfSheet, 7, snapshot.consentForms.size + 2)
            }


            // ==========================================
            // 16. Sheet: کمیسیون‌های ارجاع (Referral Commissions)
            // ==========================================
            executeSheetGeneration(context, "کمیسیون‌های ارجاع", "Populating Referral Commissions Tracking") {
                val rcSheet = createCustomSheet(
                    "کمیسیون‌های ارجاع",
                    "دفتر جامع محاسبات و پرداخت کمیسیون‌های ارجاع بیماران - همراهان سلامت",
                    listOf("شناسه کمیسیون", "نام معرف", "نام بیمار", "نام خدمت", "مبلغ خدمت", "درصد کمیسیون", "مبلغ کمیسیون", "تاریخ ارجاع", "وضعیت پرداخت", "تاریخ تسویه", "شماره سند / مرجع", "توضیحات")
                )
                snapshot.referralCommissions.forEachIndexed { index, rc ->
                    val row = rcSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle
                    val percentageStyle = if (index % 2 == 1) stripeStyle else dataStyle

                    row.createSafeCell(0, rc.id, style)
                    row.createSafeCell(1, rc.referralName, style)
                    row.createSafeCell(2, rc.patientName, style)
                    row.createSafeCell(3, rc.serviceName, style)
                    row.createSafeCell(4, rc.serviceAmount, curStyle)
                    row.createSafeCell(5, "${rc.commissionPercentage}%", percentageStyle)
                    row.createSafeCell(6, rc.commissionAmount, curStyle)
                    row.createSafeCell(7, rc.date.formatDateTime(), dateStyle)
                    row.createSafeCell(8, rc.status, style)
                    row.createSafeCell(9, rc.paymentDate?.formatDateTime() ?: "-", dateStyle)
                    row.createSafeCell(10, rc.documentNumber, style)
                    row.createSafeCell(11, rc.notes, style)
                }
                val totalRowIdx = snapshot.referralCommissions.size + 3
                if (snapshot.referralCommissions.isNotEmpty()) {
                    val totRow = rcSheet.createRow(totalRowIdx)
                    totRow.heightInPoints = 24f
                    totRow.createSafeCell(0, "مجموع کمیسیون‌های ارجاع:", totalsLabelStyle)
                    for (col in 1..3) totRow.createSafeCell(col, "", totalsLabelStyle)
                    totRow.createSafeCell(4, snapshot.referralCommissions.sumOf { it.serviceAmount }, totalsValueStyle)
                    totRow.createSafeCell(5, "", totalsLabelStyle)
                    totRow.createSafeCell(6, snapshot.referralCommissions.sumOf { it.commissionAmount }, totalsValueStyle)
                    for (col in 7..11) totRow.createSafeCell(col, "", totalsLabelStyle)
                }
                finalizeCustomSheet(rcSheet, 12, totalRowIdx)
            }


            // ==========================================
            // 17. Sheet: دفتر روزنامه حسابداری (Accounting Journal Entries)
            // ==========================================
            executeSheetGeneration(context, "دفتر روزنامه حسابداری", "Populating Double-Entry Accounting Journal") {
                val jeSheet = createCustomSheet(
                    "دفتر روزنامه حسابداری",
                    "دفتر روزنامه حسابداری و ثبت اسناد آرتیکل‌های مالی - همراهان سلامت",
                    listOf("شناسه آرتیکل", "شماره سند روزنامه", "حساب بدهکار", "حساب بستانکار", "مبلغ آرتیکل (تومان)", "تاریخ ثبت سند", "شرح آرتیکل حسابداری", "شناسه سند مرجع")
                )
                snapshot.journalEntries.forEachIndexed { index, je ->
                    val row = jeSheet.createRow(index + 3)
                    row.heightInPoints = 22f
                    val style = if (index % 2 == 1) stripeStyle else dataStyle
                    val curStyle = if (index % 2 == 1) currencyStripeStyle else currencyStyle

                    row.createSafeCell(0, je.id, style)
                    row.createSafeCell(1, je.documentNumber, style)
                    row.createSafeCell(2, je.debitAccount, style)
                    row.createSafeCell(3, je.creditAccount, style)
                    row.createSafeCell(4, je.amount, curStyle)
                    row.createSafeCell(5, je.date.formatDateTime(), dateStyle)
                    row.createSafeCell(6, je.reference, style)
                    row.createSafeCell(7, je.referenceId ?: "-", style)
                }
                val jeTotalIdx = snapshot.journalEntries.size + 3
                if (snapshot.journalEntries.isNotEmpty()) {
                    val totRow = jeSheet.createRow(jeTotalIdx)
                    totRow.heightInPoints = 24f
                    totRow.createSafeCell(0, "جمع آرتیکل‌های روزنامه:", totalsLabelStyle)
                    for (col in 1..3) totRow.createSafeCell(col, "", totalsLabelStyle)
                    totRow.createSafeCell(4, snapshot.journalEntries.sumOf { it.amount }, totalsValueStyle)
                    for (col in 5..7) totRow.createSafeCell(col, "", totalsLabelStyle)
                }
                finalizeCustomSheet(jeSheet, 8, jeTotalIdx)
            }


            // ==========================================
            // ATOMIC FILE WRITE & STREAM SAFETY
            // ==========================================
            val temp = File.createTempFile("excel_export_", ".tmp", context.cacheDir)
            tempFile = temp
            if (temp.exists()) {
                temp.delete()
            }

            var writeSuccessful = false
            try {
                java.io.FileOutputStream(temp).use { fos ->
                    java.io.BufferedOutputStream(fos).use { bos ->
                        CountingOutputStream(bos).use { countingOS ->
                            workbook.write(countingOS)
                            countingOS.flush()
                        }
                    }
                }
                writeSuccessful = true
            } catch (e: Exception) {
                Log.e("EXPORT_DEBUG", "Workbook atomic write to temporary file failed", e)
                EnterpriseCrashLogger.log(context, "Workbook atomic write to temporary file failed: [${e.javaClass.name}] ${e.message}")
                EnterpriseCrashLogger.logThrowable(context, "Atomic Write Execution Exception", e)
                writeSuccessful = false
            } finally {
                try {
                    workbook.close()
                } catch (_: Exception) {}
                activeWorkbook = null
            }

            // Validate temp file before touching destination
            if (!writeSuccessful || !temp.exists() || temp.length() <= 0L) {
                val valError = "Atomic File Validation Failed -> File Exists: ${temp.exists()}, Size: ${if (temp.exists()) temp.length() else -1}, Write Success: $writeSuccessful"
                Log.e("EXPORT_DEBUG", valError)
                EnterpriseCrashLogger.log(context, valError)
                if (temp.exists()) {
                    temp.delete()
                }
                return@runBlocking false
            }

            // Copy validated temp file stream to destination outputStream safely
            try {
                temp.inputStream().use { fis ->
                    java.io.BufferedInputStream(fis).use { bis ->
                        bis.copyTo(outputStream)
                    }
                }
                outputStream.flush()
            } catch (e: Exception) {
                Log.e("EXPORT_DEBUG", "Failed copying validated temp file to outputStream", e)
                EnterpriseCrashLogger.log(context, "Destination output stream copy failed: [${e.javaClass.name}] ${e.message}")
                EnterpriseCrashLogger.logThrowable(context, "Destination Output Stream Copy Exception", e)
                if (temp.exists()) {
                    temp.delete()
                }
                return@runBlocking false
            }

            Log.i("EXPORT_DEBUG", "LOG: Export completed with full atomic integrity. Output size: ${temp.length()} bytes")
            System.out.println("EXPORT_DEBUG LOG: Export completed with full atomic integrity. Output size: ${temp.length()} bytes")

            val duration = System.currentTimeMillis() - startTime
            EnterpriseCrashLogger.logPhase(context, "Completed Modern XLSX Write", duration, "SUCCESS")
            return@runBlocking true
        } catch (sge: SheetGenerationException) {
            val runtime = Runtime.getRuntime()
            val freeMem = runtime.freeMemory()
            val totalMem = runtime.totalMemory()
            val maxMem = runtime.maxMemory()
            val usedMem = totalMem - freeMem

            val details = StringBuilder().apply {
                append("--- SHEET GENERATION EXCEPTION DETECTED ---\n")
                append("Failed Sheet: ${sge.sheetName}\n")
                append("Operation: ${sge.operation}\n")
                append("Exception Type: ${sge.originalException.javaClass.name}\n")
                append("Message: ${sge.originalException.message}\n")
                append("Localized Message: ${sge.originalException.localizedMessage}\n")
                append("Cause: ${sge.originalException.cause}\n")
                append("Thread Name: ${Thread.currentThread().name}\n")
                append("Memory Status:\n")
                append("  Free Memory: ${freeMem / (1024 * 1024)} MB\n")
                append("  Total Memory: ${totalMem / (1024 * 1024)} MB\n")
                append("  Used Memory: ${usedMem / (1024 * 1024)} MB\n")
                append("-------------------------------------------\n")
            }.toString()

            Log.e("EXPORT_DEBUG", details, sge)
            EnterpriseCrashLogger.log(context, details)
            EnterpriseCrashLogger.logThrowable(context, "Sheet Generation Failure [${sge.sheetName}]", sge)
            return@runBlocking false
        } catch (e: Exception) {
            val runtime = Runtime.getRuntime()
            val freeMem = runtime.freeMemory()
            val totalMem = runtime.totalMemory()
            val maxMem = runtime.maxMemory()
            val usedMem = totalMem - freeMem

            val details = StringBuilder().apply {
                append("--- ERROR ORIGIN TRACE MODE ---\n")
                append("Exception Class: ${e.javaClass.name}\n")
                append("Message: ${e.message}\n")
                append("Localized Message: ${e.localizedMessage}\n")
                append("Cause: ${e.cause}\n")
                append("Thread Name: ${Thread.currentThread().name}\n")
                append("Memory Status:\n")
                append("  Free Memory: ${freeMem / (1024 * 1024)} MB\n")
                append("  Total Memory: ${totalMem / (1024 * 1024)} MB\n")
                append("  Max Memory: ${maxMem / (1024 * 1024)} MB\n")
                append("  Used Memory: ${usedMem / (1024 * 1024)} MB\n")
                append("Local Variables:\n")
                append("  Patients Count: ${snapshot.patients.size}\n")
                append("  Staff Count: ${snapshot.personnel.size}\n")
                append("  Services Count: ${snapshot.services.size}\n")
                append("  ServiceRegistrations Count: ${snapshot.serviceRegistrations.size}\n")
                append("  Expenses Count: ${snapshot.expenses.size}\n")
                append("  Financial Transactions Count: ${snapshot.financialTransactions.size}\n")
                append("-------------------------------\n")
            }.toString()
            Log.e("EXPORT_DEBUG", "Exception in exportSnapshotToExcel: $details", e)
            e.printStackTrace()
            EnterpriseCrashLogger.log(context, details)
            EnterpriseCrashLogger.logThrowable(context, "XSSF Export Failure (Exception)", e)
            return@runBlocking false
        } catch (t: Throwable) {
            val runtime = Runtime.getRuntime()
            val freeMem = runtime.freeMemory()
            val totalMem = runtime.totalMemory()
            val maxMem = runtime.maxMemory()
            val usedMem = totalMem - freeMem

            val details = StringBuilder().apply {
                append("--- ERROR ORIGIN TRACE MODE (Throwable) ---\n")
                append("Throwable Class: ${t.javaClass.name}\n")
                append("Message: ${t.message}\n")
                append("Localized Message: ${t.localizedMessage}\n")
                append("Cause: ${t.cause}\n")
                append("Thread Name: ${Thread.currentThread().name}\n")
                append("Memory Status:\n")
                append("  Free Memory: ${freeMem / (1024 * 1024)} MB\n")
                append("  Total Memory: ${totalMem / (1024 * 1024)} MB\n")
                append("  Max Memory: ${maxMem / (1024 * 1024)} MB\n")
                append("  Used Memory: ${usedMem / (1024 * 1024)} MB\n")
                append("-------------------------------------------\n")
            }.toString()
            Log.e("EXPORT_DEBUG", "Throwable in exportSnapshotToExcel: $details", t)
            t.printStackTrace()
            EnterpriseCrashLogger.log(context, details)
            EnterpriseCrashLogger.logThrowable(context, "XSSF Export Failure (Throwable)", t)
            return@runBlocking false
        } finally {
            try {
                activeWorkbook?.close()
            } catch (_: Exception) {}
            try {
                tempFile?.let { if (it.exists()) it.delete() }
            } catch (_: Exception) {}
        }
    }

    /**
     * Legacy backward-compatible method signature.
     * Maps raw database lists to the new clean projection Report snapshot and triggers the modern export engine.
     */
    fun exportToExcel(
        context: Context,
        outputStream: OutputStream,
        patients: List<Patient>? = null,
        employees: List<Employee>? = null,
        services: List<Service>? = null,
        registrations: List<ServiceRegistration>? = null,
        transactions: List<FinancialTransaction>? = null,
        expenses: List<Expense>? = null,
        referrals: List<Referral>? = null,
        commissions: List<ReferralCommission>? = null
    ): Boolean = runBlocking(Dispatchers.IO) {
        val allRegs = registrations ?: emptyList()
        val allTxs = transactions ?: emptyList()

        // Pre-group for O(N + M) memory & performance scaling
        val regsByEmployee = allRegs.groupBy { it.employeeId }
        val regsByPatient = allRegs.groupBy { it.patientId }
        val regsByService = allRegs.groupBy { it.serviceId }

        val snapshot = ReportingLayer.BusinessReportSnapshot(
            personnel = (employees ?: emptyList()).map { emp ->
                val empRegs = regsByEmployee[emp.id] ?: emptyList()
                val totalComms = empRegs.sumOf { it.employeeCost }
                val settled = totalComms
                val pending = 0.0
                val payments = allTxs.filter { tx ->
                    tx.type == "هزینه" && (tx.category == "حقوق همکار" || tx.description.contains(emp.fullName))
                }.sumOf { it.amount }

                ReportingLayer.PersonnelReportDto(
                    id = emp.id, fullName = emp.fullName, nationalId = emp.nationalId, phone = emp.phone,
                    profession = emp.profession, position = emp.position, employmentType = emp.employmentType,
                    status = emp.status, commissionModel = emp.commissionModel, commissionValue = emp.commissionValue,
                    totalSettledCommissions = settled, totalPendingCommissions = pending, totalPaymentsReceived = payments,
                    bankInfo = emp.bankInfo
                )
            },
            patients = (patients ?: emptyList()).map { p ->
                val pRegs = regsByPatient[p.id] ?: emptyList()
                val totalInvoiced = pRegs.sumOf { it.finalPrice }
                val totalPaid = pRegs.filter { it.isPaid }.sumOf { it.finalPrice }
                ReportingLayer.PatientReportDto(
                    id = p.id, fullName = p.fullName, gender = p.gender, age = p.age, phone = p.phone, address = p.address,
                    referralSource = p.referralSource, status = p.status, totalInvoiced = totalInvoiced, totalPaid = totalPaid,
                    remainingBalance = totalInvoiced - totalPaid, servicesCount = pRegs.size, registrationDate = p.registrationDate, notes = p.notes
                )
            },
            nursingHistories = emptyList(),
            services = (services ?: emptyList()).map { s ->
                val sRegs = regsByService[s.id] ?: emptyList()
                ReportingLayer.ServiceReportDto(
                    id = s.id, name = s.name, category = s.category, sellingPrice = s.sellingPrice, defaultCost = s.defaultCost,
                    durationMinutes = s.durationMinutes, timesCompleted = sRegs.filter { it.isPaid }.size,
                    timesScheduled = sRegs.filter { !it.isPaid }.size, timesCancelled = 0, status = if (s.isActive) "فعال" else "غیرفعال"
                )
            },
            financialSummary = ReportingLayer.FinancialSummaryReportDto(
                totalIncome = allTxs.filter { it.type == "درآمد" }.sumOf { it.amount },
                totalExpenses = allTxs.filter { it.type == "هزینه" }.sumOf { it.amount },
                netProfit = allTxs.filter { it.type == "درآمد" }.sumOf { it.amount } - allTxs.filter { it.type == "هزینه" }.sumOf { it.amount },
                totalReceivables = allRegs.filter { !it.isPaid }.sumOf { it.finalPrice },
                totalPayables = 0.0
            ),
            cashboxes = emptyList(),
            expenses = (expenses ?: emptyList()).map { exp ->
                ReportingLayer.ExpenseReportDto(
                    id = exp.id, title = exp.title, category = exp.category, amount = exp.amount,
                    registrationDate = exp.registrationDate, paymentDate = exp.paymentDate,
                    paymentMethod = exp.paymentMethod, submitterName = exp.submitterName,
                    description = exp.description, status = exp.workflowStatus
                )
            },
            financialTransactions = allTxs.map { tx ->
                ReportingLayer.FinancialTransactionReportDto(
                    id = tx.id, type = tx.type, category = tx.category, amount = tx.amount, date = tx.date,
                    description = tx.description, paymentMethod = tx.paymentMethod, referenceId = tx.referenceId
                )
            },
            invoices = allRegs.map { reg ->
                ReportingLayer.InvoiceReportDto(
                    invoiceNumber = reg.invoiceNumber, patientName = "بیمار ${reg.patientId}", serviceName = "خدمت ${reg.serviceId}",
                    date = reg.dateTime, finalPrice = reg.finalPrice, isPaid = reg.isPaid, paymentMethod = reg.paymentMethod, notes = reg.notes
                )
            },
            contracts = emptyList(),
            dashboardSummaries = emptyList(),
            referrals = (referrals ?: emptyList()).map { ref ->
                ReportingLayer.ReferralReportDto(
                    id = ref.id, fullName = ref.name, phone = ref.phone, specialty = ref.type, notes = ref.notes
                )
            },
            referralCommissions = (commissions ?: emptyList()).map { rc ->
                ReportingLayer.ReferralCommissionReportDto(
                    id = rc.id, referralId = rc.referralId, referralName = "معرف ${rc.referralId}",
                    patientId = rc.patientId, patientName = "بیمار ${rc.patientId}",
                    serviceRegistrationId = rc.serviceRegistrationId, serviceName = rc.serviceName,
                    serviceAmount = rc.serviceAmount, commissionPercentage = rc.commissionPercentage,
                    commissionAmount = rc.commissionAmount, date = rc.date, status = rc.status,
                    paymentDate = rc.paymentDate, documentNumber = rc.documentNumber, notes = rc.notes
                )
            },
            systemSettings = emptyList(),
            auditLogs = emptyList(),
            editHistories = emptyList(),
            syncMetadata = emptyList(),
            commissionSettlements = emptyList(),
            nursingReports = emptyList(),
            vitalSigns = emptyList(),
            woundRecords = emptyList(),
            serviceRegistrations = allRegs.map { reg ->
                ReportingLayer.ServiceRegistrationReportDto(
                    id = reg.id, patientId = reg.patientId, patientName = "بیمار ${reg.patientId}",
                    employeeId = reg.employeeId, employeeName = "همکار ${reg.employeeId}",
                    serviceId = reg.serviceId, serviceName = "خدمت ${reg.serviceId}",
                    dateTime = reg.dateTime, sellingPrice = reg.sellingPrice, employeeCost = reg.employeeCost,
                    transportationCost = reg.transportationCost, otherCosts = reg.otherCosts,
                    discount = reg.discount, finalPrice = reg.finalPrice, paymentMethod = reg.paymentMethod,
                    invoiceNumber = reg.invoiceNumber, notes = reg.notes, isPaid = reg.isPaid,
                    workflowStatus = reg.workflowStatus
                )
            }
        )
        return@runBlocking exportSnapshotToExcel(context, outputStream, snapshot)
    }
}

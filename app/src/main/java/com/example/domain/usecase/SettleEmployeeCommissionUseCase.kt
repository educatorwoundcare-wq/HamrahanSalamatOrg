package com.example.domain.usecase

import com.example.data.CommissionSettlement
import com.example.data.FinancialTransaction
import com.example.data.HamrahanDao
import com.example.data.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettleEmployeeCommissionUseCase(
    private val dao: HamrahanDao,
    private val syncEngine: SyncEngine
) {
    suspend operator fun invoke(
        settlement: CommissionSettlement,
        employeeName: String,
        selectedCashboxId: Int?
    ) = withContext(Dispatchers.IO) {
        dao.runInTransaction {
            // 1. Insert Settlement record
            val settlementId = dao.insertCommissionSettlement(settlement).toInt()
            
            // 2. Insert financial transaction of type EXPENSE (هزینه) representing cash outflow for wage payment
            val description = "تسویه کارمزد همکار «$employeeName» بابت دوره ${settlement.periodStart.toDateString()} الی ${settlement.periodEnd.toDateString()}"
            val tx = FinancialTransaction(
                type = "هزینه",
                category = "حقوق همکار",
                amount = settlement.amount,
                date = settlement.settlementDate,
                description = description,
                paymentMethod = if (selectedCashboxId != null) "بانکی/صندوق" else "کارت به کارت",
                referenceId = settlementId,
                origin = "Salary"
            )
            val txId = dao.insertFinancialTransaction(tx).toInt()
            
            // 3. Update Cashbox balance if paid from a local cashbox
            if (selectedCashboxId != null) {
                val cashbox = dao.getCashboxById(selectedCashboxId)
                if (cashbox != null) {
                    val updatedCashbox = cashbox.copy(balance = cashbox.balance - settlement.amount)
                    dao.updateCashbox(updatedCashbox)
                    registerLocalChange("Cashbox", updatedCashbox.uuid)
                }
            }
            
            // Insert informational alert
            val docAlert = com.example.data.Alert(
                title = "✅ سند تسویه ثبت شد",
                description = "سند پرداخت شماره SETTLE-${settlementId} تولید شد و تایید شد.",
                type = "document_created",
                alertType = "document_created",
                entityId = "settle_$settlementId",
                status = "COMPLETED",
                isDismissed = true,
                isRead = true,
                createdAt = System.currentTimeMillis(),
                resolvedAt = System.currentTimeMillis()
            )
            dao.insertAlert(docAlert)
            registerLocalChange("Alert", docAlert.uuid)
            
            registerLocalChange("CommissionSettlement", settlement.uuid)
            registerLocalChange("FinancialTransaction", tx.uuid)
        }
    }

    private suspend fun registerLocalChange(entityType: String, entityId: String, isDeleted: Boolean = false) {
        val activeDeviceId = dao.getSystemSettingByKey("active_device_id")?.takeIf { com.example.data.DeviceIdentityProvider.isValidUuidDeviceId(it) }
            ?: "DEV-00000000-0000-0000-0000-000000000000"
        val meta = com.example.data.SyncMetadata(
            entityType = entityType,
            entityId = entityId,
            updatedTimestamp = System.currentTimeMillis(),
            deletedStatus = isDeleted,
            lastModifiedDeviceId = activeDeviceId,
            syncStatus = "Pending"
        )
        dao.insertSyncMetadata(meta)
        syncEngine.triggerSync()
    }

    private fun Long.toDateString(): String {
        return SimpleDateFormat("yyyy/MM/dd", Locale("fa")).format(Date(this))
    }
}

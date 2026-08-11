package com.example.domain.usecase

import com.example.data.Alert
import com.example.data.AuditLog
import com.example.data.FinancialTransaction
import com.example.data.HamrahanDao
import com.example.data.JournalEntry
import com.example.data.ReferralCommission
import com.example.data.ServiceRegistration
import com.example.data.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegisterServiceAndGenerateLedgerUseCase(
    private val dao: HamrahanDao,
    private val syncEngine: SyncEngine
) {
    suspend operator fun invoke(
        reg: ServiceRegistration,
        patientName: String,
        serviceName: String,
        employeeName: String,
        selectedCashboxId: Int?
    ) = withContext(Dispatchers.IO) {
        var regId = 0
        dao.runInTransaction {
            // Update cashboxId in reg if selectedCashboxId is provided
            val regWithCashbox = if (selectedCashboxId != null) {
                reg.copy(cashboxId = selectedCashboxId)
            } else {
                reg
            }

            // 1. Insert service registration and get generated ID
            regId = dao.insertServiceRegistration(regWithCashbox).toInt()

            // 2. Automatically generate Financial Transaction of type INCOME (درآمد)
            val incomeDescription = "ثبت خدمت «$serviceName» برای بیمار «$patientName» توسط همکار «$employeeName»"
            val incomeTx = FinancialTransaction(
                type = "درآمد",
                category = "ثبت خدمت",
                amount = regWithCashbox.finalPrice,
                date = regWithCashbox.serviceDate,
                description = incomeDescription,
                paymentMethod = regWithCashbox.paymentMethod,
                referenceId = regId,
                origin = "Service"
            )
            dao.insertFinancialTransaction(incomeTx)
            registerLocalChange("FinancialTransaction", incomeTx.uuid)

            // 3. Automatically generate accrued commission transaction as EXPENSE (هزینه)
            // representing the payables of the company to the employee.
            val baseCommission = regWithCashbox.employeeCommission - regWithCashbox.transportationCost
            val expenseDescription = "کارمزد همکار «$employeeName» بابت خدمت «$serviceName» برای بیمار «$patientName»"
            val expenseTx = FinancialTransaction(
                type = "هزینه",
                category = "حقوق همکار",
                amount = baseCommission,
                date = regWithCashbox.serviceDate,
                description = expenseDescription,
                paymentMethod = "ثبت در حساب (بستانکار)",
                referenceId = regId,
                isCleared = false, // Will be cleared upon Monthly Settlement
                origin = "Salary"
            )
            dao.insertFinancialTransaction(expenseTx)
            registerLocalChange("FinancialTransaction", expenseTx.uuid)

            if (regWithCashbox.transportationCost > 0.0) {
                val transportTx = FinancialTransaction(
                    type = "هزینه",
                    category = "STAFF_TRANSPORTATION",
                    amount = regWithCashbox.transportationCost,
                    date = regWithCashbox.serviceDate,
                    description = "هزینه ایاب و ذهاب همکار «$employeeName» بابت خدمت «$serviceName»",
                    paymentMethod = "ثبت در حساب (بستانکار)",
                    referenceId = regId,
                    isCleared = false,
                    origin = "STAFF_TRANSPORTATION"
                )
                dao.insertFinancialTransaction(transportTx)
                registerLocalChange("FinancialTransaction", transportTx.uuid)
            }

            // 4. Update Cashbox balance if applicable
            val targetCashboxId = regWithCashbox.cashboxId
            if (targetCashboxId != null) {
                val cashbox = dao.getCashboxById(targetCashboxId)
                if (cashbox != null) {
                    // Add finalPrice to cashbox balance
                    val updatedCashbox = cashbox.copy(balance = cashbox.balance + regWithCashbox.finalPrice)
                    dao.updateCashbox(updatedCashbox)
                    registerLocalChange("Cashbox", updatedCashbox.uuid)
                }
            }

            // 5. Future-ready double-entry ledger entry
            val docNum = "REG-${System.currentTimeMillis() % 1000000}"
            val ledgerDebit = if (regWithCashbox.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
            val ledgerCredit = "درآمد خدمات سلامت (درآمد)"
            dao.insertJournalEntry(
                JournalEntry(
                    documentNumber = docNum,
                    debitAccount = ledgerDebit,
                    creditAccount = ledgerCredit,
                    amount = regWithCashbox.finalPrice,
                    reference = "ثبت خدمت شماره ${regWithCashbox.invoiceNumber}",
                    referenceId = regId
                )
            )

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Create",
                    affectedModule = "ServiceRegistrations",
                    details = "ثبت خدمت جدید برای بیمار ${patientName}، مبلغ نهایی: ${regWithCashbox.finalPrice}"
                )
            )

            // Insert informational alert
            val docAlert = Alert(
                title = "✅ سند ثبت خدمت تولید شد",
                description = "سند پرداخت شماره ${regWithCashbox.invoiceNumber} تولید شد و تایید شد.",
                type = "document_created",
                alertType = "document_created",
                entityId = "reg_$regId",
                status = "COMPLETED",
                isDismissed = true,
                isRead = true,
                createdAt = System.currentTimeMillis(),
                resolvedAt = System.currentTimeMillis()
            )
            dao.insertAlert(docAlert)
            registerLocalChange("Alert", docAlert.uuid)

            // 6. Handle Referral Commission
            val patient = dao.getPatientById(regWithCashbox.patientId)
            if (patient?.referralId != null) {
                val referral = dao.getReferralById(patient.referralId)
                if (referral != null && referral.isActive) {
                    val commissionAmount = if (referral.commissionPercentage > 0.0) {
                        regWithCashbox.companyProfit * (referral.commissionPercentage / 100.0)
                    } else {
                        referral.commissionFixedAmount
                    }
                    if (commissionAmount > 0.0) {
                        val referralCommission = ReferralCommission(
                            referralId = referral.id,
                            patientId = regWithCashbox.patientId,
                            serviceRegistrationId = regId,
                            serviceName = serviceName,
                            serviceAmount = regWithCashbox.finalPrice,
                            commissionPercentage = referral.commissionPercentage,
                            commissionAmount = commissionAmount,
                            status = "در انتظار پرداخت",
                            date = regWithCashbox.serviceDate
                        )
                        dao.insertReferralCommission(referralCommission)
                        
                        // Insert into Financial Transactions as an Expense (هزینه) under category "پورسانت معرف"
                        val referralTx = FinancialTransaction(
                            type = "هزینه",
                            category = "پورسانت معرف",
                            amount = commissionAmount,
                            date = regWithCashbox.serviceDate,
                            description = "پورسانت معرف «${referral.name}» بابت خدمت «$serviceName» برای بیمار «$patientName»",
                            paymentMethod = "ثبت در حساب (بستانکار)",
                            referenceId = regId,
                            isCleared = false,
                            origin = "Referral"
                        )
                        dao.insertFinancialTransaction(referralTx)
                        registerLocalChange("FinancialTransaction", referralTx.uuid)
                    }
                }
            }

            // We omit validateFinancialIntegrity here as it should ideally be run separately or moved to use case
            // If it's needed, we can call it here if it was exposed.
        }
        
        registerLocalChange("ServiceRegistration", reg.uuid)
        val generatedCommission = dao.getCommissionByServiceRegistration(regId)
        if (generatedCommission != null) {
            registerLocalChange("ReferralCommission", generatedCommission.uuid)
        }
    }

    private suspend fun registerLocalChange(entityType: String, entityId: String, isDeleted: Boolean = false) {
        val activeDeviceId = dao.getSystemSettingByKey("active_device_id") ?: "UNKNOWN-DEVICE"
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
}

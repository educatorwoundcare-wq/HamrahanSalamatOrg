package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RestoreReport(
    val imported: Int,
    val skipped: Int,
    val deferred: Int,
    val failed: Int
)

object CoreDomainBackupManager {

    suspend fun exportBackupJson(dao: HamrahanDao): String {
        val expenseCategories = dao.getExpenseCategoriesList()
        val fixedExpenseTemplates = dao.getFixedExpenseTemplatesList()
        val referrals = dao.getReferralsList()
        val commissionSettlements = dao.getCommissionSettlementsList()
        val referralCommissions = dao.getReferralCommissionsList()
        val patients = dao.getPatientsList()
        val employees = dao.getEmployeesList()
        val services = dao.getServicesList()
        val cashboxes = dao.getCashboxesList()
        val serviceRegistrations = dao.getServiceRegistrationsList()
        val expenses = dao.getExpensesList()
        val financialTransactions = dao.getFinancialTransactionsList()

        val employeeUuidMap = employees.associate { it.id to it.uuid }
        val referralUuidMap = referrals.associate { it.id to it.uuid }
        val patientUuidMap = patients.associate { it.id to it.uuid }
        val serviceRegistrationUuidMap = serviceRegistrations.associate { it.id to it.uuid }
        val serviceUuidMap = services.associate { it.id to it.uuid }
        val cashboxUuidMap = cashboxes.associate { it.id to it.uuid }
        val categoryUuidMap = expenseCategories.associate { it.name to it.uuid }

        val expenseCategoriesArr = JSONArray()
        expenseCategories.forEach { item ->
            expenseCategoriesArr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("isSystemDefault", item.isSystemDefault)
                put("uuid", item.uuid)
            })
        }

        val fixedExpenseTemplatesArr = JSONArray()
        fixedExpenseTemplates.forEach { item ->
            fixedExpenseTemplatesArr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("monthlyAmount", item.monthlyAmount)
                put("paymentDay", item.paymentDay)
                put("isActive", item.isActive)
                put("category", item.category)
                put("categoryUuid", categoryUuidMap[item.category] ?: "")
                put("recurringInterval", item.recurringInterval)
                put("uuid", item.uuid)
            })
        }

        val referralsArr = JSONArray()
        referrals.forEach { item ->
            referralsArr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("type", item.type)
                put("phone", item.phone)
                put("address", item.address)
                put("commissionPercentage", item.commissionPercentage)
                put("commissionFixedAmount", item.commissionFixedAmount)
                put("notes", item.notes)
                put("isActive", item.isActive)
                put("uuid", item.uuid)
            })
        }

        val commissionSettlementsArr = JSONArray()
        commissionSettlements.forEach { item ->
            commissionSettlementsArr.put(JSONObject().apply {
                put("id", item.id)
                put("employeeId", item.employeeId)
                put("employeeUuid", employeeUuidMap[item.employeeId] ?: "")
                put("amount", item.amount)
                put("settlementDate", item.settlementDate)
                put("periodStart", item.periodStart)
                put("periodEnd", item.periodEnd)
                put("notes", item.notes)
                put("uuid", item.uuid)
            })
        }

        val referralCommissionsArr = JSONArray()
        referralCommissions.forEach { item ->
            referralCommissionsArr.put(JSONObject().apply {
                put("id", item.id)
                put("referralId", item.referralId)
                put("referralUuid", referralUuidMap[item.referralId] ?: "")
                put("patientId", item.patientId)
                put("patientUuid", patientUuidMap[item.patientId] ?: "")
                put("serviceRegistrationId", item.serviceRegistrationId)
                put("serviceRegistrationUuid", serviceRegistrationUuidMap[item.serviceRegistrationId] ?: "")
                put("serviceName", item.serviceName)
                put("serviceAmount", item.serviceAmount)
                put("commissionPercentage", item.commissionPercentage)
                put("commissionAmount", item.commissionAmount)
                put("date", item.date)
                put("status", item.status)
                if (item.paymentDate != null) put("paymentDate", item.paymentDate)
                put("documentNumber", item.documentNumber)
                put("notes", item.notes)
                put("uuid", item.uuid)
            })
        }

        val patientsArr = JSONArray()
        patients.forEach { item ->
            patientsArr.put(JSONObject().apply {
                put("id", item.id)
                put("fullName", item.fullName)
                put("gender", item.gender)
                put("age", item.age)
                put("phone", item.phone)
                put("address", item.address)
                put("referralSource", item.referralSource)
                if (item.referralId != null) {
                    put("referralId", item.referralId)
                    put("referralUuid", referralUuidMap[item.referralId] ?: "")
                }
                put("status", item.status)
                put("registrationDate", item.registrationDate)
                put("notes", item.notes)
                put("tags", item.tags)
                put("uuid", item.uuid)
            })
        }

        val employeesArr = JSONArray()
        employees.forEach { item ->
            employeesArr.put(JSONObject().apply {
                put("id", item.id)
                put("fullName", item.fullName)
                put("nationalId", item.nationalId)
                put("phone", item.phone)
                put("profession", item.profession)
                put("position", item.position)
                put("skill", item.skill)
                put("employmentType", item.employmentType)
                put("commissionModel", item.commissionModel)
                put("commissionValue", item.commissionValue)
                put("bankInfo", item.bankInfo)
                put("status", item.status)
                put("startDate", item.startDate)
                put("notes", item.notes)
                put("uuid", item.uuid)
            })
        }

        val servicesArr = JSONArray()
        services.forEach { item ->
            servicesArr.put(JSONObject().apply {
                put("id", item.id)
                put("officialCode", item.officialCode)
                put("officialName", item.officialName)
                put("name", item.name)
                put("category", item.category)
                put("officialTariff", item.officialTariff)
                put("sellingPrice", item.sellingPrice)
                put("defaultCost", item.defaultCost)
                put("transportationCost", item.transportationCost)
                put("consumablesCost", item.consumablesCost)
                put("discount", item.discount)
                put("employeeCommission", item.employeeCommission)
                put("durationMinutes", item.durationMinutes)
                put("description", item.description)
                put("isActive", item.isActive)
                put("pricingUnit", item.pricingUnit)
                put("isVisibleInApp", item.isVisibleInApp)
                put("isSelectableByPatient", item.isSelectableByPatient)
                put("lastModifiedDate", item.lastModifiedDate)
                put("uuid", item.uuid)
            })
        }

        val cashboxesArr = JSONArray()
        cashboxes.forEach { item ->
            cashboxesArr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("type", item.type)
                put("accountNumber", item.accountNumber)
                put("balance", item.balance)
                put("uuid", item.uuid)
            })
        }

        val serviceRegistrationsArr = JSONArray()
        serviceRegistrations.forEach { item ->
            serviceRegistrationsArr.put(JSONObject().apply {
                put("id", item.id)
                put("patientId", item.patientId)
                put("patientUuid", patientUuidMap[item.patientId] ?: "")
                put("serviceId", item.serviceId)
                put("serviceUuid", serviceUuidMap[item.serviceId] ?: "")
                put("employeeId", item.employeeId)
                put("employeeUuid", employeeUuidMap[item.employeeId] ?: "")
                if (item.cashboxId != null) {
                    put("cashboxId", item.cashboxId)
                    put("cashboxUuid", cashboxUuidMap[item.cashboxId] ?: "")
                }
                put("dateTime", item.dateTime)
                put("sellingPrice", item.sellingPrice)
                put("employeeCost", item.employeeCost)
                put("transportationCost", item.transportationCost)
                put("otherCosts", item.otherCosts)
                put("discount", item.discount)
                put("finalPrice", item.finalPrice)
                put("paymentMethod", item.paymentMethod)
                put("invoiceNumber", item.invoiceNumber)
                put("notes", item.notes)
                put("grossIncome", item.grossIncome)
                put("employeeCommission", item.employeeCommission)
                put("companyProfit", item.companyProfit)
                put("isPaid", item.isPaid)
                put("isDeleted", item.isDeleted)
                put("workflowStatus", item.workflowStatus)
                put("consumablesOwner", item.consumablesOwner)
                put("scheduledDate", item.scheduledDate)
                put("serviceDate", item.serviceDate)
                put("uuid", item.uuid)
            })
        }

        val expensesArr = JSONArray()
        expenses.forEach { item ->
            expensesArr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("amount", item.amount)
                put("registrationDate", item.registrationDate)
                put("paymentDate", item.paymentDate)
                put("paymentMethod", item.paymentMethod)
                put("description", item.description)
                put("submitterName", item.submitterName)
                put("receiptAttachmentPath", item.receiptAttachmentPath)
                put("isDeleted", item.isDeleted)
                put("workflowStatus", item.workflowStatus)
                put("uuid", item.uuid)
            })
        }

        val financialTransactionsArr = JSONArray()
        financialTransactions.forEach { item ->
            financialTransactionsArr.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("category", item.category)
                put("amount", item.amount)
                put("date", item.date)
                put("description", item.description)
                put("paymentMethod", item.paymentMethod)
                if (item.referenceId != null) put("referenceId", item.referenceId)
                put("isCleared", item.isCleared)
                put("origin", item.origin)
                if (item.manualReason != null) put("manualReason", item.manualReason)
                if (item.creatorName != null) put("creatorName", item.creatorName)
                put("uuid", item.uuid)
            })
        }

        val root = JSONObject().apply {
            put("version", 1)
            put("timestamp", System.currentTimeMillis())
            put("expenseCategories", expenseCategoriesArr)
            put("fixedExpenseTemplates", fixedExpenseTemplatesArr)
            put("referrals", referralsArr)
            put("commissionSettlements", commissionSettlementsArr)
            put("referralCommissions", referralCommissionsArr)
            put("patients", patientsArr)
            put("employees", employeesArr)
            put("services", servicesArr)
            put("cashboxes", cashboxesArr)
            put("serviceRegistrations", serviceRegistrationsArr)
            put("expenses", expensesArr)
            put("financialTransactions", financialTransactionsArr)
        }
        return root.toString(2)
    }

    suspend fun restoreBackupJson(dao: HamrahanDao, jsonString: String): RestoreReport {
        var importedCount = 0
        var skippedCount = 0
        var deferredCount = 0
        var failedCount = 0

        val root = JSONObject(jsonString)

        val resolvedReferralUuids = mutableMapOf<String, Int>()
        val resolvedEmployeeUuids = mutableMapOf<String, Int>()
        val resolvedPatientUuids = mutableMapOf<String, Int>()
        val resolvedServiceUuids = mutableMapOf<String, Int>()
        val resolvedCashboxUuids = mutableMapOf<String, Int>()
        val resolvedRegistrationUuids = mutableMapOf<String, Int>()
        val resolvedCategoryNames = mutableSetOf<String>()

        dao.getReferralsList().forEach { resolvedReferralUuids[it.uuid] = it.id }
        dao.getEmployeesList().forEach { resolvedEmployeeUuids[it.uuid] = it.id }
        dao.getPatientsList().forEach { resolvedPatientUuids[it.uuid] = it.id }
        dao.getServicesList().forEach { resolvedServiceUuids[it.uuid] = it.id }
        dao.getCashboxesList().forEach { resolvedCashboxUuids[it.uuid] = it.id }
        dao.getServiceRegistrationsList().forEach { resolvedRegistrationUuids[it.uuid] = it.id }
        dao.getExpenseCategoriesList().forEach { resolvedCategoryNames.add(it.name) }

        suspend fun resolveEmployeeId(uuid: String?, altId: Int?): Int? {
            if (!uuid.isNullOrBlank()) {
                resolvedEmployeeUuids[uuid]?.let { return it }
                dao.getEmployeeByUuid(uuid)?.let {
                    resolvedEmployeeUuids[uuid] = it.id
                    return it.id
                }
            }
            if (altId != null && altId > 0) {
                dao.getEmployeeById(altId)?.let { return it.id }
            }
            return null
        }

        suspend fun resolveReferralId(uuid: String?, altId: Int?): Int? {
            if (!uuid.isNullOrBlank()) {
                resolvedReferralUuids[uuid]?.let { return it }
                dao.getReferralByUuid(uuid)?.let {
                    resolvedReferralUuids[uuid] = it.id
                    return it.id
                }
            }
            if (altId != null && altId > 0) {
                dao.getReferralById(altId)?.let { return it.id }
            }
            return null
        }

        suspend fun resolvePatientId(uuid: String?, altId: Int?): Int? {
            if (!uuid.isNullOrBlank()) {
                resolvedPatientUuids[uuid]?.let { return it }
                dao.getPatientByUuid(uuid)?.let {
                    resolvedPatientUuids[uuid] = it.id
                    return it.id
                }
            }
            if (altId != null && altId > 0) {
                dao.getPatientById(altId)?.let { return it.id }
            }
            return null
        }

        suspend fun resolveServiceRegistrationId(uuid: String?, altId: Int?): Int? {
            if (!uuid.isNullOrBlank()) {
                resolvedRegistrationUuids[uuid]?.let { return it }
                dao.getServiceRegistrationByUuid(uuid)?.let {
                    resolvedRegistrationUuids[uuid] = it.id
                    return it.id
                }
            }
            if (altId != null && altId > 0) {
                dao.getServiceRegistrationById(altId)?.let { return it.id }
            }
            return null
        }

        suspend fun resolveServiceId(uuid: String?, altId: Int?): Int? {
            if (!uuid.isNullOrBlank()) {
                resolvedServiceUuids[uuid]?.let { return it }
                dao.getServiceByUuid(uuid)?.let {
                    resolvedServiceUuids[uuid] = it.id
                    return it.id
                }
            }
            if (altId != null && altId > 0) {
                dao.getServiceById(altId)?.let { return it.id }
            }
            return null
        }

        suspend fun resolveCashboxId(uuid: String?, altId: Int?): Int? {
            if (!uuid.isNullOrBlank()) {
                resolvedCashboxUuids[uuid]?.let { return it }
                dao.getCashboxByUuid(uuid)?.let {
                    resolvedCashboxUuids[uuid] = it.id
                    return it.id
                }
            }
            if (altId != null && altId > 0) {
                dao.getCashboxById(altId)?.let { return it.id }
            }
            return null
        }

        val deferredSettlements = mutableListOf<JSONObject>()
        val deferredReferralCommissions = mutableListOf<JSONObject>()
        val deferredRegistrations = mutableListOf<JSONObject>()

        // STEP 1: ExpenseCategory
        root.optJSONArray("expenseCategories")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getExpenseCategoryByUuid(uuid)
                    if (existing != null) {
                        resolvedCategoryNames.add(existing.name)
                        skippedCount++
                    } else {
                        val name = obj.getString("name")
                        val isSys = obj.optBoolean("isSystemDefault", false)
                        dao.insertExpenseCategory(ExpenseCategory(name = name, isSystemDefault = isSys, uuid = uuid))
                        resolvedCategoryNames.add(name)
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // STEP 2: FixedExpenseTemplate
        root.optJSONArray("fixedExpenseTemplates")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getFixedExpenseTemplateByUuid(uuid)
                    if (existing != null) {
                        skippedCount++
                    } else {
                        val title = obj.getString("title")
                        val monthlyAmount = obj.getDouble("monthlyAmount")
                        val paymentDay = obj.optInt("paymentDay", 1)
                        val isActive = obj.optBoolean("isActive", true)
                        val category = obj.optString("category", "هزینه ثابت")
                        val recurringInterval = obj.optString("recurringInterval", "Monthly")
                        dao.insertFixedExpenseTemplate(
                            FixedExpenseTemplate(
                                title = title,
                                monthlyAmount = monthlyAmount,
                                paymentDay = paymentDay,
                                isActive = isActive,
                                category = category,
                                recurringInterval = recurringInterval,
                                uuid = uuid
                            )
                        )
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // STEP 3: Referral
        root.optJSONArray("referrals")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getReferralByUuid(uuid)
                    if (existing != null) {
                        resolvedReferralUuids[uuid] = existing.id
                        skippedCount++
                    } else {
                        val newId = dao.insertReferral(
                            Referral(
                                name = obj.getString("name"),
                                type = obj.optString("type", "سایر"),
                                phone = obj.optString("phone", ""),
                                address = obj.optString("address", ""),
                                commissionPercentage = obj.optDouble("commissionPercentage", 0.0),
                                commissionFixedAmount = obj.optDouble("commissionFixedAmount", 0.0),
                                notes = obj.optString("notes", ""),
                                isActive = obj.optBoolean("isActive", true),
                                uuid = uuid
                            )
                        )
                        resolvedReferralUuids[uuid] = newId.toInt()
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // STEP 4: CommissionSettlement
        root.optJSONArray("commissionSettlements")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getCommissionSettlementByUuid(uuid)
                    if (existing != null) {
                        skippedCount++
                    } else {
                        val empUuid = obj.optString("employeeUuid").takeIf { it.isNotBlank() }
                        val empAltId = if (obj.has("employeeId")) obj.optInt("employeeId") else null
                        val empId = resolveEmployeeId(empUuid, empAltId)
                        if (empId != null) {
                            dao.insertCommissionSettlement(
                                CommissionSettlement(
                                    employeeId = empId,
                                    amount = obj.getDouble("amount"),
                                    settlementDate = obj.optLong("settlementDate", System.currentTimeMillis()),
                                    periodStart = obj.optLong("periodStart", 0L),
                                    periodEnd = obj.optLong("periodEnd", 0L),
                                    notes = obj.optString("notes", ""),
                                    uuid = uuid
                                )
                            )
                            importedCount++
                        } else {
                            deferredSettlements.add(obj)
                            deferredCount++
                        }
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // STEP 5: ReferralCommission
        root.optJSONArray("referralCommissions")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getReferralCommissionByUuid(uuid)
                    if (existing != null) {
                        skippedCount++
                    } else {
                        val refUuid = obj.optString("referralUuid").takeIf { it.isNotBlank() }
                        val refAltId = if (obj.has("referralId")) obj.optInt("referralId") else null
                        val refId = resolveReferralId(refUuid, refAltId)

                        val patUuid = obj.optString("patientUuid").takeIf { it.isNotBlank() }
                        val patAltId = if (obj.has("patientId")) obj.optInt("patientId") else null
                        val patId = resolvePatientId(patUuid, patAltId)

                        val regUuid = obj.optString("serviceRegistrationUuid").takeIf { it.isNotBlank() }
                        val regAltId = if (obj.has("serviceRegistrationId")) obj.optInt("serviceRegistrationId") else null
                        val regId = resolveServiceRegistrationId(regUuid, regAltId)

                        if (refId != null && patId != null && regId != null) {
                            dao.insertReferralCommission(
                                ReferralCommission(
                                    referralId = refId,
                                    patientId = patId,
                                    serviceRegistrationId = regId,
                                    serviceName = obj.optString("serviceName", ""),
                                    serviceAmount = obj.optDouble("serviceAmount", 0.0),
                                    commissionPercentage = obj.optDouble("commissionPercentage", 0.0),
                                    commissionAmount = obj.optDouble("commissionAmount", 0.0),
                                    date = obj.optLong("date", System.currentTimeMillis()),
                                    status = obj.optString("status", "در انتظار پرداخت"),
                                    paymentDate = if (obj.has("paymentDate") && !obj.isNull("paymentDate")) obj.getLong("paymentDate") else null,
                                    documentNumber = obj.optString("documentNumber", ""),
                                    notes = obj.optString("notes", ""),
                                    uuid = uuid
                                )
                            )
                            importedCount++
                        } else {
                            deferredReferralCommissions.add(obj)
                            deferredCount++
                        }
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // STEP 6: Existing financial / core entities
        // 6a: Employees
        root.optJSONArray("employees")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getEmployeeByUuid(uuid)
                    if (existing != null) {
                        resolvedEmployeeUuids[uuid] = existing.id
                        skippedCount++
                    } else {
                        val newId = dao.insertEmployee(
                            Employee(
                                fullName = obj.getString("fullName"),
                                nationalId = obj.optString("nationalId", ""),
                                phone = obj.optString("phone", ""),
                                profession = obj.optString("profession", ""),
                                position = obj.optString("position", ""),
                                skill = obj.optString("skill", ""),
                                employmentType = obj.optString("employmentType", "تمام وقت"),
                                commissionModel = obj.optString("commissionModel", "درصدی"),
                                commissionValue = obj.optDouble("commissionValue", 0.0),
                                bankInfo = obj.optString("bankInfo", ""),
                                status = obj.optString("status", "فعال"),
                                startDate = obj.optLong("startDate", System.currentTimeMillis()),
                                notes = obj.optString("notes", ""),
                                uuid = uuid
                            )
                        )
                        resolvedEmployeeUuids[uuid] = newId.toInt()
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // 6b: Patients
        root.optJSONArray("patients")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getPatientByUuid(uuid)
                    if (existing != null) {
                        resolvedPatientUuids[uuid] = existing.id
                        skippedCount++
                    } else {
                        val refUuid = obj.optString("referralUuid").takeIf { it.isNotBlank() }
                        val refAltId = if (obj.has("referralId")) obj.optInt("referralId") else null
                        val refId = resolveReferralId(refUuid, refAltId)

                        val newId = dao.insertPatient(
                            Patient(
                                fullName = obj.getString("fullName"),
                                gender = obj.optString("gender", "مرد"),
                                age = obj.optInt("age", 0),
                                phone = obj.optString("phone", ""),
                                address = obj.optString("address", ""),
                                referralSource = obj.optString("referralSource", ""),
                                referralId = refId,
                                status = obj.optString("status", "فعال"),
                                registrationDate = obj.optLong("registrationDate", System.currentTimeMillis()),
                                notes = obj.optString("notes", ""),
                                tags = obj.optString("tags", ""),
                                uuid = uuid
                            )
                        )
                        resolvedPatientUuids[uuid] = newId.toInt()
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // 6c: Services
        root.optJSONArray("services")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getServiceByUuid(uuid)
                    if (existing != null) {
                        resolvedServiceUuids[uuid] = existing.id
                        skippedCount++
                    } else {
                        val newId = dao.insertService(
                            Service(
                                officialCode = obj.optString("officialCode", ""),
                                officialName = obj.optString("officialName", ""),
                                name = obj.getString("name"),
                                category = obj.optString("category", "عمومی"),
                                officialTariff = obj.optDouble("officialTariff", 0.0),
                                sellingPrice = obj.optDouble("sellingPrice", 0.0),
                                defaultCost = obj.optDouble("defaultCost", 0.0),
                                transportationCost = obj.optDouble("transportationCost", 0.0),
                                consumablesCost = obj.optDouble("consumablesCost", 0.0),
                                discount = obj.optDouble("discount", 0.0),
                                employeeCommission = obj.optDouble("employeeCommission", 0.0),
                                durationMinutes = obj.optInt("durationMinutes", 30),
                                description = obj.optString("description", ""),
                                isActive = obj.optBoolean("isActive", true),
                                pricingUnit = obj.optString("pricingUnit", "بازدید"),
                                isVisibleInApp = obj.optBoolean("isVisibleInApp", true),
                                isSelectableByPatient = obj.optBoolean("isSelectableByPatient", true),
                                lastModifiedDate = obj.optLong("lastModifiedDate", System.currentTimeMillis()),
                                uuid = uuid
                            )
                        )
                        resolvedServiceUuids[uuid] = newId.toInt()
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // 6d: Cashboxes
        root.optJSONArray("cashboxes")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getCashboxByUuid(uuid)
                    if (existing != null) {
                        resolvedCashboxUuids[uuid] = existing.id
                        skippedCount++
                    } else {
                        val newId = dao.insertCashbox(
                            Cashbox(
                                name = obj.getString("name"),
                                type = obj.optString("type", "صندوق"),
                                accountNumber = obj.optString("accountNumber", ""),
                                balance = obj.optDouble("balance", 0.0),
                                uuid = uuid
                            )
                        )
                        resolvedCashboxUuids[uuid] = newId.toInt()
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // 6e: ServiceRegistrations
        root.optJSONArray("serviceRegistrations")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getServiceRegistrationByUuid(uuid)
                    if (existing != null) {
                        resolvedRegistrationUuids[uuid] = existing.id
                        skippedCount++
                    } else {
                        val patUuid = obj.optString("patientUuid").takeIf { it.isNotBlank() }
                        val patAltId = if (obj.has("patientId")) obj.optInt("patientId") else null
                        val patId = resolvePatientId(patUuid, patAltId)

                        val empUuid = obj.optString("employeeUuid").takeIf { it.isNotBlank() }
                        val empAltId = if (obj.has("employeeId")) obj.optInt("employeeId") else null
                        val empId = resolveEmployeeId(empUuid, empAltId)

                        val svcUuid = obj.optString("serviceUuid").takeIf { it.isNotBlank() }
                        val svcAltId = if (obj.has("serviceId")) obj.optInt("serviceId") else null
                        val svcId = resolveServiceId(svcUuid, svcAltId)

                        val cbUuid = obj.optString("cashboxUuid").takeIf { it.isNotBlank() }
                        val cbAltId = if (obj.has("cashboxId")) obj.optInt("cashboxId") else null
                        val cbId = resolveCashboxId(cbUuid, cbAltId)

                        if (patId != null && empId != null && svcId != null) {
                            val dateTime = obj.optLong("dateTime", System.currentTimeMillis())
                            val sellingPrice = obj.optDouble("sellingPrice", 0.0)
                            val employeeCost = obj.optDouble("employeeCost", 0.0)
                            val transportationCost = obj.optDouble("transportationCost", 0.0)
                            val otherCosts = obj.optDouble("otherCosts", 0.0)
                            val discount = obj.optDouble("discount", 0.0)
                            val finalPrice = obj.optDouble("finalPrice", sellingPrice + otherCosts + transportationCost - discount)

                            val newId = dao.insertServiceRegistration(
                                ServiceRegistration(
                                    patientId = patId,
                                    serviceId = svcId,
                                    employeeId = empId,
                                    dateTime = dateTime,
                                    sellingPrice = sellingPrice,
                                    employeeCost = employeeCost,
                                    transportationCost = transportationCost,
                                    otherCosts = otherCosts,
                                    discount = discount,
                                    finalPrice = finalPrice,
                                    paymentMethod = obj.optString("paymentMethod", "نقدی"),
                                    invoiceNumber = obj.optString("invoiceNumber", ""),
                                    notes = obj.optString("notes", ""),
                                    grossIncome = obj.optDouble("grossIncome", finalPrice),
                                    employeeCommission = obj.optDouble("employeeCommission", employeeCost),
                                    companyProfit = obj.optDouble("companyProfit", finalPrice - employeeCost - otherCosts),
                                    isPaid = obj.optBoolean("isPaid", true),
                                    isDeleted = obj.optBoolean("isDeleted", false),
                                    workflowStatus = obj.optString("workflowStatus", "Submitted"),
                                    consumablesOwner = obj.optString("consumablesOwner", "Nurse"),
                                    cashboxId = cbId,
                                    scheduledDate = obj.optLong("scheduledDate", dateTime),
                                    serviceDate = obj.optLong("serviceDate", dateTime),
                                    uuid = uuid
                                )
                            )
                            resolvedRegistrationUuids[uuid] = newId.toInt()
                            importedCount++
                        } else {
                            deferredRegistrations.add(obj)
                            deferredCount++
                        }
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // 6f: Expenses
        root.optJSONArray("expenses")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getExpenseByUuid(uuid)
                    if (existing != null) {
                        skippedCount++
                    } else {
                        dao.insertExpense(
                            Expense(
                                title = obj.getString("title"),
                                category = obj.optString("category", "هزینه متغیر"),
                                amount = obj.getDouble("amount"),
                                registrationDate = obj.optLong("registrationDate", System.currentTimeMillis()),
                                paymentDate = obj.optLong("paymentDate", System.currentTimeMillis()),
                                paymentMethod = obj.optString("paymentMethod", "نقدی"),
                                description = obj.optString("description", ""),
                                submitterName = obj.optString("submitterName", ""),
                                receiptAttachmentPath = obj.optString("receiptAttachmentPath", ""),
                                isDeleted = obj.optBoolean("isDeleted", false),
                                workflowStatus = obj.optString("workflowStatus", "Submitted"),
                                uuid = uuid
                            )
                        )
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // 6g: FinancialTransactions
        root.optJSONArray("financialTransactions")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                    val existing = dao.getFinancialTransactionByUuid(uuid)
                    if (existing != null) {
                        skippedCount++
                    } else {
                        val refId = if (obj.has("referenceId") && !obj.isNull("referenceId")) obj.optInt("referenceId") else null
                        dao.insertFinancialTransaction(
                            FinancialTransaction(
                                type = obj.optString("type", "درآمد"),
                                category = obj.optString("category", "سایر"),
                                amount = obj.getDouble("amount"),
                                date = obj.optLong("date", System.currentTimeMillis()),
                                description = obj.optString("description", ""),
                                paymentMethod = obj.optString("paymentMethod", "نقدی"),
                                referenceId = refId,
                                isCleared = obj.optBoolean("isCleared", true),
                                origin = obj.optString("origin", "Manual Entry"),
                                manualReason = if (obj.has("manualReason")) obj.optString("manualReason") else null,
                                creatorName = if (obj.has("creatorName")) obj.optString("creatorName") else null,
                                uuid = uuid
                            )
                        )
                        importedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
        }

        // STEP 7: SECOND PASS OVER DEFERRED ITEMS
        for (obj in deferredRegistrations) {
            try {
                val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                val patUuid = obj.optString("patientUuid").takeIf { it.isNotBlank() }
                val patAltId = if (obj.has("patientId")) obj.optInt("patientId") else null
                val patId = resolvePatientId(patUuid, patAltId)

                val empUuid = obj.optString("employeeUuid").takeIf { it.isNotBlank() }
                val empAltId = if (obj.has("employeeId")) obj.optInt("employeeId") else null
                val empId = resolveEmployeeId(empUuid, empAltId)

                val svcUuid = obj.optString("serviceUuid").takeIf { it.isNotBlank() }
                val svcAltId = if (obj.has("serviceId")) obj.optInt("serviceId") else null
                val svcId = resolveServiceId(svcUuid, svcAltId)

                val cbUuid = obj.optString("cashboxUuid").takeIf { it.isNotBlank() }
                val cbAltId = if (obj.has("cashboxId")) obj.optInt("cashboxId") else null
                val cbId = resolveCashboxId(cbUuid, cbAltId)

                if (patId != null && empId != null && svcId != null) {
                    val dateTime = obj.optLong("dateTime", System.currentTimeMillis())
                    val sellingPrice = obj.optDouble("sellingPrice", 0.0)
                    val employeeCost = obj.optDouble("employeeCost", 0.0)
                    val transportationCost = obj.optDouble("transportationCost", 0.0)
                    val otherCosts = obj.optDouble("otherCosts", 0.0)
                    val discount = obj.optDouble("discount", 0.0)
                    val finalPrice = obj.optDouble("finalPrice", sellingPrice + otherCosts + transportationCost - discount)

                    val newId = dao.insertServiceRegistration(
                        ServiceRegistration(
                            patientId = patId,
                            serviceId = svcId,
                            employeeId = empId,
                            dateTime = dateTime,
                            sellingPrice = sellingPrice,
                            employeeCost = employeeCost,
                            transportationCost = transportationCost,
                            otherCosts = otherCosts,
                            discount = discount,
                            finalPrice = finalPrice,
                            paymentMethod = obj.optString("paymentMethod", "نقدی"),
                            invoiceNumber = obj.optString("invoiceNumber", ""),
                            notes = obj.optString("notes", ""),
                            grossIncome = obj.optDouble("grossIncome", finalPrice),
                            employeeCommission = obj.optDouble("employeeCommission", employeeCost),
                            companyProfit = obj.optDouble("companyProfit", finalPrice - employeeCost - otherCosts),
                            isPaid = obj.optBoolean("isPaid", true),
                            isDeleted = obj.optBoolean("isDeleted", false),
                            workflowStatus = obj.optString("workflowStatus", "Submitted"),
                            consumablesOwner = obj.optString("consumablesOwner", "Nurse"),
                            cashboxId = cbId,
                            scheduledDate = obj.optLong("scheduledDate", dateTime),
                            serviceDate = obj.optLong("serviceDate", dateTime),
                            uuid = uuid
                        )
                    )
                    resolvedRegistrationUuids[uuid] = newId.toInt()
                    importedCount++
                    deferredCount--
                }
            } catch (e: Exception) {
                deferredCount--
                failedCount++
            }
        }

        for (obj in deferredSettlements) {
            try {
                val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                val empUuid = obj.optString("employeeUuid").takeIf { it.isNotBlank() }
                val empAltId = if (obj.has("employeeId")) obj.optInt("employeeId") else null
                val empId = resolveEmployeeId(empUuid, empAltId)

                if (empId != null) {
                    dao.insertCommissionSettlement(
                        CommissionSettlement(
                            employeeId = empId,
                            amount = obj.getDouble("amount"),
                            settlementDate = obj.optLong("settlementDate", System.currentTimeMillis()),
                            periodStart = obj.optLong("periodStart", 0L),
                            periodEnd = obj.optLong("periodEnd", 0L),
                            notes = obj.optString("notes", ""),
                            uuid = uuid
                        )
                    )
                    importedCount++
                    deferredCount--
                }
            } catch (e: Exception) {
                deferredCount--
                failedCount++
            }
        }

        for (obj in deferredReferralCommissions) {
            try {
                val uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() }
                val refUuid = obj.optString("referralUuid").takeIf { it.isNotBlank() }
                val refAltId = if (obj.has("referralId")) obj.optInt("referralId") else null
                val refId = resolveReferralId(refUuid, refAltId)

                val patUuid = obj.optString("patientUuid").takeIf { it.isNotBlank() }
                val patAltId = if (obj.has("patientId")) obj.optInt("patientId") else null
                val patId = resolvePatientId(patUuid, patAltId)

                val regUuid = obj.optString("serviceRegistrationUuid").takeIf { it.isNotBlank() }
                val regAltId = if (obj.has("serviceRegistrationId")) obj.optInt("serviceRegistrationId") else null
                val regId = resolveServiceRegistrationId(regUuid, regAltId)

                if (refId != null && patId != null && regId != null) {
                    dao.insertReferralCommission(
                        ReferralCommission(
                            referralId = refId,
                            patientId = patId,
                            serviceRegistrationId = regId,
                            serviceName = obj.optString("serviceName", ""),
                            serviceAmount = obj.optDouble("serviceAmount", 0.0),
                            commissionPercentage = obj.optDouble("commissionPercentage", 0.0),
                            commissionAmount = obj.optDouble("commissionAmount", 0.0),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            status = obj.optString("status", "در انتظار پرداخت"),
                            paymentDate = if (obj.has("paymentDate") && !obj.isNull("paymentDate")) obj.getLong("paymentDate") else null,
                            documentNumber = obj.optString("documentNumber", ""),
                            notes = obj.optString("notes", ""),
                            uuid = uuid
                        )
                    )
                    importedCount++
                    deferredCount--
                }
            } catch (e: Exception) {
                deferredCount--
                failedCount++
            }
        }

        return RestoreReport(
            imported = importedCount,
            skipped = skippedCount,
            deferred = deferredCount,
            failed = failedCount
        )
    }
}

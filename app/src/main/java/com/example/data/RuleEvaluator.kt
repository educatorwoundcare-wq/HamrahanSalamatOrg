package com.example.data

import java.util.Calendar

class RuleEvaluator(private val repository: HamrahanRepository) {

    suspend fun evaluateRules(): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val now = System.currentTimeMillis()

        // Fetch primary lists for evaluation directly from the persistent database state
        val employees = try { repository.dao.getEmployeesList() } catch (e: Exception) { emptyList() }
        val patients = try { repository.dao.getPatientsList() } catch (e: Exception) { emptyList() }
        val registrations = try { repository.dao.getServiceRegistrationsList() } catch (e: Exception) { emptyList() }
        val expenses = try { repository.dao.getExpensesList() } catch (e: Exception) { emptyList() }
        val cashboxes = try { repository.dao.getCashboxesList() } catch (e: Exception) { emptyList() }
        val serviceSchedules = try { repository.dao.getServiceSchedulesList() } catch (e: Exception) { emptyList() }
        val settlements = try { repository.dao.getCommissionSettlementsList() } catch (e: Exception) { emptyList() }

        // Get calendar boundaries for "today"
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfToday = calendar.timeInMillis

        // --- FINANCIAL ALERTS ---

        // 1. Unpaid Commissions
        employees.forEach { emp ->
            val totalEarned = registrations.filter { it.employeeId == emp.id && !it.isDeleted && it.workflowStatus == "Approved" }.sumOf { it.employeeCommission }
            val totalSettled = settlements.filter { it.employeeId == emp.id }.sumOf { it.amount }
            val unpaid = totalEarned - totalSettled
            if (unpaid > 0.0) {
                alerts.add(
                    Alert(
                        title = "💰 کارمزد پرداخت‌نشده همکار ${emp.fullName}",
                        description = "مبلغ ${unpaid.toLong()} تومان کارمزد همکار تسویه نشده است.",
                        type = "unpaid_commission",
                        relatedScreen = "commissions?id=${emp.id}&tab=ledger&alertType=unpaid_commission",
                        alertType = "unpaid_commission",
                        entityId = "emp_${emp.id}",
                        status = "PENDING",
                        createdAt = now
                    )
                )
            }
        }

        // 2. Commissions Waiting for Approval
        val pendingCommissions = registrations.filter { !it.isDeleted && (it.workflowStatus == "Submitted" || it.workflowStatus == "Pending") && it.employeeCommission > 0.0 }
        pendingCommissions.forEach { reg ->
            val empName = employees.find { it.id == reg.employeeId }?.fullName ?: "همکار"
            alerts.add(
                Alert(
                    title = "⚠️ کارمزد در انتظار تایید همکار $empName",
                    description = "کارمزد خدمت به مبلغ ${reg.employeeCommission.toLong()} تومان در انتظار بررسی و تایید است.",
                    type = "commission_pending_approval",
                    relatedScreen = "commissions?id=${reg.employeeId}&tab=ledger&alertType=commission_pending_approval",
                    alertType = "commission_pending_approval",
                    entityId = "reg_${reg.id}",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        // 3. Overdue Expenses
        val pendingExpenses = expenses.filter { !it.isDeleted && (it.workflowStatus == "Submitted" || it.workflowStatus == "Pending") }
        pendingExpenses.forEach { exp ->
            alerts.add(
                Alert(
                    title = "💳 سند هزینه ${exp.title} در انتظار تایید",
                    description = "سند هزینه جدید به مبلغ ${exp.amount.toLong()} تومان نیاز به بررسی و تایید دارد.",
                    type = "overdue_expenses",
                    relatedScreen = "expenses?id=${exp.id}&tab=list&alertType=overdue_expenses",
                    alertType = "overdue_expenses",
                    entityId = "exp_${exp.id}",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        // 4. Unpaid Invoices
        val unpaidInvoicesList = registrations.filter { !it.isDeleted && !it.isPaid }
        unpaidInvoicesList.forEach { reg ->
            val ptName = patients.find { it.id == reg.patientId }?.fullName ?: "بیمار"
            alerts.add(
                Alert(
                    title = "🔴 فاکتور پرداخت‌نشده بیمار $ptName",
                    description = "فاکتور شماره ${reg.invoiceNumber} به مبلغ ${reg.finalPrice.toLong()} تومان پرداخت نشده است.",
                    type = "unpaid_invoices",
                    relatedScreen = "accounting?id=${reg.id}&tab=transactions&alertType=unpaid_invoices",
                    alertType = "unpaid_invoices",
                    entityId = "invoice_${reg.id}",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        // 5. Payment Deadlines
        val paymentDeadlineMs = 3 * 24 * 60 * 60 * 1000L
        val overduePaymentInvoices = unpaidInvoicesList.filter { (now - it.dateTime) > paymentDeadlineMs }
        overduePaymentInvoices.forEach { reg ->
            val ptName = patients.find { it.id == reg.patientId }?.fullName ?: "بیمار"
            alerts.add(
                Alert(
                    title = "🚨 معوقه فاکتور بیمار $ptName از موعد پرداخت",
                    description = "از موعد پرداخت فاکتور شماره ${reg.invoiceNumber} به مبلغ ${reg.finalPrice.toLong()} بیش از ۳ روز گذشته است.",
                    type = "payment_deadlines",
                    relatedScreen = "accounting?id=${reg.id}&tab=transactions&alertType=payment_deadlines",
                    alertType = "payment_deadlines",
                    entityId = "deadline_${reg.id}",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        // 6. Receivables
        val totalReceivables = unpaidInvoicesList.sumOf { it.finalPrice }
        if (totalReceivables > 0.0) {
            alerts.add(
                Alert(
                    title = "📊 مجموع مطالبات معوقه مرکز",
                    description = "مجموع فاکتورهای پرداخت‌نشده بیمارها مبلغ ${totalReceivables.toLong()} تومان می‌باشد.",
                    type = "receivables",
                    relatedScreen = "accounting?tab=reports&alertType=receivables",
                    alertType = "receivables",
                    entityId = "receivables_total",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        // 7. Negative Cash Balance
        cashboxes.forEach { box ->
            if (box.balance < 0.0) {
                alerts.add(
                    Alert(
                        title = "⚠️ موجودی منفی صندوق ${box.name}",
                        description = "موجودی این صندوق/حساب به مبلغ ${box.balance.toLong()} تومان زیر صفر است.",
                        type = "negative_cash_balance",
                        relatedScreen = "accounting?tab=cashboxes&alertType=negative_cash_balance",
                        alertType = "negative_cash_balance",
                        entityId = "cashbox_${box.id}",
                        status = "PENDING",
                        createdAt = now
                    )
                )
            }
        }

        // --- DAILY OPERATIONS ALERTS ---

        // 1. Today's Nursing Services
        val todaysSchedules = serviceSchedules.filter { it.scheduledDate in startOfToday..endOfToday && (it.status == "Scheduled" || it.status == "Pending") }
        todaysSchedules.forEach { sched ->
            val empName = employees.find { it.id == sched.employeeId }?.fullName ?: "همکار"
            alerts.add(
                Alert(
                    title = "🩺 خدمت پرستاری امروز همکار $empName",
                    description = "مأموریت مراقبت بالینی برای امروز برنامه‌ریزی شده است. وضعیت مأموریت: ${sched.status}.",
                    type = "scheduled_service_today",
                    relatedScreen = "patients?id=${sched.registrationId}&tab=schedule&alertType=scheduled_service_today",
                    alertType = "scheduled_service_today",
                    entityId = "sched_${sched.id}",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        // 2. Today's Patient Visits
        val todaysRegistrations = registrations.filter { !it.isDeleted && it.scheduledDate in startOfToday..endOfToday && it.workflowStatus == "Scheduled" }
        todaysRegistrations.forEach { reg ->
            val ptName = patients.find { it.id == reg.patientId }?.fullName ?: "بیمار"
            alerts.add(
                Alert(
                    title = "📅 ویزیت بیمار $ptName برای امروز",
                    description = "ثبت خدمت بیمار برای امروز برنامه‌ریزی شده است.",
                    type = "today_patient_visits",
                    relatedScreen = "patients?id=${reg.patientId}&tab=schedule&alertType=today_patient_visits",
                    alertType = "today_patient_visits",
                    entityId = "visit_${reg.id}",
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        return alerts
    }
}

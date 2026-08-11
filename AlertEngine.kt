package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first

class AlertEngine(
    private val context: Context,
    private val repository: HamrahanRepository
) {
    private val ruleEvaluator = RuleEvaluator(repository)

    suspend fun runDiagnosticAndSync(): Int {
        return try {
            Log.i("AlertEngine", "Initiating Rule-Based Alert Engine scan...")
            
            // 1. Evaluate current active rules
            val evaluatedAlerts = ruleEvaluator.evaluateRules()
            Log.i("AlertEngine", "Scan completed. Found ${evaluatedAlerts.size} active issues across the system.")

            // 2. Load existing alerts from database
            val existingAlerts = repository.allAlerts.first()
            val activeExistingAlerts = existingAlerts.filter { !it.isDismissed }

            val newAlertsToSave = mutableListOf<Alert>()
            var newlyTriggeredCount = 0

            // Keep track of which existing alerts are still valid
            val stillValidAlertUuids = mutableSetOf<String>()

            evaluatedAlerts.forEach { eval ->
                // Check if this issue is already captured in active existing alerts
                val matchingActive = activeExistingAlerts.find { exist ->
                    (exist.entityId.isNotEmpty() && exist.entityId == eval.entityId) ||
                    (exist.entityId.isEmpty() && exist.type == eval.type && exist.relatedScreen == eval.relatedScreen)
                }

                // Check if this issue was ALREADY dismissed or completed in the past
                val wasResolvedOrDismissed = existingAlerts.any { exist ->
                    exist.isDismissed && (
                        (exist.entityId.isNotEmpty() && exist.entityId == eval.entityId) ||
                        (exist.entityId.isEmpty() && exist.type == eval.type && exist.relatedScreen == eval.relatedScreen)
                    )
                }

                if (matchingActive != null) {
                    // It is still valid
                    stillValidAlertUuids.add(matchingActive.uuid)
                } else if (!wasResolvedOrDismissed) {
                    // Brand new alert
                    newAlertsToSave.add(eval)
                    newlyTriggeredCount++

                    // Trigger system notification
                    NotificationGenerator.triggerNotification(
                        context = context,
                        id = (eval.type + (eval.relatedScreen ?: "")).hashCode(),
                        title = eval.title,
                        text = eval.description,
                        screen = eval.relatedScreen
                    )
                    Log.i("AlertEngine", "New alert triggered: '${eval.title}'")
                }
            }

            // 3. Sync: Dismiss existing active alerts that are NO LONGER valid/triggered
            // This auto-clears resolved issues (e.g., invoice paid, commission approved)
            val allowedTypes = setOf(
                "unpaid_commission", "commission_pending_approval", "overdue_expenses",
                "unpaid_invoices", "payment_deadlines", "receivables",
                "negative_cash_balance", "scheduled_service_today", "today_patient_visits"
            )

            activeExistingAlerts.forEach { exist ->
                if (exist.type in allowedTypes && exist.uuid !in stillValidAlertUuids) {
                    // Mark as dismissed and read in the database
                    repository.updateAlert(exist.copy(isDismissed = true, isRead = true))
                    Log.i("AlertEngine", "Auto-resolved and dismissed stale alert: '${exist.title}'")
                }
            }

            // 4. Save new active alerts
            if (newAlertsToSave.isNotEmpty()) {
                repository.insertAlerts(newAlertsToSave)
                Log.i("AlertEngine", "Stored ${newAlertsToSave.size} new active alerts.")
            }

            newlyTriggeredCount
        } catch (e: Exception) {
            Log.e("AlertEngine", "Error executing alert sync loop: ${e.localizedMessage}", e)
            0
        }
    }
}

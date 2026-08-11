package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Dashboard

@Serializable
data object PatientList

@Serializable
data object PersonnelList

@Serializable
data object ServiceCatalog

@Serializable
data object ServiceRegistration

@Serializable
data object FinancialLedgers

@Serializable
data object Expenses

@Serializable
data object Commissions

@Serializable
data object Reports

@Serializable
data object Search

@Serializable
data object Settings

@Serializable
data object CompanyProfile

@Serializable
data object SyncManagement

@Serializable
data class EmployeeLedger(val employeeId: Int)

@Serializable
data class ServiceRegistrationDetail(val registrationId: Int)


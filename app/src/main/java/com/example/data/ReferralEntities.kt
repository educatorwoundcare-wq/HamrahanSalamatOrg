package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "referrals")
data class Referral(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "پزشک" / "مرکز درمانی" / "بیمارستان" / "درمانگاه" / "آزمایشگاه" / "شرکت" / "شخص حقیقی" / "سایر"
    val phone: String = "",
    val address: String = "",
    val commissionPercentage: Double = 0.0,
    val commissionFixedAmount: Double = 0.0,
    val notes: String = "",
    val isActive: Boolean = true,
    val uuid: String = UUID.randomUUID().toString()
)

@Entity(tableName = "referral_commissions")
data class ReferralCommission(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val referralId: Int,
    val patientId: Int,
    val serviceRegistrationId: Int,
    val serviceName: String,
    val serviceAmount: Double,
    val commissionPercentage: Double,
    val commissionAmount: Double,
    val date: Long = System.currentTimeMillis(),
    val status: String, // "در انتظار پرداخت" / "پرداخت شده" / "تسویه شده"
    val paymentDate: Long? = null,
    val documentNumber: String = "",
    val notes: String = "",
    val uuid: String = UUID.randomUUID().toString()
)

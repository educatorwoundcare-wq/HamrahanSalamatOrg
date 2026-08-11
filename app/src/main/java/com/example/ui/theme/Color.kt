package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Hamrahan Salamat (همراهان سلامت) Official Brand Color Palette - Light
val HamrahanPrimary = Color(0xFF0F766E)          // Deep Teal
val HamrahanSecondary = Color(0xFF14B8A6)        // Turquoise
val HamrahanAccent = Color(0xFF0284C7)           // Medical Sky/Ocean Blue
val HamrahanBackground = Color(0xFFF8FAFC)       // Calm slate medical background
val HamrahanSurface = Color(0xFFFFFFFF)          // Clean crisp white
val HamrahanSurfaceVariant = Color(0xFFF1F5F9)   // Light slate variant
val HamrahanOutline = Color(0xFFE2E8F0)          // Soft border line

// Standard Semantic Feedback Colors
val HamrahanSuccess = Color(0xFF16A34A)          // Enterprise Positive Green
val HamrahanWarning = Color(0xFFD97706)          // Caution Gold/Amber
val HamrahanDanger = Color(0xFFDC2626)           // Warning/Danger Red
val HamrahanInfo = Color(0xFF0284C7)             // Informational Blue

// Sync Status Colors
val SyncSyncedColor = Color(0xFF16A34A)          // Synced Green
val SyncSyncingColor = Color(0xFF0284C7)         // Syncing Blue
val SyncPendingColor = Color(0xFFD97706)         // Pending Amber
val SyncErrorColor = Color(0xFFDC2626)           // Sync Error Red

// Financial Positive / Negative Indicators
val FinancialPositive = Color(0xFF16A34A)        // Income / Profit Green
val FinancialNegative = Color(0xFFDC2626)        // Expense / Loss Red
val FinancialNeutral = Color(0xFF64748B)         // Slate Neutral Gray

// Hamrahan Salamat Official Brand Color Palette - Dark (Low-fatigue medical dark)
val HamrahanPrimaryDark = Color(0xFF14B8A6)      // Bright turquoise
val HamrahanSecondaryDark = Color(0xFF0F766E)    // Safe deep teal
val HamrahanBackgroundDark = Color(0xFF0B1E21)   // Soft deep teal dark grey
val HamrahanSurfaceDark = Color(0xFF112A2D)      // Soft deep slate teal
val HamrahanSurfaceVariantDark = Color(0xFF163C3E)
val HamrahanOutlineDark = Color(0xFF374151)      // Cool grey borders

@Immutable
data class EnterpriseColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val background: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val syncSynced: Color,
    val syncSyncing: Color,
    val syncPending: Color,
    val syncError: Color,
    val financialPositive: Color,
    val financialNegative: Color,
    val financialNeutral: Color,
    val border: Color
)

val LightEnterpriseColors = EnterpriseColors(
    primary = HamrahanPrimary,
    secondary = HamrahanSecondary,
    accent = HamrahanAccent,
    surface = HamrahanSurface,
    surfaceVariant = HamrahanSurfaceVariant,
    background = HamrahanBackground,
    success = HamrahanSuccess,
    warning = HamrahanWarning,
    error = HamrahanDanger,
    info = HamrahanInfo,
    syncSynced = SyncSyncedColor,
    syncSyncing = SyncSyncingColor,
    syncPending = SyncPendingColor,
    syncError = SyncErrorColor,
    financialPositive = FinancialPositive,
    financialNegative = FinancialNegative,
    financialNeutral = FinancialNeutral,
    border = HamrahanOutline
)

val DarkEnterpriseColors = EnterpriseColors(
    primary = HamrahanPrimaryDark,
    secondary = HamrahanSecondaryDark,
    accent = Color(0xFF38BDF8),
    surface = HamrahanSurfaceDark,
    surfaceVariant = HamrahanSurfaceVariantDark,
    background = HamrahanBackgroundDark,
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFF87171),
    info = Color(0xFF38BDF8),
    syncSynced = Color(0xFF4ADE80),
    syncSyncing = Color(0xFF38BDF8),
    syncPending = Color(0xFFFBBF24),
    syncError = Color(0xFFF87171),
    financialPositive = Color(0xFF4ADE80),
    financialNegative = Color(0xFFF87171),
    financialNeutral = Color(0xFF94A3B8),
    border = HamrahanOutlineDark
)


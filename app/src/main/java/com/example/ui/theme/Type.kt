package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Enterprise Typography System
object EnterpriseTypographyStyles {
    val screenTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )

    val sectionHeader = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )

    val cardTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )

    val kpiValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    )

    val bodyText = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )

    val supportingText = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )

    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
}

val Typography = Typography(
    headlineMedium = EnterpriseTypographyStyles.screenTitle,
    titleLarge = EnterpriseTypographyStyles.screenTitle,
    titleMedium = EnterpriseTypographyStyles.sectionHeader,
    titleSmall = EnterpriseTypographyStyles.cardTitle,
    displaySmall = EnterpriseTypographyStyles.kpiValue,
    bodyLarge = EnterpriseTypographyStyles.bodyText,
    bodyMedium = EnterpriseTypographyStyles.bodyText,
    bodySmall = EnterpriseTypographyStyles.supportingText,
    labelLarge = EnterpriseTypographyStyles.label,
    labelMedium = EnterpriseTypographyStyles.label,
    labelSmall = EnterpriseTypographyStyles.supportingText
)


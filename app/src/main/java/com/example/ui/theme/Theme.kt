package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalEnterpriseColors = staticCompositionLocalOf { LightEnterpriseColors }

private val DarkColorScheme = darkColorScheme(
    primary = HamrahanPrimaryDark,
    onPrimary = Color(0xFF0B1E21),
    primaryContainer = HamrahanSurfaceDark,
    onPrimaryContainer = HamrahanPrimaryDark,
    secondary = HamrahanSecondaryDark,
    onSecondary = HamrahanBackgroundDark,
    secondaryContainer = Color(0xFF0F766E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = HamrahanAccent,
    onTertiary = Color(0xFF0B1E21),
    background = HamrahanBackgroundDark,
    onBackground = Color(0xFFE2E8F0),
    surface = HamrahanSurfaceDark,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = HamrahanSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = HamrahanOutlineDark,
    outlineVariant = Color(0xFF374151)
)

private val LightColorScheme = lightColorScheme(
    primary = HamrahanPrimary,
    onPrimary = HamrahanSurface,
    primaryContainer = Color(0xFFE0F2F1), // Soft Teal Container
    onPrimaryContainer = HamrahanPrimary,
    secondary = HamrahanSecondary,
    onSecondary = HamrahanSurface,
    secondaryContainer = Color(0xFFCCFBF1), // Soft Turquoise Container
    onSecondaryContainer = HamrahanPrimary,
    tertiary = HamrahanAccent,
    onTertiary = Color(0xFF0F172A),
    background = HamrahanBackground,
    onBackground = Color(0xFF0F172A),
    surface = HamrahanSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = HamrahanSurfaceVariant,
    onSurfaceVariant = Color(0xFF0F766E),
    outline = HamrahanOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to keep our beautiful Emerald branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val enterpriseColors = if (darkTheme) DarkEnterpriseColors else LightEnterpriseColors

    CompositionLocalProvider(
        LocalEnterpriseColors provides enterpriseColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}


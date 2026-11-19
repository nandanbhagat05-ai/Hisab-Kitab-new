package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KhataLightColorScheme = lightColorScheme(
    primary = IndigoInk,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoInk,
    secondary = SaffronAmber,
    onSecondary = Color.White,
    secondaryContainer = SaffronSoft,
    onSecondaryContainer = SaffronOnSoft,
    tertiary = EmeraldLena,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldSoft,
    onTertiaryContainer = EmeraldOnSoft,
    error = CrimsonDena,
    onError = Color.White,
    errorContainer = CrimsonSoft,
    onErrorContainer = CrimsonOnSoft,
    background = WarmIvory,
    onBackground = InkPrimary,
    surface = LedgerPaper,
    onSurface = InkPrimary,
    surfaceVariant = LedgerPaperAlt,
    onSurfaceVariant = InkSecondary,
    outline = LedgerBorder,
    outlineVariant = LedgerBorder
)

private val KhataDarkColorScheme = darkColorScheme(
    primary = IndigoDarkPrimary,
    onPrimary = DarkSlate,
    primaryContainer = IndigoInk,
    onPrimaryContainer = IndigoContainer,
    secondary = SaffronDark,
    onSecondary = DarkSlate,
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = SaffronSoft,
    tertiary = EmeraldDark,
    onTertiary = DarkSlate,
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = EmeraldSoft,
    error = CrimsonDark,
    onError = DarkSlate,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = CrimsonSoft,
    background = DarkSlate,
    onBackground = DarkTextPrimary,
    surface = DarkCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCardAlt,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

val KhataShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KhataBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KhataDarkColorScheme else KhataLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KhataTypography,
        shapes = KhataShapes,
        content = content
    )
}

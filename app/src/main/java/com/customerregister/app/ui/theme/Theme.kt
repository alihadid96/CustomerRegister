package com.customerregister.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customerregister.app.data.ThemeMode
import com.customerregister.app.domain.AccountStatus

object Brand {
    val Navy = Color(0xFF0B1430)
    val NavyDeep = Color(0xFF070D1F)
    val NavyLight = Color(0xFF1C2B5A)
    val Electric = Color(0xFF2F6BFF)
    val ElectricLight = Color(0xFF6B95FF)
    val Cyan = Color(0xFF22D3EE)
    val Green = Color(0xFF22C55E)
    val Orange = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)

    val headerGradient = Brush.linearGradient(listOf(Navy, Color(0xFF14286B), Color(0xFF1F45B8)))
    val electricGradient = Brush.linearGradient(listOf(Electric, Color(0xFF5B8CFF)))
}

fun statusColor(status: AccountStatus): Color = when (status) {
    AccountStatus.ACTIVE -> Brand.Green
    AccountStatus.EXPIRING -> Brand.Orange
    AccountStatus.EXPIRED -> Brand.Red
}

private val LightColors = lightColorScheme(
    primary = Brand.Electric,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A1E5E),
    secondary = Color(0xFF4A5A8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8F7),
    onSecondaryContainer = Color(0xFF14204A),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF5F7FC),
    onBackground = Brand.Navy,
    surface = Color(0xFFF5F7FC),
    onSurface = Brand.Navy,
    surfaceVariant = Color(0xFFE9EDF7),
    onSurfaceVariant = Color(0xFF59648A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFF),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F3FA),
    surfaceContainerHighest = Color(0xFFE8ECF6),
    outline = Color(0xFFC5CDE2),
    outlineVariant = Color(0xFFE1E6F2),
    error = Brand.Red,
)

private val DarkColors = darkColorScheme(
    primary = Brand.ElectricLight,
    onPrimary = Color(0xFF001A55),
    primaryContainer = Color(0xFF1C3A8C),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB4C0E8),
    onSecondary = Color(0xFF1C2A55),
    secondaryContainer = Color(0xFF26345F),
    onSecondaryContainer = Color(0xFFDCE3FA),
    tertiary = Brand.Cyan,
    background = Brand.NavyDeep,
    onBackground = Color(0xFFE6EAF5),
    surface = Brand.NavyDeep,
    onSurface = Color(0xFFE6EAF5),
    surfaceVariant = Color(0xFF18223F),
    onSurfaceVariant = Color(0xFFA3AECB),
    surfaceContainerLowest = Color(0xFF050A18),
    surfaceContainerLow = Color(0xFF0C1530),
    surfaceContainer = Color(0xFF101A37),
    surfaceContainerHigh = Color(0xFF16213F),
    surfaceContainerHighest = Color(0xFF1C2848),
    outline = Color(0xFF34416A),
    outlineVariant = Color(0xFF1F2A4C),
    error = Color(0xFFFF6B6B),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CustomerRegisterTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

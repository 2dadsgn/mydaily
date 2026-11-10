package com.mydaily.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val GoogleCalendarBlue = Color(0xFF4285F4)
val OutlookBlue = Color(0xFF0078D4)
val GoogleTasksGreen = Color(0xFF10B981)
val SystemCalendarPurple = Color(0xFFA855F7)

val CollisionCrimson = Color(0xFFEF4444)
val CollisionAmber = Color(0xFFF59E0B)
val ClearEmerald = Color(0xFF10B981)

// Dark Palette (original dark theme)
private val DarkBackground = Color(0xFF0B1120)
private val DarkSurface = Color(0xFF1E293B)
private val DarkSurfaceVariant = Color(0xFF334155)
private val DarkTextPrimary = Color(0xFFF8FAFC)
private val DarkTextSecondary = Color(0xFF94A3B8)

// Light Palette (clean daylight theme)
private val LightBackground = Color(0xFFF1F5F9)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE2E8F0)
private val LightTextPrimary = Color(0xFF0F172A)
private val LightTextSecondary = Color(0xFF475569)

val SlateBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkBackground else LightBackground

val SlateSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkSurface else LightSurface

val SlateSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkSurfaceVariant else LightSurfaceVariant

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkTextPrimary else LightTextPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkTextSecondary else LightTextSecondary

val ConflictCardBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF281520) else Color(0xFFFFF1F2)

val ConflictBannerBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF2B1218) else Color(0xFFFEE2E2)

val ConflictTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFFFECACA) else Color(0xFF991B1B)

val FutureConflictBannerBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF292012) else Color(0xFFFEF3C7)

val FutureConflictTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFFFDE68A) else Color(0xFF92400E)

val InfoBannerBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF1E3A5F) else Color(0xFFDBEAFE)

val InfoBannerText: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color.White else Color(0xFF1E3A8A)

val TodayHighlightRing: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF38BDF8) else Color(0xFF2563EB)

val TodayUnselectedBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color(0xFF1E3A5F) else Color(0xFFDBEAFE)

private val DarkColorScheme = darkColorScheme(
    primary = GoogleCalendarBlue,
    secondary = OutlookBlue,
    tertiary = GoogleTasksGreen,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    error = CollisionCrimson
)

private val LightColorScheme = lightColorScheme(
    primary = GoogleCalendarBlue,
    secondary = OutlookBlue,
    tertiary = GoogleTasksGreen,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    error = CollisionCrimson
)

@Composable
fun MyDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

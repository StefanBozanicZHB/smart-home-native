package zhb.company.smart_home.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * One teal palette with every Material 3 role set explicitly (light and dark), so no generated
 * default tone (e.g. the stock purple) leaks into components. Seed: #0F6E56.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E56), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6EFDF), onPrimaryContainer = Color(0xFF04342C),
    inversePrimary = Color(0xFF7DD8B8),
    secondary = Color(0xFF4B635A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DC), onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF3F6374), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E8FC), onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF8), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF8), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF), onSurfaceVariant = Color(0xFF3F4945),
    surfaceTint = Color(0xFF0F6E56),
    inverseSurface = Color(0xFF2C322F), inverseOnSurface = Color(0xFFEDF2EE),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C3),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBF8), surfaceDim = Color(0xFFD6DBD8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F5F2),
    surfaceContainer = Color(0xFFEAEFEC), surfaceContainerHigh = Color(0xFFE4EAE6),
    surfaceContainerHighest = Color(0xFFDFE4E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8B8), onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005140), onPrimaryContainer = Color(0xFF9AF4D3),
    inversePrimary = Color(0xFF0F6E56),
    secondary = Color(0xFFB2CCC1), onSecondary = Color(0xFF1D352D),
    secondaryContainer = Color(0xFF344C43), onSecondaryContainer = Color(0xFFCDE8DC),
    tertiary = Color(0xFFA7CCE0), onTertiary = Color(0xFF0B3545),
    tertiaryContainer = Color(0xFF264B5C), onTertiaryContainer = Color(0xFFC2E8FC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceTint = Color(0xFF7DD8B8),
    inverseSurface = Color(0xFFDEE4E0), inverseOnSurface = Color(0xFF2C322F),
    outline = Color(0xFF89938E), outlineVariant = Color(0xFF3F4945),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353B38), surfaceDim = Color(0xFF0F1512),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

/**
 * Status colors with a meaning, not decoration: "on/running" (active), warning and danger,
 * plus blue/orange for the cold (well) and warm (heating) side of the heat pump.
 */
@Immutable
data class StatusColors(
    val active: Color,
    val activeContainer: Color,
    val onActiveContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val cold: Color,
    val coldContainer: Color,
    val onColdContainer: Color,
    val warm: Color,
    val warmContainer: Color,
    val onWarmContainer: Color,
)

private val LightStatus = StatusColors(
    active = Color(0xFF1D9E75), activeContainer = Color(0xFFC6EFDF), onActiveContainer = Color(0xFF04342C),
    warning = Color(0xFFB26A00), warningContainer = Color(0xFFFAEEDA), onWarningContainer = Color(0xFF633806),
    danger = Color(0xFFBA1A1A), dangerContainer = Color(0xFFFFDAD6), onDangerContainer = Color(0xFF410002),
    cold = Color(0xFF185FA5), coldContainer = Color(0xFFE6F1FB), onColdContainer = Color(0xFF0C447C),
    warm = Color(0xFFD85A30), warmContainer = Color(0xFFFAECE7), onWarmContainer = Color(0xFF712B13),
)

private val DarkStatus = StatusColors(
    active = Color(0xFF5DCAA5), activeContainer = Color(0xFF085041), onActiveContainer = Color(0xFFC6EFDF),
    warning = Color(0xFFEF9F27), warningContainer = Color(0xFF633806), onWarningContainer = Color(0xFFFAEEDA),
    danger = Color(0xFFFFB4AB), dangerContainer = Color(0xFF93000A), onDangerContainer = Color(0xFFFFDAD6),
    cold = Color(0xFF85B7EB), coldContainer = Color(0xFF0C447C), onColdContainer = Color(0xFFE6F1FB),
    warm = Color(0xFFF0997B), warmContainer = Color(0xFF712B13), onWarmContainer = Color(0xFFFAECE7),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Access as `AppTheme.status.active`, next to `MaterialTheme.colorScheme`. */
object AppTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}

fun smartHomeColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) DarkColors else LightColors

@Composable
fun SmartHomeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = smartHomeColorScheme(darkTheme),
            typography = AppTypography,
            content = content,
        )
    }
}

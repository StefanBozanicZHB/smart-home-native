package zhb.company.smart_home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = White,
    background = LightCanvas,
    surface = White,
    surfaceVariant = ShortcutBackgroundLight,
    error = DisableRed,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryColor,
    onPrimary = White,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = ShortcutBackgroundDark,
    error = DisableRed,
)

@Composable
fun SmartHomeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

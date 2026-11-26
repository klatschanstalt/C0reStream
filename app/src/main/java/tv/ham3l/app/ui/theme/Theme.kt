package tv.ham3l.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.ham3l.app.ui.settings.SettingsViewModel

private val DarkScheme = darkColorScheme(
    primary = Ham3LCyan,
    secondary = Ham3LViolet,
    background = Ham3LBase,
    surface = Ham3LSurface,
    surfaceVariant = Ham3LSurfaceHigh,
    onPrimary = Ham3LBase,
    onBackground = Ham3LText,
    onSurface = Ham3LText,
    onSurfaceVariant = Ham3LTextDim,
    error = Ham3LRed,
)

private val LightScheme = lightColorScheme(
    primary = Ham3LViolet,
    secondary = Ham3LCyan,
    background = LightBase,
    surface = LightSurface,
    surfaceVariant = LightSurfaceHigh,
    onPrimary = LightSurface,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightTextDim,
)

// Zweit-Brand: "Ember" (Orange/Rot)
private val EmberDark = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFF7A45),
    secondary = androidx.compose.ui.graphics.Color(0xFFFF4D6D),
    background = androidx.compose.ui.graphics.Color(0xFF140B08),
    surface = androidx.compose.ui.graphics.Color(0xFF1F130E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C1B14),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF140B08),
    onBackground = androidx.compose.ui.graphics.Color(0xFFFBEFE8),
    onSurface = androidx.compose.ui.graphics.Color(0xFFFBEFE8),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB09A8D),
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class Brand { HAM3L, EMBER }

@Composable
fun Ham3LThemeFromPrefs(content: @Composable () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val mode by vm.themeMode.collectAsState()
    val brand by vm.brand.collectAsState()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = when {
        brand == Brand.EMBER -> EmberDark
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Ham3LTypography,
        content = content,
    )
}

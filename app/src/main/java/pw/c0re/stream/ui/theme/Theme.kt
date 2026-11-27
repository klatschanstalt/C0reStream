package pw.c0re.stream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.ui.settings.SettingsViewModel

private val DarkScheme = darkColorScheme(
    primary = C0reStreamCyan,
    secondary = C0reStreamViolet,
    background = C0reStreamBase,
    surface = C0reStreamSurface,
    surfaceVariant = C0reStreamSurfaceHigh,
    onPrimary = C0reStreamBase,
    onBackground = C0reStreamText,
    onSurface = C0reStreamText,
    onSurfaceVariant = C0reStreamTextDim,
    error = C0reStreamRed,
)

private val LightScheme = lightColorScheme(
    primary = C0reStreamViolet,
    secondary = C0reStreamCyan,
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

enum class ThemeMode { DARK, LIGHT, SYSTEM, AMOLED }
enum class Brand { HAM3L, EMBER }

@Composable
fun C0reStreamThemeFromPrefs(content: @Composable () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val mode by vm.themeMode.collectAsState()
    val brand by vm.brand.collectAsState()
    val dark = when (mode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = when {
        mode == ThemeMode.AMOLED -> DarkScheme.copy(background = androidx.compose.ui.graphics.Color.Black, surface = androidx.compose.ui.graphics.Color.Black)
        brand == Brand.EMBER && dark -> EmberDark
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = C0reStreamTypography,
        content = content,
    )
}

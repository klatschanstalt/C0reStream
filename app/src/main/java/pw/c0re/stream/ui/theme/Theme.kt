package pw.c0re.stream.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pw.c0re.stream.ui.components.BrandSpacing
import pw.c0re.stream.ui.components.BrandThemeTokens
import pw.c0re.stream.ui.components.BrandTokens
import pw.c0re.stream.ui.components.LocalBrandTokens
import pw.c0re.stream.ui.settings.SettingsViewModel

/** Short, transform/alpha-only motion shared by all brand components. */
object BrandMotion {
    const val DurationFast = 120
    const val DurationMedium = 220
    const val DurationSlow = 320
    const val PressScale = 0.98f

    /** Instant when the user reduced motion; otherwise a short eased tween. */
    fun <T> spec(reducedMotion: Boolean, durationMs: Int): AnimationSpec<T> =
        if (reducedMotion) {
            snap()
        } else {
            tween(durationMs, easing = FastOutSlowInEasing)
        }
}

/** Material3 shape scale, plus the named component shapes used by the brand kit. */
val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
val BrandCardShape = RoundedCornerShape(20.dp)
val BrandButtonShape = RoundedCornerShape(14.dp)
val BrandPillShape = RoundedCornerShape(50)

private val DarkScheme = darkColorScheme(
    primary = BrandCyan,
    onPrimary = BrandNavyDeep,
    primaryContainer = Color(0xFF0B3055),
    onPrimaryContainer = BrandTextOnDark,
    inversePrimary = BrandBlue,
    secondary = BrandViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1A5C),
    onSecondaryContainer = BrandTextOnDark,
    tertiary = BrandMagenta,
    onTertiary = BrandNavyDeep,
    tertiaryContainer = Color(0xFF4A1040),
    onTertiaryContainer = BrandTextOnDark,
    background = BrandNavyDeep,
    onBackground = BrandTextOnDark,
    surface = BrandSurfaceDark,
    onSurface = BrandTextOnDark,
    surfaceVariant = BrandSurfaceDarkVariant,
    onSurfaceVariant = BrandTextMutedOnDark,
    surfaceTint = BrandCyan,
    inverseSurface = BrandTextOnDark,
    inverseOnSurface = BrandNavyDeep,
    error = BrandError,
    onError = BrandNavyDeep,
    errorContainer = Color(0xFF5C1226),
    onErrorContainer = Color(0xFFFFD9E0),
    outline = BrandBorderDark,
    outlineVariant = Color(0xFF24314C),
    scrim = Color.Black,
    surfaceBright = BrandSurfaceDarkHigh,
    surfaceDim = Color(0xFF080D1A),
    surfaceContainer = BrandSurfaceDark,
    surfaceContainerHigh = BrandSurfaceDarkHigh,
    surfaceContainerHighest = Color(0xFF1F2C4E),
    surfaceContainerLow = Color(0xFF0B1222),
    surfaceContainerLowest = Color(0xFF060A14),
)

private val AmoledScheme = DarkScheme.copy(
    background = BrandAmoledBackground,
    surface = BrandAmoledSurface,
    surfaceVariant = BrandAmoledSurfaceHigh,
    surfaceBright = BrandAmoledSurfaceHigh,
    surfaceDim = BrandAmoledBackground,
    surfaceContainer = BrandAmoledSurface,
    surfaceContainerHigh = BrandAmoledSurfaceHigh,
    surfaceContainerHighest = Color(0xFF121621),
    surfaceContainerLow = Color(0xFF030407),
    surfaceContainerLowest = BrandAmoledBackground,
    outline = Color(0xFF202633),
    outlineVariant = Color(0xFF161A23),
)

private val LightScheme = lightColorScheme(
    primary = BrandVioletLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF210B57),
    inversePrimary = Color(0xFFB7A5FF),
    secondary = BrandBlueLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EAFF),
    onSecondaryContainer = Color(0xFF002E63),
    tertiary = BrandMagentaLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9F2),
    onTertiaryContainer = Color(0xFF4E003B),
    background = BrandBaseLight,
    onBackground = BrandTextOnLight,
    surface = BrandSurfaceLight,
    onSurface = BrandTextOnLight,
    surfaceVariant = BrandSurfaceLightHigh,
    onSurfaceVariant = BrandTextMutedOnLight,
    surfaceTint = BrandVioletLight,
    inverseSurface = Color(0xFF2A3040),
    inverseOnSurface = Color.White,
    error = Color(0xFFB32644),
    onError = Color.White,
    errorContainer = Color(0xFFFFD9E0),
    onErrorContainer = Color(0xFF41001A),
    outline = BrandBorderLight,
    outlineVariant = Color(0xFFE3E9F3),
    scrim = Color.Black,
    surfaceBright = BrandSurfaceLight,
    surfaceDim = Color(0xFFE2E7F0),
    surfaceContainer = Color(0xFFF2F5FA),
    surfaceContainerHigh = Color(0xFFE8EDF5),
    surfaceContainerHighest = BrandSurfaceLightHigh,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainerLowest = Color.White,
)

private val EmberDarkScheme = darkColorScheme(
    primary = BrandGold,
    onPrimary = EmberBackground,
    primaryContainer = Color(0xFF4A3310),
    onPrimaryContainer = Color(0xFFFFF0D2),
    inversePrimary = BrandOrange,
    secondary = BrandPink,
    onSecondary = EmberBackground,
    secondaryContainer = Color(0xFF4B1436),
    onSecondaryContainer = Color(0xFFFFE1F0),
    tertiary = BrandOrange,
    onTertiary = EmberBackground,
    tertiaryContainer = Color(0xFF4C2410),
    onTertiaryContainer = Color(0xFFFFE8D5),
    background = EmberBackground,
    onBackground = EmberText,
    surface = EmberSurface,
    onSurface = EmberText,
    surfaceVariant = EmberSurfaceHigh,
    onSurfaceVariant = EmberTextMuted,
    surfaceTint = BrandGold,
    inverseSurface = EmberText,
    inverseOnSurface = EmberBackground,
    error = BrandError,
    onError = EmberBackground,
    errorContainer = Color(0xFF5C1226),
    onErrorContainer = Color(0xFFFFD9E0),
    outline = Color(0xFF5A3547),
    outlineVariant = Color(0xFF3A2230),
    scrim = Color.Black,
    surfaceBright = EmberSurfaceHigh,
    surfaceDim = Color(0xFF0C0609),
    surfaceContainer = EmberSurface,
    surfaceContainerHigh = EmberSurfaceHigh,
    surfaceContainerHighest = Color(0xFF341B29),
    surfaceContainerLow = Color(0xFF180D13),
    surfaceContainerLowest = Color(0xFF0A0508),
)

private val EmberAmoledScheme = EmberDarkScheme.copy(
    background = BrandAmoledBackground,
    surface = BrandAmoledSurface,
    surfaceVariant = BrandAmoledSurfaceHigh,
    surfaceBright = BrandAmoledSurfaceHigh,
    surfaceDim = BrandAmoledBackground,
    surfaceContainer = BrandAmoledSurface,
    surfaceContainerHigh = BrandAmoledSurfaceHigh,
    surfaceContainerHighest = Color(0xFF121621),
    surfaceContainerLow = Color(0xFF030407),
    surfaceContainerLowest = BrandAmoledBackground,
    outline = Color(0xFF202633),
    outlineVariant = Color(0xFF161A23),
)

private val EmberLightScheme = lightColorScheme(
    primary = EmberPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9C2),
    onPrimaryContainer = Color(0xFF3A1000),
    inversePrimary = Color(0xFFFFB68A),
    secondary = EmberSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9F2),
    onSecondaryContainer = Color(0xFF4E003B),
    tertiary = BrandOrangeLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3CC),
    onTertiaryContainer = Color(0xFF3A1A00),
    background = EmberBackgroundLight,
    onBackground = BrandTextOnLight,
    surface = Color.White,
    onSurface = BrandTextOnLight,
    surfaceVariant = Color(0xFFF2E7E3),
    onSurfaceVariant = Color(0xFF62545A),
    surfaceTint = EmberPrimaryLight,
    inverseSurface = Color(0xFF30262A),
    inverseOnSurface = Color.White,
    error = Color(0xFFB32644),
    onError = Color.White,
    errorContainer = Color(0xFFFFD9E0),
    onErrorContainer = Color(0xFF41001A),
    outline = Color(0xFFE0CECF),
    outlineVariant = Color(0xFFEFE3E3),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEADDD8),
    surfaceContainer = Color(0xFFFAF2EF),
    surfaceContainerHigh = Color(0xFFF2E7E3),
    surfaceContainerHighest = Color(0xFFEADDD8),
    surfaceContainerLow = Color(0xFFFDF9F7),
    surfaceContainerLowest = Color.White,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM, AMOLED }
enum class Brand { HAM3L, EMBER }

/** Brand color scheme for a mode. No dynamic/platform colors are used. */
fun brandColorScheme(
    mode: ThemeMode,
    brand: Brand = Brand.HAM3L,
    systemDark: Boolean = false,
): ColorScheme {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.AMOLED ||
        (mode == ThemeMode.SYSTEM && systemDark)
    return when {
        mode == ThemeMode.AMOLED && brand == Brand.EMBER -> EmberAmoledScheme
        mode == ThemeMode.AMOLED -> AmoledScheme
        brand == Brand.EMBER && dark -> EmberDarkScheme
        brand == Brand.EMBER -> EmberLightScheme
        dark -> DarkScheme
        else -> LightScheme
    }
}

/** Matching gradient/glow/spacing tokens for previews and non-theme composables. */
fun brandThemeTokens(
    mode: ThemeMode,
    brand: Brand = Brand.HAM3L,
    systemDark: Boolean = false,
): BrandThemeTokens {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.AMOLED ||
        (mode == ThemeMode.SYSTEM && systemDark)
    return when {
        mode == ThemeMode.AMOLED && brand == Brand.EMBER -> BrandTokens.EmberAmoled
        mode == ThemeMode.AMOLED -> BrandTokens.Amoled
        brand == Brand.EMBER && dark -> BrandTokens.EmberDark
        brand == Brand.EMBER -> BrandTokens.EmberLight
        dark -> BrandTokens.Dark
        else -> BrandTokens.Light
    }
}

@Composable
fun C0reStreamThemeFromPrefs(content: @Composable () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val mode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.DARK)
    val brand = runCatching { Brand.valueOf(settings.brand) }.getOrDefault(Brand.HAM3L)
    val systemDark = isSystemInDarkTheme()

    val scheme = brandColorScheme(mode, brand, systemDark)
    val tokens = brandThemeTokens(mode, brand, systemDark)
    val typography = rememberBrandTypography()

    CompositionLocalProvider(
        LocalBrandTokens provides tokens,
        LocalBrandTypography provides typography,
        LocalMinimumInteractiveComponentSize provides BrandSpacing.Default.touchTarget,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography.asMaterialTypography(),
            shapes = BrandShapes,
            content = content,
        )
    }
}

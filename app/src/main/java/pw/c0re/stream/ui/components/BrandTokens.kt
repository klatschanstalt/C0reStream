package pw.c0re.stream.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pw.c0re.stream.ui.theme.BrandAmoledBackground
import pw.c0re.stream.ui.theme.BrandAmoledSurface
import pw.c0re.stream.ui.theme.BrandAmoledSurfaceHigh
import pw.c0re.stream.ui.theme.BrandBaseLight
import pw.c0re.stream.ui.theme.BrandBlue
import pw.c0re.stream.ui.theme.BrandBlueLight
import pw.c0re.stream.ui.theme.BrandBorderDark
import pw.c0re.stream.ui.theme.BrandBorderLight
import pw.c0re.stream.ui.theme.BrandCyan
import pw.c0re.stream.ui.theme.BrandCyanLight
import pw.c0re.stream.ui.theme.BrandGold
import pw.c0re.stream.ui.theme.BrandMagenta
import pw.c0re.stream.ui.theme.BrandMagentaLight
import pw.c0re.stream.ui.theme.BrandNavyDeep
import pw.c0re.stream.ui.theme.BrandNavyStart
import pw.c0re.stream.ui.theme.BrandOrange
import pw.c0re.stream.ui.theme.BrandPink
import pw.c0re.stream.ui.theme.BrandPlumDeep
import pw.c0re.stream.ui.theme.BrandStateColors
import pw.c0re.stream.ui.theme.BrandSurfaceDark
import pw.c0re.stream.ui.theme.BrandSurfaceDarkHigh
import pw.c0re.stream.ui.theme.BrandSurfaceLight
import pw.c0re.stream.ui.theme.BrandViolet
import pw.c0re.stream.ui.theme.BrandVioletLight
import pw.c0re.stream.ui.theme.EmberBackground
import pw.c0re.stream.ui.theme.EmberBackgroundLight
import pw.c0re.stream.ui.theme.EmberPrimaryLight
import pw.c0re.stream.ui.theme.EmberSecondaryLight
import pw.c0re.stream.ui.theme.EmberSurface
import pw.c0re.stream.ui.theme.EmberSurfaceHigh

/** Spacing scale shared by all brand components. All values are `dp`. */
data class BrandSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screenPadding: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val sectionGap: Dp = 24.dp,
    val touchTarget: Dp = 48.dp,
    val topBarHeight: Dp = 64.dp,
    val iconSize: Dp = 24.dp,
) {
    companion object {
        val Default = BrandSpacing()
    }
}

/**
 * Everything visual that a screen needs but should never hardcode: gradients, glow,
 * spacing and the semantic state palette. Provided by `C0reStreamThemeFromPrefs()`.
 * Brushes are immutable and sized by the drawing bounds, so one instance is reused.
 */
data class BrandThemeTokens(
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val backdrop: Brush,
    val primaryGradient: Brush,
    val secondaryGradient: Brush,
    val hotGradient: Brush,
    val cardBackground: Brush,
    val badgeBackground: Brush,
    val glow: Brush,
    val glowSecondary: Brush,
    val cardBorder: Color,
    val topBarBackground: Color,
    val states: BrandStateColors,
    val spacing: BrandSpacing,
)

/** Ready-made token sets for each theme/brand combination. Dark is the reference. */
object BrandTokens {
    private val spectrum = listOf(BrandCyan, BrandBlue, BrandViolet, BrandMagenta, BrandOrange)
    private val spectrumStops = listOf(0f, 0.35f, 0.65f, 0.85f, 1f)

    private fun spectrumBrush(colors: List<Color>, stops: List<Float>? = null): Brush =
        Brush.linearGradient(colors = colors, stops = stops)

    private fun glowBrush(color: Color, alpha: Float): Brush =
        Brush.radialGradient(colors = listOf(color.copy(alpha = alpha), Color.Transparent))

    private fun solidBrush(color: Color): Brush = spectrumBrush(listOf(color, color))

    val Dark = BrandThemeTokens(
        accent = BrandCyan,
        accentSecondary = BrandViolet,
        onAccent = BrandNavyDeep,
        backdrop = spectrumBrush(listOf(BrandNavyStart, BrandNavyDeep, BrandPlumDeep)),
        primaryGradient = spectrumBrush(spectrum, spectrumStops),
        secondaryGradient = spectrumBrush(listOf(BrandViolet, BrandMagenta, BrandPink)),
        hotGradient = spectrumBrush(listOf(BrandGold, BrandPink)),
        cardBackground = spectrumBrush(listOf(BrandSurfaceDarkHigh, BrandSurfaceDark)),
        badgeBackground = spectrumBrush(spectrum, spectrumStops),
        glow = glowBrush(BrandCyan, 0.20f),
        glowSecondary = glowBrush(BrandViolet, 0.16f),
        cardBorder = BrandBorderDark.copy(alpha = 0.7f),
        topBarBackground = BrandNavyDeep.copy(alpha = 0.88f),
        states = BrandStateColors.Dark,
        spacing = BrandSpacing.Default,
    )

    val Amoled = Dark.copy(
        backdrop = solidBrush(BrandAmoledBackground),
        cardBackground = spectrumBrush(listOf(BrandAmoledSurfaceHigh, BrandAmoledSurface)),
        glow = glowBrush(BrandCyan, 0.10f),
        glowSecondary = glowBrush(BrandMagenta, 0.08f),
        cardBorder = Color(0xFF202633),
        topBarBackground = BrandAmoledBackground,
        states = BrandStateColors.Amoled,
    )

    val Light = BrandThemeTokens(
        accent = BrandVioletLight,
        accentSecondary = BrandBlueLight,
        onAccent = Color.White,
        backdrop = spectrumBrush(listOf(BrandBaseLight, Color(0xFFEAF0FA))),
        primaryGradient = spectrumBrush(
            listOf(BrandCyanLight, BrandBlueLight, BrandVioletLight, BrandMagentaLight, BrandOrangeLight),
            spectrumStops,
        ),
        secondaryGradient = spectrumBrush(listOf(BrandBlueLight, BrandVioletLight, BrandMagentaLight)),
        hotGradient = spectrumBrush(listOf(BrandOrangeLight, BrandMagentaLight)),
        cardBackground = spectrumBrush(listOf(BrandSurfaceLight, BrandBaseLight)),
        badgeBackground = spectrumBrush(listOf(BrandBlueLight, BrandVioletLight, BrandMagentaLight)),
        glow = glowBrush(BrandCyan, 0.10f),
        glowSecondary = glowBrush(BrandViolet, 0.08f),
        cardBorder = BrandBorderLight,
        topBarBackground = BrandSurfaceLight,
        states = BrandStateColors.Light,
        spacing = BrandSpacing.Default,
    )

    val EmberDark = Dark.copy(
        accent = BrandGold,
        accentSecondary = BrandPink,
        onAccent = EmberBackground,
        backdrop = spectrumBrush(listOf(EmberSurfaceHigh, EmberBackground, BrandPlumDeep)),
        primaryGradient = spectrumBrush(listOf(BrandGold, BrandOrange, BrandPink)),
        secondaryGradient = spectrumBrush(listOf(BrandPink, BrandMagenta)),
        cardBackground = spectrumBrush(listOf(EmberSurface, EmberBackground)),
        badgeBackground = spectrumBrush(listOf(BrandGold, BrandPink)),
        glow = glowBrush(BrandGold, 0.14f),
        glowSecondary = glowBrush(BrandPink, 0.14f),
        cardBorder = Color(0xFF5A3547),
        topBarBackground = EmberBackground.copy(alpha = 0.9f),
        states = BrandStateColors.Ember,
    )

    val EmberLight = Dark.copy(
        accent = EmberPrimaryLight,
        accentSecondary = EmberSecondaryLight,
        onAccent = Color.White,
        backdrop = spectrumBrush(listOf(EmberBackgroundLight, Color(0xFFFFEEE7))),
        primaryGradient = spectrumBrush(
            listOf(EmberPrimaryLight, BrandOrangeLight, EmberSecondaryLight),
            spectrumStops,
        ),
        secondaryGradient = spectrumBrush(listOf(EmberSecondaryLight, BrandMagentaLight)),
        hotGradient = spectrumBrush(listOf(BrandOrangeLight, EmberSecondaryLight)),
        cardBackground = spectrumBrush(listOf(BrandSurfaceLight, Color(0xFFFBF4F1))),
        badgeBackground = spectrumBrush(listOf(EmberPrimaryLight, EmberSecondaryLight)),
        glow = glowBrush(BrandOrange, 0.10f),
        glowSecondary = glowBrush(BrandPink, 0.08f),
        cardBorder = Color(0xFFE0CECF),
        topBarBackground = BrandSurfaceLight,
        states = BrandStateColors.Light.copy(
            focus = EmberPrimaryLight,
            selection = EmberSecondaryLight,
        ),
    )

    val EmberAmoled = EmberDark.copy(
        backdrop = solidBrush(BrandAmoledBackground),
        cardBackground = spectrumBrush(listOf(BrandAmoledSurfaceHigh, BrandAmoledSurface)),
        glow = glowBrush(BrandGold, 0.08f),
        glowSecondary = glowBrush(BrandPink, 0.08f),
        cardBorder = Color(0xFF202633),
        topBarBackground = BrandAmoledBackground,
    )
}

/** Read gradients, glow, spacing and state colors from here instead of hardcoding them. */
val LocalBrandTokens = compositionLocalOf<BrandThemeTokens> { BrandTokens.Dark }

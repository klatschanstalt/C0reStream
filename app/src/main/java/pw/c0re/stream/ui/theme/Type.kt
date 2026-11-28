package pw.c0re.stream.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography for C0reStream. Every size is in `sp`, so Android's own font-size setting
 * (including 1.5x) is honored automatically. There is deliberately no width-dependent
 * scale: layout responsiveness is handled by `rememberResponsivePadding` and
 * `AdaptivePane`, and shrinking text on narrow screens would work against users who
 * need larger text. See [C0reStreamThemeFromPrefs] for how this scale is installed.
 *
 * The Material3 slots are the canonical set used by `MaterialTheme.typography`.
 * `player*` and `status*` styles are brand-specific slots exposed through
 * [LocalBrandTypography] for player and status/empty-state surfaces.
 */
data class BrandTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    val playerTitle: TextStyle,
    val playerSubtitle: TextStyle,
    val playerTime: TextStyle,
    val playerMetadata: TextStyle,
    val playerControlLabel: TextStyle,
    val statusTitle: TextStyle,
    val statusBody: TextStyle,
    val statusLabel: TextStyle,
) {
    /** Adapts the full set to Material3 so `MaterialTheme.typography` stays consistent. */
    fun asMaterialTypography(): Typography = Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
    )

    companion object {
        /** Regular-width reference styles. */
        val Base = BrandTypography(
            displayLarge = textStyle(57f, 64f, FontWeight.ExtraBold, -0.5f),
            displayMedium = textStyle(45f, 52f, FontWeight.ExtraBold),
            displaySmall = textStyle(36f, 44f, FontWeight.Bold),
            headlineLarge = textStyle(32f, 40f, FontWeight.ExtraBold, -0.5f),
            headlineMedium = textStyle(28f, 36f, FontWeight.Bold, -0.5f),
            headlineSmall = textStyle(24f, 32f, FontWeight.Bold),
            titleLarge = textStyle(22f, 28f, FontWeight.SemiBold),
            titleMedium = textStyle(16f, 24f, FontWeight.SemiBold, 0.1f),
            titleSmall = textStyle(14f, 20f, FontWeight.Medium, 0.1f),
            bodyLarge = textStyle(16f, 24f),
            bodyMedium = textStyle(14f, 20f),
            bodySmall = textStyle(12f, 16f, FontWeight.Normal, 0.4f),
            labelLarge = textStyle(14f, 20f, FontWeight.SemiBold, 0.1f),
            labelMedium = textStyle(12f, 16f, FontWeight.Medium, 0.5f),
            labelSmall = textStyle(11f, 16f, FontWeight.Medium, 0.8f),
            playerTitle = textStyle(22f, 28f, FontWeight.ExtraBold, -0.4f),
            playerSubtitle = textStyle(14f, 20f, FontWeight.Medium, 0.2f),
            playerTime = textStyle(15f, 20f, FontWeight.Bold, 1f),
            playerMetadata = textStyle(13f, 18f),
            playerControlLabel = textStyle(12f, 16f, FontWeight.SemiBold, 0.8f),
            statusTitle = textStyle(20f, 26f, FontWeight.Bold),
            statusBody = textStyle(14f, 20f),
            statusLabel = textStyle(11f, 16f, FontWeight.SemiBold, 1.2f),
        )
    }
}

private fun textStyle(
    size: Float,
    lineHeight: Float,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
): TextStyle = TextStyle(
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * Static typography for previews and non-composable callers. Lazy on purpose: eagerly deriving
 * this at file level while [BrandTypography.Base] is being constructed creates a circular class
 * initialization (the companion calls the file-level `textStyle` helper).
 */
val C0reStreamTypography: Typography by lazy { BrandTypography.Base.asMaterialTypography() }

/** Brand-specific typography slots (player/status) for composables. */
val LocalBrandTypography = compositionLocalOf<BrandTypography> { BrandTypography.Base }

// No width-dependent typography selection exists on purpose: the single sp-based scale above
// follows the platform font setting, and screen widths are handled by layout (padding/panes).

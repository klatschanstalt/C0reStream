package pw.c0re.stream.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Typography for C0reStream. Every size is in `sp`, so the system font-size setting
 * is honored automatically. [rememberBrandTypography] additionally picks a compact,
 * regular or expanded step from the window width; it never applies a second font
 * scale on top of the platform's own scaling.
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
    /** Returns a copy with all sizes scaled by [factor] while remaining in `sp`. */
    fun scaled(factor: Float): BrandTypography = copy(
        displayLarge = displayLarge.scaledBy(factor),
        displayMedium = displayMedium.scaledBy(factor),
        displaySmall = displaySmall.scaledBy(factor),
        headlineLarge = headlineLarge.scaledBy(factor),
        headlineMedium = headlineMedium.scaledBy(factor),
        headlineSmall = headlineSmall.scaledBy(factor),
        titleLarge = titleLarge.scaledBy(factor),
        titleMedium = titleMedium.scaledBy(factor),
        titleSmall = titleSmall.scaledBy(factor),
        bodyLarge = bodyLarge.scaledBy(factor),
        bodyMedium = bodyMedium.scaledBy(factor),
        bodySmall = bodySmall.scaledBy(factor),
        labelLarge = labelLarge.scaledBy(factor),
        labelMedium = labelMedium.scaledBy(factor),
        labelSmall = labelSmall.scaledBy(factor),
        playerTitle = playerTitle.scaledBy(factor),
        playerSubtitle = playerSubtitle.scaledBy(factor),
        playerTime = playerTime.scaledBy(factor),
        playerMetadata = playerMetadata.scaledBy(factor),
        playerControlLabel = playerControlLabel.scaledBy(factor),
        statusTitle = statusTitle.scaledBy(factor),
        statusBody = statusBody.scaledBy(factor),
        statusLabel = statusLabel.scaledBy(factor),
    )

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

private fun TextStyle.scaledBy(factor: Float): TextStyle = copy(
    fontSize = (fontSize.value * factor).sp,
    lineHeight = if (lineHeight == TextUnit.Unspecified) {
        TextUnit.Unspecified
    } else {
        (lineHeight.value * factor).sp
    },
    letterSpacing = if (letterSpacing == TextUnit.Unspecified) {
        TextUnit.Unspecified
    } else {
        (letterSpacing.value * factor).sp
    },
)

/** Static typography for previews and non-composable callers. */
val C0reStreamTypography: Typography = BrandTypography.Base.asMaterialTypography()

/** Brand-specific typography slots (player/status) for composables. */
val LocalBrandTypography = compositionLocalOf<BrandTypography> { BrandTypography.Base }

/**
 * Selects a width-appropriate typography step. The scale is applied to `sp` values,
 * so user font-size preferences still work exactly as with any other Compose text.
 */
@Composable
fun rememberBrandTypography(): BrandTypography {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp().value }
    val step = when {
        widthDp < 360f -> 0.92f
        widthDp < 600f -> 1f
        widthDp < 840f -> 1.05f
        else -> 1.1f
    }
    return remember(step) { BrandTypography.Base.scaled(step) }
}

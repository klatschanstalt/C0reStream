package pw.c0re.stream.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * C0reStream palette, derived from the native vector artwork in assets/branding/.
 *
 * Spectrum stops are taken verbatim from the `g` gradient used by logo-transparent.svg,
 * app-icon.svg, splashscreen.svg and every feature SVG. Backdrop stops come from the
 * `bg` gradient; the warm pair comes from `hot`. Text colors come from the logo/splash
 * wordmarks. Everything else is a tonal step of those exact colors, chosen so the
 * brand survives dark/light/AMOLED schemes without dynamic color overriding it.
 */

// --- Spectrum (brand "g" gradient, in stop order) -------------------------------
val BrandCyan = Color(0xFF52FAFF)
val BrandBlue = Color(0xFF008CFF)
val BrandViolet = Color(0xFF6434FF)
val BrandMagenta = Color(0xFFFF36CF)
val BrandOrange = Color(0xFFFFB34E)

// --- Warm accent pair (brand "hot" gradient) ------------------------------------
val BrandGold = Color(0xFFFFDA72)
val BrandPink = Color(0xFFFF36AD)

// --- Backdrop (brand "bg" gradient) ---------------------------------------------
val BrandNavyStart = Color(0xFF102C51)
val BrandNavyDeep = Color(0xFF050B20)
val BrandPlumDeep = Color(0xFF210C40)

// --- On-dark content colors (logo / splash wordmark) ----------------------------
val BrandTextOnDark = Color(0xFFF3F7FF)
val BrandTextMutedOnDark = Color(0xFFB9D5F5)

// --- Dark UI surfaces (tonal steps of the backdrop) -----------------------------
val BrandSurfaceDark = Color(0xFF0E162B)
val BrandSurfaceDarkHigh = Color(0xFF162344)
val BrandSurfaceDarkVariant = Color(0xFF1D1538)
val BrandBorderDark = Color(0xFF2C3A5C)
val BrandScrim = Color(0xB3050B20)

// --- Light UI surfaces ----------------------------------------------------------
val BrandBaseLight = Color(0xFFF5F7FB)
val BrandSurfaceLight = Color(0xFFFFFFFF)
val BrandSurfaceLightHigh = Color(0xFFE9EEF7)
val BrandTextOnLight = Color(0xFF111827)
val BrandTextMutedOnLight = Color(0xFF5B6678)
val BrandBorderLight = Color(0xFFD8E1EF)

// Contrast-adjusted accents for light backgrounds (same hues, darker values).
val BrandBlueLight = Color(0xFF0057C7)
val BrandVioletLight = Color(0xFF4A1FD1)
val BrandMagentaLight = Color(0xFFA31584)
val BrandCyanLight = Color(0xFF006C82)
val BrandOrangeLight = Color(0xFF8A4E07)

// --- AMOLED: pure black with the same spectrum ---------------------------------
val BrandAmoledBackground = Color(0xFF000000)
val BrandAmoledSurface = Color(0xFF05060A)
val BrandAmoledSurfaceHigh = Color(0xFF0B0E16)

// --- Ember variant: warm steps of the asset's hot/orange end --------------------
val EmberBackground = Color(0xFF140B10)
val EmberSurface = Color(0xFF1E1118)
val EmberSurfaceHigh = Color(0xFF2A1522)
val EmberText = Color(0xFFFFF2F6)
val EmberTextMuted = Color(0xFFD6B6C4)
val EmberBackgroundLight = Color(0xFFFFF8F5)
val EmberPrimaryLight = Color(0xFF8A3B12)
val EmberSecondaryLight = Color(0xFFA31584)

// --- Semantic state colors ------------------------------------------------------
val BrandError = Color(0xFFFF5473)
val BrandSuccess = Color(0xFF36E0A4)
val BrandWarning = BrandOrange
val BrandFocus = BrandCyan
val BrandSelection = BrandViolet
val BrandDisabledDark = Color(0xFF4A556C)
val BrandDisabledLight = Color(0xFFB7C1D0)

/** State palette for one theme. Screen code should read this via [LocalBrandTokens]. */
data class BrandStateColors(
    val error: Color,
    val success: Color,
    val warning: Color,
    val focus: Color,
    val selection: Color,
    val disabled: Color,
) {
    companion object {
        val Dark = BrandStateColors(
            error = BrandError,
            success = BrandSuccess,
            warning = BrandWarning,
            focus = BrandFocus,
            selection = BrandSelection,
            disabled = BrandDisabledDark,
        )
        val Light = BrandStateColors(
            error = Color(0xFFB32644),
            success = Color(0xFF00785A),
            warning = BrandOrangeLight,
            focus = BrandBlueLight,
            selection = BrandVioletLight,
            disabled = BrandDisabledLight,
        )
        val Amoled = Dark.copy(focus = BrandCyan, selection = BrandMagenta)
        val Ember = Dark.copy(
            error = BrandError,
            warning = BrandGold,
            focus = BrandGold,
            selection = BrandPink,
        )
    }
}

// --- Backwards-compatible aliases ----------------------------------------------
// Existing UI code (and other consumers) refer to these names; they now point at
// the asset-derived palette so the whole app shares one source of truth.
val C0reStreamBase = Color(0xFF0B0E14)
val C0reStreamSurface = BrandSurfaceDark
val C0reStreamSurfaceHigh = BrandSurfaceDarkHigh
val C0reStreamCyan = BrandCyan
val C0reStreamViolet = BrandViolet
val C0reStreamText = BrandTextOnDark
val C0reStreamTextDim = BrandTextMutedOnDark
val C0reStreamRed = BrandError

val LightBase = BrandBaseLight
val LightSurface = BrandSurfaceLight
val LightSurfaceHigh = BrandSurfaceLightHigh
val LightText = BrandTextOnLight
val LightTextDim = BrandTextMutedOnLight

package pw.c0re.stream.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * JVM WCAG 2.x contrast checks for the semantic roles used by brand buttons and focus
 * indicators. No UI runtime is needed: the roles are plain packed ARGB constants, and the
 * surfaces mirror the theme colors installed by the app theme.
 */
class BrandContrastTest {

    private data class Surface(val name: String, val argb: Long)

    // Backdrop/surface/container colors a secondary button can actually sit on.
    private val darkSurfaces = listOf(
        Surface("navyDeep", 0xFF050B20),
        Surface("navyStart", 0xFF102C51),
        Surface("plumDeep", 0xFF210C40),
        Surface("surfaceDark", 0xFF0E162B),
        Surface("surfaceDarkHigh", 0xFF162344),
        Surface("surfaceDarkVariant", 0xFF1D1538),
        Surface("surfaceContainerHighest", 0xFF1F2C4E),
        Surface("secondaryContainer", 0xFF2A1A5C),
        Surface("tertiaryContainer", 0xFF4A1040),
        Surface("primaryContainer", 0xFF0B3055),
    )
    private val amoledSurfaces = listOf(
        Surface("black", 0xFF000000),
        Surface("amoledSurface", 0xFF05060A),
        Surface("amoledSurfaceHigh", 0xFF0B0E16),
        Surface("amoledContainerHighest", 0xFF121621),
    )
    private val lightSurfaces = listOf(
        Surface("white", 0xFFFFFFFF),
        Surface("baseLight", 0xFFF5F7FB),
        Surface("surfaceLightHigh", 0xFFE9EEF7),
        Surface("surfaceContainer", 0xFFF2F5FA),
        Surface("surfaceContainerHigh", 0xFFE8EDF5),
        Surface("surfaceDim", 0xFFE2E7F0),
    )
    private val emberDarkSurfaces = listOf(
        Surface("emberBackground", 0xFF140B10),
        Surface("emberSurface", 0xFF1E1118),
        Surface("emberSurfaceHigh", 0xFF2A1522),
        Surface("emberContainerHighest", 0xFF341B29),
        Surface("plumDeep", 0xFF210C40),
    )
    private val emberLightSurfaces = listOf(
        Surface("emberBackgroundLight", 0xFFFFF8F5),
        Surface("white", 0xFFFFFFFF),
        Surface("emberSurfaceVariant", 0xFFF2E7E3),
        Surface("emberSurfaceContainer", 0xFFFAF2EF),
        Surface("emberSurfaceDim", 0xFFEADDD8),
    )

    @Test
    fun `secondary button text reaches 4_5 to 1 on every dark surface`() {
        assertContrast(
            "dark secondary text",
            BrandContrastRoles.SECONDARY_TEXT_DARK,
            darkSurfaces,
            4.5,
        )
    }

    @Test
    fun `dark secondary border and focus reach 3 to 1`() {
        assertContrast("dark secondary border", BrandContrastRoles.SECONDARY_BORDER_DARK, darkSurfaces, 3.0)
        assertContrast("dark focus", BrandContrastRoles.FOCUS_DARK, darkSurfaces, 3.0)
    }

    @Test
    fun `AMOLED secondary roles keep their contrast`() {
        assertContrast("AMOLED secondary text", BrandContrastRoles.SECONDARY_TEXT_DARK, amoledSurfaces, 4.5)
        assertContrast("AMOLED secondary border", BrandContrastRoles.SECONDARY_BORDER_DARK, amoledSurfaces, 3.0)
        assertContrast("AMOLED focus", BrandContrastRoles.FOCUS_DARK, amoledSurfaces, 3.0)
    }

    @Test
    fun `light secondary roles keep their contrast`() {
        assertContrast("light secondary text", BrandContrastRoles.SECONDARY_TEXT_LIGHT, lightSurfaces, 4.5)
        assertContrast("light secondary border", BrandContrastRoles.SECONDARY_BORDER_LIGHT, lightSurfaces, 3.0)
        assertContrast("light focus", BrandContrastRoles.FOCUS_LIGHT, lightSurfaces, 3.0)
    }

    @Test
    fun `ember dark secondary roles keep their contrast`() {
        assertContrast("ember dark secondary text", BrandContrastRoles.SECONDARY_TEXT_EMBER_DARK, emberDarkSurfaces, 4.5)
        assertContrast("ember dark secondary border", BrandContrastRoles.SECONDARY_BORDER_EMBER_DARK, emberDarkSurfaces, 3.0)
        assertContrast("ember dark focus", BrandContrastRoles.FOCUS_EMBER, emberDarkSurfaces, 3.0)
        assertContrast("ember AMOLED secondary text", BrandContrastRoles.SECONDARY_TEXT_EMBER_DARK, amoledSurfaces, 4.5)
        assertContrast("ember AMOLED focus", BrandContrastRoles.FOCUS_EMBER, amoledSurfaces, 3.0)
    }

    @Test
    fun `ember light secondary roles keep their contrast`() {
        assertContrast("ember light secondary text", BrandContrastRoles.SECONDARY_TEXT_EMBER_LIGHT, emberLightSurfaces, 4.5)
        assertContrast("ember light secondary border", BrandContrastRoles.SECONDARY_BORDER_EMBER_LIGHT, emberLightSurfaces, 3.0)
        assertContrast("ember light focus", BrandContrastRoles.FOCUS_EMBER_LIGHT, emberLightSurfaces, 3.0)
    }

    @Test
    fun `legacy violet is no longer the normal secondary text role`() {
        assertTrue(BrandContrastRoles.SECONDARY_TEXT_DARK != 0xFF6434FF)
        // Documents the original defect: the raw brand violet fails 4.5:1 on navyStart.
        assertTrue(contrast(0xFF6434FF, 0xFF102C51) < 4.5)
    }

    private fun assertContrast(label: String, foreground: Long, surfaces: List<Surface>, minimum: Double) {
        surfaces.forEach { surface ->
            val ratio = contrast(foreground, surface.argb)
            assertTrue("$label on ${surface.name}: $ratio:1 is below $minimum:1", ratio >= minimum)
        }
    }

    private fun contrast(a: Long, b: Long): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFFL).toInt() / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}

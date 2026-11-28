package pw.c0re.stream.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Structural checks for the single typography scale. Runtime system font scaling (normal and
 * 1.5x) is applied by Android because every size is expressed in `sp`; these tests make sure
 * no width-dependent factor or fixed pixel size is reintroduced behind that promise.
 */
class BrandTypographyTest {

    @Test
    fun `reference scale stays in sp and maps to Material3 unchanged`() {
        val typography = BrandTypography.Base
        assertSp(typography.displayLarge.fontSize, 57f)
        assertSp(typography.titleLarge.fontSize, 22f)
        assertSp(typography.bodyLarge.fontSize, 16f)
        assertSp(typography.labelLarge.fontSize, 14f)
        assertSp(typography.playerTitle.fontSize, 22f)
        assertSp(typography.statusBody.fontSize, 14f)

        val material = typography.asMaterialTypography()
        assertEquals(typography.titleLarge.fontSize, material.titleLarge.fontSize)
        assertEquals(typography.bodyMedium.lineHeight, material.bodyMedium.lineHeight)
        assertEquals(typography.labelSmall.fontSize, C0reStreamTypography.labelSmall.fontSize)
    }

    @Test
    fun `all slots use sp so system font scaling including 1_5x is preserved`() {
        val t = BrandTypography.Base
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
            t.playerTitle, t.playerSubtitle, t.playerTime, t.playerMetadata, t.playerControlLabel,
            t.statusTitle, t.statusBody, t.statusLabel,
        )
        styles.forEachIndexed { index, style ->
            assertEquals("fontSize slot $index", TextUnitType.Sp, style.fontSize.type)
            if (style.lineHeight != TextUnit.Unspecified) {
                assertEquals("lineHeight slot $index", TextUnitType.Sp, style.lineHeight.type)
            }
        }
    }

    private fun assertSp(unit: TextUnit, expected: Float) {
        assertEquals(TextUnitType.Sp, unit.type)
        assertEquals(expected, unit.value, 0.01f)
    }
}

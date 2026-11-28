package pw.c0re.stream.ui.playlistdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Regression coverage for highly compressed images that declare huge dimensions. The compressed
 * byte count is deliberately small; only the declared bounds make these inputs dangerous.
 */
class SafeImageDecodeTest {

    @Test
    fun `huge declared dimensions are rejected before decode`() {
        assertFalse(SafeImageDecode.isWithinLimits(20_000, 20_000))
        assertFalse(SafeImageDecode.isWithinLimits(4_000, 100))
        assertFalse(SafeImageDecode.isWithinLimits(1_500, 1_500))
        assertFalse(SafeImageDecode.isWithinLimits(0, 100))
        assertTrue(SafeImageDecode.isWithinLimits(1_024, 1_024))
    }

    @Test
    fun `sample size keeps downscaled dimensions and pixels inside the limits`() {
        val sample = SafeImageDecode.sampleSize(20_000, 20_000, SafeImageDecode.LOGO_TARGET_PX, 1_000_000L)
        val width = 20_000 / sample
        val height = 20_000 / sample
        assertTrue("sample=$sample decoded=${width}x$height", width <= 256 && height <= 256)
        assertTrue(width.toLong() * height.toLong() <= 1_000_000L)
        assertEquals(1, SafeImageDecode.sampleSize(64, 64, SafeImageDecode.LOGO_TARGET_PX))
    }

    @Test
    fun `target size respects both QR dimension and pixel ceilings`() {
        val (width, height) = SafeImageDecode.targetSize(30_000, 10_000)
        assertTrue("target=${width}x$height", width > 0 && height > 0)
        assertTrue(width <= SafeImageDecode.MAX_DECODED_DIMENSION)
        assertTrue(height <= SafeImageDecode.MAX_DECODED_DIMENSION)
        assertTrue(width.toLong() * height.toLong() <= SafeImageDecode.MAX_DECODED_PIXELS)
        val (squareWidth, squareHeight) = SafeImageDecode.targetSize(1, 1)
        assertEquals(1, squareWidth)
        assertEquals(1, squareHeight)
    }

    @Test
    fun `decoded memory is bounded for oversized bitmaps`() {
        assertTrue(
            SafeImageDecode.decodedBytes(20_000, 20_000) > SafeImageDecode.MAX_DECODED_MEMORY_BYTES,
        )
        assertTrue(
            SafeImageDecode.decodedBytes(256, 256) <= SafeImageDecode.MAX_DECODED_MEMORY_BYTES,
        )
    }

    @Test
    fun `compressed byte limit stops an oversized stream`() {
        val bytes = ByteArray(1_024) { 1 }
        assertEquals(1_024, SafeImageDecode.readBounded(ByteArrayInputStream(bytes), 1_024).size)
        val failure = runCatching {
            SafeImageDecode.readBounded(ByteArrayInputStream(bytes), 512)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }
}

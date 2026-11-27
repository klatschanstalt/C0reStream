package pw.c0re.stream.data.profiles

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import pw.c0re.stream.data.NetworkPolicy
import java.util.EnumMap

/**
 * Decodes a QR code that contains exactly one HTTP(S) playlist URL.
 *
 * Any other payload (plain text, other schemes, embedded credentials, control characters,
 * oversized text) is rejected. Bitmap dimensions and pixel count are capped before decoding so
 * a hostile image cannot force an unbounded allocation.
 *
 * Requires `com.google.zxing:core:3.5.3`, which is not yet a project dependency. Decoding a
 * supplied [Bitmap] needs no Android permission.
 */
object QrPlaylistParser {
    const val MAX_BITMAP_DIMENSION: Int = 2_048
    const val MAX_PIXELS: Int = 2_000_000
    const val MAX_PAYLOAD_LENGTH: Int = 2_048

    /** Returns the canonical playlist URL, or `null` if no valid QR payload was found. */
    fun parse(bitmap: Bitmap): String? {
        if (bitmap.isRecycled) return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || width > MAX_BITMAP_DIMENSION || height > MAX_BITMAP_DIMENSION) {
            return null
        }
        if (width.toLong() * height.toLong() > MAX_PIXELS) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        val text = try {
            MultiFormatReader().decode(image, hints).text
        } catch (_: NotFoundException) {
            return null
        } catch (_: ChecksumException) {
            return null
        } catch (_: FormatException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return parsePayload(text)
    }

    /**
     * Validates a decoded QR string without a bitmap. Surrounding whitespace is trimmed;
     * everything else must satisfy [NetworkPolicy] (HTTP(S) only, no credentials, no controls).
     */
    fun parsePayload(payload: String): String? {
        val value = payload.trim()
        if (value.isEmpty() || value.length > MAX_PAYLOAD_LENGTH) return null
        return NetworkPolicy.parse(value)?.toString()
    }
}

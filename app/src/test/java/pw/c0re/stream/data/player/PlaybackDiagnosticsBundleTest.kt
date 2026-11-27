package pw.c0re.stream.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the payload shared by [PlaybackDiagnostics.toBundle] and
 * [PlaybackDiagnostics.fromBundle]. android.os.Bundle is a framework stub in plain JVM tests,
 * so the pure key/value layer is exercised directly; toBundle only writes these exact pairs.
 */
class PlaybackDiagnosticsBundleTest {

    @Test
    fun `every diagnostics value round trips through the bundle payload`() {
        val original = PlaybackDiagnostics(
            fps = 59.94f,
            bitrateKbps = 8_000L,
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            codec = "h264",
            decoder = "OMX.qcom.video.decoder.avc (HW)",
            bufferMs = 5_000L,
            droppedFrames = 12L,
            audioFormat = "aac 48000Hz 2ch 128kbps",
            colorInfo = "BT.2020 PQ HDR limited range",
            videoSizeInfo = "DAR 1.78:1",
            audioMime = "audio/mp4a-latm",
            videoMime = "video/avc",
            audioDecoder = "OMX.google.aac.decoder (SW)",
            audioSampleRate = 48_000,
            audioChannelCount = 2,
            videoFrameRate = 60f,
        )
        assertEquals(original, PlaybackDiagnostics.fromKeyValueMap(original.toKeyValueMap()))
    }

    @Test
    fun `original bundle keys stay unchanged and new keys are additive`() {
        val keys = PlaybackDiagnostics().toKeyValueMap().keys
        assertTrue(
            keys.containsAll(
                listOf(
                    "fps", "bitrate_kbps", "video_width", "video_height", "pixel_aspect_ratio",
                    "codec", "decoder", "buffer_ms", "dropped_frames", "audio_format",
                    "color_info", "video_size_info",
                ),
            ),
        )
        assertTrue(
            keys.containsAll(
                listOf(
                    "audio_mime", "video_mime", "audio_decoder", "audio_sample_rate",
                    "audio_channels", "video_frame_rate",
                ),
            ),
        )
        assertEquals(18, keys.size)
        assertTrue(
            PlaybackDiagnostics().toKeyValueMap().values.all {
                it is Float || it is Long || it is Int || it is String
            },
        )
    }

    @Test
    fun `malformed and unbounded values are clamped and strings stay credential free`() {
        val decoded = PlaybackDiagnostics.fromKeyValueMap(
            mapOf(
                "fps" to Float.NaN,
                "bitrate_kbps" to Long.MAX_VALUE,
                "video_width" to -20,
                "video_height" to 100_000,
                "pixel_aspect_ratio" to 0f,
                "buffer_ms" to -5L,
                "dropped_frames" to Long.MAX_VALUE,
                "audio_sample_rate" to -1,
                "audio_channels" to -3,
                "video_frame_rate" to Double.POSITIVE_INFINITY,
                "codec" to "a".repeat(400) + "\nsecret-token",
            ),
        )
        assertEquals(0f, decoded.fps, 0f)
        assertEquals(1_000_000L, decoded.bitrateKbps)
        assertEquals(0, decoded.videoWidth)
        assertEquals(16_384, decoded.videoHeight)
        assertEquals(0.1f, decoded.pixelWidthHeightRatio, 0f)
        assertEquals(0L, decoded.bufferMs)
        assertEquals(Long.MAX_VALUE / 4, decoded.droppedFrames)
        assertEquals(0, decoded.audioSampleRate)
        assertEquals(0, decoded.audioChannelCount)
        assertEquals(0f, decoded.videoFrameRate, 0f)
        assertTrue(decoded.codec.length <= PlaybackDiagnostics.MAX_TEXT)
        assertTrue(!decoded.codec.contains('\n'))
        assertTrue(!decoded.codec.contains("secret-token"))
    }

    @Test
    fun `null or empty payload yields defaults`() {
        assertEquals(PlaybackDiagnostics(), PlaybackDiagnostics.fromBundle(null))
        assertEquals(PlaybackDiagnostics(), PlaybackDiagnostics.fromKeyValueMap(emptyMap()))
    }

    @Test
    fun `decoder failure classification survives the diagnostics payload`() {
        val snapshot = PlaybackDiagnostics(
            decoder = DecoderFailureClass.INIT_LIMIT_REACHED.decorate("OMX.video.decoder (HW)"),
            audioDecoder = DecoderFailureClass.AUDIO_INIT_FAILED.decorate("FfmpegAudioRenderer"),
        )
        val restored = PlaybackDiagnostics.fromKeyValueMap(snapshot.toKeyValueMap())
        assertEquals(snapshot.decoder, restored.decoder)
        assertEquals(snapshot.audioDecoder, restored.audioDecoder)
        assertTrue(restored.decoder.endsWith(DecoderFailureClass.INIT_LIMIT_REACHED.token))
    }
}

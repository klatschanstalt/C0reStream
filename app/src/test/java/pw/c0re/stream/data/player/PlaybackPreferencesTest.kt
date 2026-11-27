package pw.c0re.stream.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPreferencesTest {

    @Test
    fun `all persisted video mode names round trip`() {
        VideoMode.entries.forEach { assertEquals(it, VideoMode.fromName(it.name)) }
    }

    @Test
    fun `unknown or missing video mode falls back to auto`() {
        assertEquals(VideoMode.AUTO, VideoMode.fromName(null))
        assertEquals(VideoMode.AUTO, VideoMode.fromName("FILL_ZOOM"))
    }

    @Test
    fun `forced aspect ratios cover only zoom and ratio modes`() {
        assertEquals(16f / 9f, VideoMode.ZOOM.forcedAspectRatio!!, 0.0001f)
        assertEquals(16f / 9f, VideoMode.RATIO_16_9.forcedAspectRatio!!, 0.0001f)
        assertEquals(4f / 3f, VideoMode.RATIO_4_3.forcedAspectRatio!!, 0.0001f)
        assertEquals(21f / 9f, VideoMode.RATIO_21_9.forcedAspectRatio!!, 0.0001f)
        listOf(VideoMode.AUTO, VideoMode.ORIGINAL, VideoMode.FIT, VideoMode.FILL, VideoMode.STRETCH)
            .forEach { assertNull(it.forcedAspectRatio) }
    }

    @Test
    fun `normalization bounds audio settings`() {
        val normalized = PlaybackSettings(
            preferredAudioLanguage = "  DE-DE!!  ",
            audioOffsetMs = 9_999,
        ).normalized()
        assertEquals("de-de", normalized.preferredAudioLanguage)
        assertEquals(PlaybackSettings.MAX_AUDIO_OFFSET_MS, normalized.audioOffsetMs)
        assertEquals(
            -PlaybackSettings.MAX_AUDIO_OFFSET_MS,
            PlaybackSettings(audioOffsetMs = -9_999).normalized().audioOffsetMs,
        )
    }

    @Test
    fun `normalization keeps other settings untouched`() {
        val settings = PlaybackSettings(
            videoMode = VideoMode.FILL,
            hardwareDecoder = false,
            decoderFallback = false,
            performanceProfile = PerformanceProfile.QUALITY,
        )
        assertEquals(settings, settings.normalized())
    }
}

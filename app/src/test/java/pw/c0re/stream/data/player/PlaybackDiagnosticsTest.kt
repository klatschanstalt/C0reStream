package pw.c0re.stream.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDiagnosticsTest {

    @Test
    fun `display aspect ratio respects pixel aspect ratio`() {
        assertEquals(
            16f / 9f,
            PlaybackDiagnostics(videoWidth = 1920, videoHeight = 1080).displayAspectRatio,
            0.0001f,
        )
        assertEquals(
            4f / 3f,
            PlaybackDiagnostics(
                videoWidth = 720,
                videoHeight = 480,
                pixelWidthHeightRatio = 8f / 9f,
            ).displayAspectRatio,
            0.0001f,
        )
    }

    @Test
    fun `unknown or invalid size has no display aspect ratio`() {
        assertEquals(0f, PlaybackDiagnostics().displayAspectRatio, 0f)
        assertEquals(
            0f,
            PlaybackDiagnostics(videoWidth = 1920, videoHeight = 1080, pixelWidthHeightRatio = 0f)
                .displayAspectRatio,
            0f,
        )
    }
}

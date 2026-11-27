package pw.c0re.stream.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderFailurePolicyTest {

    @Test
    fun `failure counter saturates per playback session`() {
        var count = 0
        repeat(20) { count = DecoderFailurePolicy.nextCount(count) }
        assertEquals(DecoderFailurePolicy.MAX_INIT_FAILURES_PER_SESSION, count)
        assertEquals(1, DecoderFailurePolicy.nextCount(-10))
    }

    @Test
    fun `classification needs an init failure and becomes terminal at the cap`() {
        assertEquals(
            DecoderFailureClass.NONE,
            DecoderFailurePolicy.classify(1, isInitFailure = false, isAudioRenderer = true),
        )
        assertEquals(
            DecoderFailureClass.NONE,
            DecoderFailurePolicy.classify(0, isInitFailure = true, isAudioRenderer = false),
        )
        assertEquals(
            DecoderFailureClass.AUDIO_INIT_FAILED,
            DecoderFailurePolicy.classify(1, isInitFailure = true, isAudioRenderer = true),
        )
        assertEquals(
            DecoderFailureClass.VIDEO_INIT_FAILED,
            DecoderFailurePolicy.classify(2, isInitFailure = true, isAudioRenderer = false),
        )
        val cap = DecoderFailurePolicy.MAX_INIT_FAILURES_PER_SESSION
        assertEquals(
            DecoderFailureClass.INIT_LIMIT_REACHED,
            DecoderFailurePolicy.classify(cap, isInitFailure = true, isAudioRenderer = true),
        )
        assertEquals(
            DecoderFailureClass.INIT_LIMIT_REACHED,
            DecoderFailurePolicy.classify(cap + 100, isInitFailure = true, isAudioRenderer = false),
        )
    }

    @Test
    fun `classification decorates existing fields without growing the bundle`() {
        val longName = "D".repeat(400)
        val decorated = DecoderFailureClass.INIT_LIMIT_REACHED.decorate(longName)
        assertTrue(decorated.length <= PlaybackDiagnostics.MAX_TEXT)
        assertTrue(decorated.endsWith(DecoderFailureClass.INIT_LIMIT_REACHED.token))
        assertEquals(
            DecoderFailureClass.AUDIO_INIT_FAILED.token,
            DecoderFailureClass.AUDIO_INIT_FAILED.decorate(""),
        )
        assertEquals(longName.take(PlaybackDiagnostics.MAX_TEXT), DecoderFailureClass.NONE.decorate(longName))
    }
}

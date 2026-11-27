package pw.c0re.stream.data.player

import android.os.Bundle

/**
 * Snapshot of publicly safe playback telemetry. Values come from Media3 player state and
 * analytics events only: no stream URL, header, token or credential is included.
 */
data class PlaybackDiagnostics(
    val fps: Float = 0f,
    val bitrateKbps: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
    val codec: String = "",
    val decoder: String = "",
    val bufferMs: Long = 0L,
    val droppedFrames: Long = 0L,
    val audioFormat: String = "",
) {
    /** Serialised into MediaSession extras so the UI can read service-side analytics. */
    fun toBundle(): Bundle = Bundle().apply {
        putFloat(KEY_FPS, fps.coerceIn(0f, 240f))
        putLong(KEY_BITRATE, bitrateKbps.coerceIn(0L, 1_000_000L))
        putInt(KEY_WIDTH, videoWidth.coerceIn(0, 16_384))
        putInt(KEY_HEIGHT, videoHeight.coerceIn(0, 16_384))
        putFloat(KEY_PAR, pixelWidthHeightRatio.coerceIn(0.1f, 10f))
        putString(KEY_CODEC, cap(codec))
        putString(KEY_DECODER, cap(decoder))
        putLong(KEY_BUFFER, bufferMs.coerceIn(0L, 3_600_000L))
        putLong(KEY_DROPPED, droppedFrames.coerceIn(0L, Long.MAX_VALUE / 4))
        putString(KEY_AUDIO, cap(audioFormat))
    }

    companion object {
        private const val KEY_FPS = "fps"
        private const val KEY_BITRATE = "bitrate_kbps"
        private const val KEY_WIDTH = "video_width"
        private const val KEY_HEIGHT = "video_height"
        private const val KEY_PAR = "pixel_aspect_ratio"
        private const val KEY_CODEC = "codec"
        private const val KEY_DECODER = "decoder"
        private const val KEY_BUFFER = "buffer_ms"
        private const val KEY_DROPPED = "dropped_frames"
        private const val KEY_AUDIO = "audio_format"
        private const val MAX_TEXT = 160

        fun fromBundle(bundle: Bundle?): PlaybackDiagnostics {
            if (bundle == null) return PlaybackDiagnostics()
            fun text(key: String): String = runCatching { bundle.getString(key).orEmpty() }
                .getOrDefault("")
                .take(MAX_TEXT)
            return PlaybackDiagnostics(
                fps = runCatching { bundle.getFloat(KEY_FPS, 0f) }.getOrDefault(0f).coerceIn(0f, 240f),
                bitrateKbps = runCatching { bundle.getLong(KEY_BITRATE, 0L) }.getOrDefault(0L).coerceIn(0L, 1_000_000L),
                videoWidth = runCatching { bundle.getInt(KEY_WIDTH, 0) }.getOrDefault(0).coerceIn(0, 16_384),
                videoHeight = runCatching { bundle.getInt(KEY_HEIGHT, 0) }.getOrDefault(0).coerceIn(0, 16_384),
                pixelWidthHeightRatio = runCatching { bundle.getFloat(KEY_PAR, 1f) }.getOrDefault(1f).coerceIn(0.1f, 10f),
                codec = text(KEY_CODEC),
                decoder = text(KEY_DECODER),
                bufferMs = runCatching { bundle.getLong(KEY_BUFFER, 0L) }.getOrDefault(0L).coerceIn(0L, 3_600_000L),
                droppedFrames = runCatching { bundle.getLong(KEY_DROPPED, 0L) }.getOrDefault(0L).coerceIn(0L, Long.MAX_VALUE / 4),
                audioFormat = text(KEY_AUDIO),
            )
        }

        private fun cap(value: String): String = value.take(MAX_TEXT)
    }
}

package pw.c0re.stream.data.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackPreferencesDataStore by preferencesDataStore("c0restream_playback_preferences")

/** How video is fitted into the player surface. AUTO and FIT never crop the decoded image. */
enum class VideoMode {
    AUTO,
    ORIGINAL,
    FIT,
    FILL,
    ZOOM,
    STRETCH,
    RATIO_16_9,
    RATIO_4_3,
    RATIO_21_9;

    /** A forced output frame ratio, or null to derive the frame from the decoded video. */
    val forcedAspectRatio: Float?
        get() = when (this) {
            RATIO_16_9 -> 16f / 9f
            RATIO_4_3 -> 4f / 3f
            RATIO_21_9 -> 21f / 9f
            ZOOM -> 16f / 9f
            else -> null
        }

    companion object {
        fun fromName(value: String?): VideoMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/** Buffering/power strategy applied when the playback service creates its player. */
enum class PerformanceProfile {
    AUTO,
    PERFORMANCE,
    QUALITY,
    LOW_LATENCY,
    STABLE,
    POWER_SAVE;

    companion object {
        fun fromName(value: String?): PerformanceProfile =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * Playback settings shared by global and per-channel scopes. A per-channel scope stores only
 * explicit overrides and falls back field-by-field to the global value.
 */
data class PlaybackSettings(
    val videoMode: VideoMode = VideoMode.AUTO,
    val hardwareDecoder: Boolean = true,
    val decoderFallback: Boolean = true,
    val preferredAudioLanguage: String = "",
    val audioOffsetMs: Long = 0L,
    val performanceProfile: PerformanceProfile = PerformanceProfile.AUTO,
) {
    fun normalized(): PlaybackSettings = copy(
        preferredAudioLanguage = preferredAudioLanguage
            .trim()
            .lowercase()
            .take(35)
            .filter { it.isLetterOrDigit() || it == '-' },
        audioOffsetMs = audioOffsetMs.coerceIn(-MAX_AUDIO_OFFSET_MS, MAX_AUDIO_OFFSET_MS),
    )

    companion object {
        const val MAX_AUDIO_OFFSET_MS = 5_000L
    }
}

/**
 * DataStore-backed playback preferences.
 *
 * Channel keys are opaque identifiers produced by [channelKey]; no stream URL, token or
 * credential is ever written into this store.
 */
class PlaybackPreferences(private val context: Context) {
    private val store = context.playbackPreferencesDataStore

    val settings: Flow<PlaybackSettings> = store.data.map { read(it, null) }

    fun settingsFor(channelKey: String?): Flow<PlaybackSettings> =
        if (channelKey.isNullOrBlank()) settings
        else store.data.map { read(it, safeKey(channelKey)) }

    suspend fun snapshot(): PlaybackSettings = settings.first()

    suspend fun snapshotFor(channelKey: String?): PlaybackSettings = settingsFor(channelKey).first()

    suspend fun setVideoMode(value: VideoMode, channelKey: String? = null) =
        editString(VIDEO_MODE, value.name, channelKey)

    suspend fun setHardwareDecoder(enabled: Boolean, channelKey: String? = null) =
        editBoolean(HARDWARE_DECODER, enabled, channelKey)

    suspend fun setDecoderFallback(enabled: Boolean, channelKey: String? = null) =
        editBoolean(DECODER_FALLBACK, enabled, channelKey)

    suspend fun setPreferredAudioLanguage(language: String, channelKey: String? = null) =
        editString(AUDIO_LANGUAGE, language, channelKey)

    suspend fun setAudioOffsetMs(offsetMs: Long, channelKey: String? = null) =
        editLong(AUDIO_OFFSET_MS, offsetMs, channelKey)

    suspend fun setPerformanceProfile(value: PerformanceProfile, channelKey: String? = null) =
        editString(PERFORMANCE_PROFILE, value.name, channelKey)

    /** Removes all per-channel overrides so this channel inherits the global settings again. */
    suspend fun clearChannel(channelKey: String) {
        val safe = safeKey(channelKey)
        store.edit { prefs ->
            listOf(VIDEO_MODE, HARDWARE_DECODER, DECODER_FALLBACK, AUDIO_LANGUAGE, AUDIO_OFFSET_MS, PERFORMANCE_PROFILE)
                .forEach { field ->
                    when (field) {
                        AUDIO_LANGUAGE, VIDEO_MODE, PERFORMANCE_PROFILE -> prefs.remove(stringKey(field, safe))
                        HARDWARE_DECODER, DECODER_FALLBACK -> prefs.remove(booleanKey(field, safe))
                        AUDIO_OFFSET_MS -> prefs.remove(longKey(field, safe))
                    }
                }
        }
    }

    private suspend fun editString(field: String, value: String, channelKey: String?) {
        val normalized = if (field == AUDIO_LANGUAGE) value else value.take(64)
        store.edit { it[stringKey(field, channelKey)] = normalized }
    }

    private suspend fun editBoolean(field: String, value: Boolean, channelKey: String?) {
        store.edit { it[booleanKey(field, channelKey)] = value }
    }

    private suspend fun editLong(field: String, value: Long, channelKey: String?) {
        store.edit { it[longKey(field, channelKey)] = value }
    }

    private fun read(prefs: androidx.datastore.preferences.core.Preferences, channelKey: String?): PlaybackSettings {
        fun string(field: String, fallback: String): String =
            prefs[stringKey(field, channelKey)] ?: prefs[stringKey(field, null)] ?: fallback

        fun boolean(field: String, fallback: Boolean): Boolean =
            prefs[booleanKey(field, channelKey)] ?: prefs[booleanKey(field, null)] ?: fallback

        fun long(field: String, fallback: Long): Long =
            prefs[longKey(field, channelKey)] ?: prefs[longKey(field, null)] ?: fallback

        return PlaybackSettings(
            videoMode = VideoMode.fromName(string(VIDEO_MODE, VideoMode.AUTO.name)),
            hardwareDecoder = boolean(HARDWARE_DECODER, true),
            decoderFallback = boolean(DECODER_FALLBACK, true),
            preferredAudioLanguage = string(AUDIO_LANGUAGE, ""),
            audioOffsetMs = long(AUDIO_OFFSET_MS, 0L),
            performanceProfile = PerformanceProfile.fromName(
                string(PERFORMANCE_PROFILE, PerformanceProfile.AUTO.name),
            ),
        ).normalized()
    }

    private fun fieldKey(field: String, channelKey: String?): String =
        if (channelKey.isNullOrBlank()) "global.$field" else "channel.${safeKey(channelKey)}.$field"

    private fun stringKey(field: String, channelKey: String?) =
        stringPreferencesKey(fieldKey(field, channelKey))

    private fun booleanKey(field: String, channelKey: String?) =
        booleanPreferencesKey(fieldKey(field, channelKey))

    private fun longKey(field: String, channelKey: String?) =
        longPreferencesKey(fieldKey(field, channelKey))

    private fun safeKey(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

    companion object {
        private const val VIDEO_MODE = "video_mode"
        private const val HARDWARE_DECODER = "hardware_decoder"
        private const val DECODER_FALLBACK = "decoder_fallback"
        private const val AUDIO_LANGUAGE = "audio_language"
        private const val AUDIO_OFFSET_MS = "audio_offset_ms"
        private const val PERFORMANCE_PROFILE = "performance_profile"

        /** Opaque, URL-free identity for per-channel overrides. */
        fun channelKey(scope: String, id: Long): String = "ch_${safe(scope)}_${id}"

        private fun safe(value: String): String =
            value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)
    }
}

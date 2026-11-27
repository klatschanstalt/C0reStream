package pw.c0re.stream.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File

private val Context.c0reSettings by preferencesDataStore("c0restream_settings")

data class AppSettings(
    val themeMode: String = "DARK",
    val brand: String = "HAM3L",
    val minBufferSec: Int = 50,
    val maxBufferSec: Int = 120,
    val autoRefresh: Boolean = true,
    val refreshHours: Int = 12,
    val expertMode: Boolean = false,
    val videoMode: String = "AUTO",
    val audioLanguage: String = "auto",
    val audioOffsetMs: Int = 0,
    val hwDecoder: Boolean = true,
    val decoderFallback: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val subtitleLanguage: String = "auto",
    val connectTimeoutSec: Int = 15,
    val fullscreen: Boolean = true,
    val performanceProfile: String = "AUTO",
    val gestureSeek: Boolean = true,
    val epgEnabled: Boolean = true,
) {
    fun normalized(): AppSettings {
        val normalizedMin = minBufferSec.coerceIn(5, 300)
        return copy(
            themeMode = themeMode.takeIf { it in setOf("DARK", "LIGHT", "SYSTEM", "AMOLED") } ?: "DARK",
            brand = brand.takeIf { it in setOf("HAM3L", "EMBER") } ?: "HAM3L",
            minBufferSec = normalizedMin,
            maxBufferSec = maxBufferSec.coerceIn(normalizedMin, 600),
            refreshHours = refreshHours.coerceIn(1, 168),
            videoMode = videoMode.takeIf { it in VIDEO_MODES } ?: "AUTO",
            audioLanguage = audioLanguage.take(64).ifBlank { "auto" },
            audioOffsetMs = audioOffsetMs.coerceIn(-2_000, 2_000),
            subtitleLanguage = subtitleLanguage.take(64).ifBlank { "auto" },
            connectTimeoutSec = connectTimeoutSec.coerceIn(5, 120),
            performanceProfile = performanceProfile.takeIf { it in PERFORMANCE_PROFILES } ?: "AUTO",
        )
    }

    companion object {
        val VIDEO_MODES = listOf(
            "AUTO", "ORIGINAL", "FIT", "FILL", "ZOOM", "STRETCH", "RATIO_16_9", "RATIO_4_3", "RATIO_21_9",
        )
        val PERFORMANCE_PROFILES = listOf(
            "AUTO", "PERFORMANCE", "QUALITY", "LOW_LATENCY", "STABLE", "POWER",
        )
    }
}

/** Single source of truth for user preferences and the settings part of encrypted backups. */
class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val brandKey = stringPreferencesKey("brand")
    private val minBufferKey = intPreferencesKey("min_buffer_sec")
    private val maxBufferKey = intPreferencesKey("max_buffer_sec")
    private val autoRefreshKey = booleanPreferencesKey("auto_refresh")
    private val refreshHoursKey = intPreferencesKey("refresh_hours")
    private val expertModeKey = booleanPreferencesKey("expert_mode")
    private val videoModeKey = stringPreferencesKey("video_mode")
    private val audioLanguageKey = stringPreferencesKey("audio_language")
    private val audioOffsetKey = intPreferencesKey("audio_offset_ms")
    private val hwDecoderKey = booleanPreferencesKey("hw_decoder")
    private val decoderFallbackKey = booleanPreferencesKey("decoder_fallback")
    private val subtitlesEnabledKey = booleanPreferencesKey("subtitles_enabled")
    private val subtitleLanguageKey = stringPreferencesKey("subtitle_language")
    private val connectTimeoutKey = intPreferencesKey("connect_timeout_sec")
    private val fullscreenKey = booleanPreferencesKey("fullscreen")
    private val performanceProfileKey = stringPreferencesKey("performance_profile")
    private val gestureSeekKey = booleanPreferencesKey("gesture_seek")
    private val epgEnabledKey = booleanPreferencesKey("epg_enabled")

    val settings: Flow<AppSettings> = context.c0reSettings.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeKey] ?: "DARK",
            brand = prefs[brandKey] ?: "HAM3L",
            minBufferSec = prefs[minBufferKey] ?: 50,
            maxBufferSec = prefs[maxBufferKey] ?: 120,
            autoRefresh = prefs[autoRefreshKey] ?: true,
            refreshHours = prefs[refreshHoursKey] ?: 12,
            expertMode = prefs[expertModeKey] ?: false,
            videoMode = prefs[videoModeKey] ?: "AUTO",
            audioLanguage = prefs[audioLanguageKey] ?: "auto",
            audioOffsetMs = prefs[audioOffsetKey] ?: 0,
            hwDecoder = prefs[hwDecoderKey] ?: true,
            decoderFallback = prefs[decoderFallbackKey] ?: true,
            subtitlesEnabled = prefs[subtitlesEnabledKey] ?: true,
            subtitleLanguage = prefs[subtitleLanguageKey] ?: "auto",
            connectTimeoutSec = prefs[connectTimeoutKey] ?: 15,
            fullscreen = prefs[fullscreenKey] ?: true,
            performanceProfile = prefs[performanceProfileKey] ?: "AUTO",
            gestureSeek = prefs[gestureSeekKey] ?: true,
            epgEnabled = prefs[epgEnabledKey] ?: true,
        ).normalized()
    }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun setThemeMode(value: String) = context.c0reSettings.edit { it[themeKey] = value }
    suspend fun setBrand(value: String) = context.c0reSettings.edit { it[brandKey] = value }

    suspend fun setBuffer(minSec: Int, maxSec: Int) = context.c0reSettings.edit { prefs ->
        val min = minSec.coerceIn(5, 300)
        prefs[minBufferKey] = min
        prefs[maxBufferKey] = maxSec.coerceIn(min, 600)
    }

    suspend fun setAutoRefresh(enabled: Boolean) = context.c0reSettings.edit { it[autoRefreshKey] = enabled }
    suspend fun setRefreshHours(hours: Int) = context.c0reSettings.edit { it[refreshHoursKey] = hours.coerceIn(1, 168) }

    suspend fun setExpertMode(enabled: Boolean) = context.c0reSettings.edit { it[expertModeKey] = enabled }

    suspend fun setVideoMode(value: String) = context.c0reSettings.edit { prefs ->
        prefs[videoModeKey] = value.takeIf { it in AppSettings.VIDEO_MODES } ?: "AUTO"
    }

    suspend fun setAudioLanguage(value: String) = context.c0reSettings.edit { prefs ->
        prefs[audioLanguageKey] = value.take(64).ifBlank { "auto" }
    }

    suspend fun setAudioOffsetMs(value: Int) = context.c0reSettings.edit { prefs ->
        prefs[audioOffsetKey] = value.coerceIn(-2_000, 2_000)
    }

    suspend fun setHwDecoder(enabled: Boolean) = context.c0reSettings.edit { it[hwDecoderKey] = enabled }
    suspend fun setDecoderFallback(enabled: Boolean) = context.c0reSettings.edit { it[decoderFallbackKey] = enabled }
    suspend fun setSubtitlesEnabled(enabled: Boolean) = context.c0reSettings.edit { it[subtitlesEnabledKey] = enabled }

    suspend fun setSubtitleLanguage(value: String) = context.c0reSettings.edit { prefs ->
        prefs[subtitleLanguageKey] = value.take(64).ifBlank { "auto" }
    }

    suspend fun setConnectTimeoutSec(value: Int) = context.c0reSettings.edit { prefs ->
        prefs[connectTimeoutKey] = value.coerceIn(5, 120)
    }

    suspend fun setFullscreen(enabled: Boolean) = context.c0reSettings.edit { it[fullscreenKey] = enabled }

    suspend fun setPerformanceProfile(value: String) = context.c0reSettings.edit { prefs ->
        prefs[performanceProfileKey] = value.takeIf { it in AppSettings.PERFORMANCE_PROFILES } ?: "AUTO"
    }

    suspend fun setGestureSeek(enabled: Boolean) = context.c0reSettings.edit { it[gestureSeekKey] = enabled }
    suspend fun setEpgEnabled(enabled: Boolean) = context.c0reSettings.edit { it[epgEnabledKey] = enabled }

    /** Reset player presentation preferences only. Library, favorites and history are untouched. */
    suspend fun resetVideo() = context.c0reSettings.edit { prefs ->
        prefs[videoModeKey] = "AUTO"
        prefs[audioOffsetKey] = 0
        prefs[hwDecoderKey] = true
        prefs[decoderFallbackKey] = true
        prefs[fullscreenKey] = true
        prefs[performanceProfileKey] = "AUTO"
    }

    /** Clear all keys so the defaults declared in [AppSettings] apply again. */
    suspend fun resetToDefaults() = context.c0reSettings.edit { it.clear() }

    fun exportJson(value: AppSettings): JSONObject = JSONObject()
        .put("themeMode", value.themeMode)
        .put("brand", value.brand)
        .put("minBufferSec", value.minBufferSec)
        .put("maxBufferSec", value.maxBufferSec)
        .put("autoRefresh", value.autoRefresh)
        .put("refreshHours", value.refreshHours)
        .put("expertMode", value.expertMode)
        .put("videoMode", value.videoMode)
        .put("audioLanguage", value.audioLanguage)
        .put("audioOffsetMs", value.audioOffsetMs)
        .put("hwDecoder", value.hwDecoder)
        .put("decoderFallback", value.decoderFallback)
        .put("subtitlesEnabled", value.subtitlesEnabled)
        .put("subtitleLanguage", value.subtitleLanguage)
        .put("connectTimeoutSec", value.connectTimeoutSec)
        .put("fullscreen", value.fullscreen)
        .put("performanceProfile", value.performanceProfile)
        .put("gestureSeek", value.gestureSeek)
        .put("epgEnabled", value.epgEnabled)

    suspend fun restoreJson(value: JSONObject?) {
        if (value == null) return
        val defaults = AppSettings()
        val restored = AppSettings(
            themeMode = value.optString("themeMode", defaults.themeMode),
            brand = value.optString("brand", defaults.brand),
            minBufferSec = value.optInt("minBufferSec", defaults.minBufferSec),
            maxBufferSec = value.optInt("maxBufferSec", defaults.maxBufferSec),
            autoRefresh = value.optBoolean("autoRefresh", defaults.autoRefresh),
            refreshHours = value.optInt("refreshHours", defaults.refreshHours),
            expertMode = value.optBoolean("expertMode", defaults.expertMode),
            videoMode = value.optString("videoMode", defaults.videoMode),
            audioLanguage = value.optString("audioLanguage", defaults.audioLanguage),
            audioOffsetMs = value.optInt("audioOffsetMs", defaults.audioOffsetMs),
            hwDecoder = value.optBoolean("hwDecoder", defaults.hwDecoder),
            decoderFallback = value.optBoolean("decoderFallback", defaults.decoderFallback),
            subtitlesEnabled = value.optBoolean("subtitlesEnabled", defaults.subtitlesEnabled),
            subtitleLanguage = value.optString("subtitleLanguage", defaults.subtitleLanguage),
            connectTimeoutSec = value.optInt("connectTimeoutSec", defaults.connectTimeoutSec),
            fullscreen = value.optBoolean("fullscreen", defaults.fullscreen),
            performanceProfile = value.optString("performanceProfile", defaults.performanceProfile),
            gestureSeek = value.optBoolean("gestureSeek", defaults.gestureSeek),
            epgEnabled = value.optBoolean("epgEnabled", defaults.epgEnabled),
        ).normalized()
        context.c0reSettings.edit { prefs ->
            prefs[themeKey] = restored.themeMode
            prefs[brandKey] = restored.brand
            prefs[minBufferKey] = restored.minBufferSec
            prefs[maxBufferKey] = restored.maxBufferSec
            prefs[autoRefreshKey] = restored.autoRefresh
            prefs[refreshHoursKey] = restored.refreshHours
            prefs[expertModeKey] = restored.expertMode
            prefs[videoModeKey] = restored.videoMode
            prefs[audioLanguageKey] = restored.audioLanguage
            prefs[audioOffsetKey] = restored.audioOffsetMs
            prefs[hwDecoderKey] = restored.hwDecoder
            prefs[decoderFallbackKey] = restored.decoderFallback
            prefs[subtitlesEnabledKey] = restored.subtitlesEnabled
            prefs[subtitleLanguageKey] = restored.subtitleLanguage
            prefs[connectTimeoutKey] = restored.connectTimeoutSec
            prefs[fullscreenKey] = restored.fullscreen
            prefs[performanceProfileKey] = restored.performanceProfile
            prefs[gestureSeekKey] = restored.gestureSeek
            prefs[epgEnabledKey] = restored.epgEnabled
        }
    }

    companion object {
        /**
         * The previous release stored preferences under a legacy file name. Copy the DataStore
         * file once before the first repository access so theme and buffer settings survive the
         * rebranding. Failure is non-fatal; defaults are safe.
         */
        fun migrateLegacyPreferences(context: Context) {
            runCatching {
                val directory = File(context.filesDir, "datastore")
                val legacy = File(directory, "ham3l_settings.preferences_pb")
                val target = File(directory, "c0restream_settings.preferences_pb")
                if (legacy.exists() && !target.exists()) {
                    directory.mkdirs()
                    legacy.copyTo(target, overwrite = false)
                }
            }
        }
    }
}

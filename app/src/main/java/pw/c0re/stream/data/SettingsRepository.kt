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
) {
    fun normalized(): AppSettings {
        val normalizedMin = minBufferSec.coerceIn(5, 300)
        return copy(
            themeMode = themeMode.takeIf { it in setOf("DARK", "LIGHT", "SYSTEM", "AMOLED") } ?: "DARK",
            brand = brand.takeIf { it in setOf("HAM3L", "EMBER") } ?: "HAM3L",
            minBufferSec = normalizedMin,
            maxBufferSec = maxBufferSec.coerceIn(normalizedMin, 600),
            refreshHours = refreshHours.coerceIn(1, 168),
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

    val settings: Flow<AppSettings> = context.c0reSettings.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeKey] ?: "DARK",
            brand = prefs[brandKey] ?: "HAM3L",
            minBufferSec = prefs[minBufferKey] ?: 50,
            maxBufferSec = prefs[maxBufferKey] ?: 120,
            autoRefresh = prefs[autoRefreshKey] ?: true,
            refreshHours = prefs[refreshHoursKey] ?: 12,
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

    fun exportJson(value: AppSettings): JSONObject = JSONObject()
        .put("themeMode", value.themeMode)
        .put("brand", value.brand)
        .put("minBufferSec", value.minBufferSec)
        .put("maxBufferSec", value.maxBufferSec)
        .put("autoRefresh", value.autoRefresh)
        .put("refreshHours", value.refreshHours)

    suspend fun restoreJson(value: JSONObject?) {
        if (value == null) return
        val restored = AppSettings(
            themeMode = value.optString("themeMode", "DARK"),
            brand = value.optString("brand", "HAM3L"),
            minBufferSec = value.optInt("minBufferSec", 50),
            maxBufferSec = value.optInt("maxBufferSec", 120),
            autoRefresh = value.optBoolean("autoRefresh", true),
            refreshHours = value.optInt("refreshHours", 12),
        ).normalized()
        context.c0reSettings.edit { prefs ->
            prefs[themeKey] = restored.themeMode
            prefs[brandKey] = restored.brand
            prefs[minBufferKey] = restored.minBufferSec
            prefs[maxBufferKey] = restored.maxBufferSec
            prefs[autoRefreshKey] = restored.autoRefresh
            prefs[refreshHoursKey] = restored.refreshHours
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

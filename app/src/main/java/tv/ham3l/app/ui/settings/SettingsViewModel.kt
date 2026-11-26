package tv.ham3l.app.ui.settings

import tv.ham3l.app.ui.theme.Brand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.ham3l.app.Ham3LApp
import tv.ham3l.app.data.AppLog
import tv.ham3l.app.ui.theme.ThemeMode

import android.content.Context

private val Context.dataStore by preferencesDataStore("ham3l_settings")

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val brandKey = stringPreferencesKey("brand")
    private val minBufferKey = intPreferencesKey("min_buffer_sec")
    private val maxBufferKey = intPreferencesKey("max_buffer_sec")

    val themeMode = getApplication<Ham3LApp>().dataStore.data
        .map { prefs -> prefs[themeKey]?.let { m -> runCatching { ThemeMode.valueOf(m) }.getOrNull() } ?: ThemeMode.DARK }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val brand = getApplication<Ham3LApp>().dataStore.data
        .map { prefs -> prefs[brandKey]?.let { b -> runCatching { Brand.valueOf(b) }.getOrNull() } ?: Brand.HAM3L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Brand.HAM3L)

    val minBufferSec = getApplication<Ham3LApp>().dataStore.data
        .map { it[minBufferKey] ?: 50 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 50)

    val maxBufferSec = getApplication<Ham3LApp>().dataStore.data
        .map { it[maxBufferKey] ?: 120 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 120)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        getApplication<Ham3LApp>().dataStore.edit { it[themeKey] = mode.name }
    }

    fun setBrand(brand: Brand) = viewModelScope.launch {
        getApplication<Ham3LApp>().dataStore.edit { it[brandKey] = brand.name }
    }

    fun setBuffer(minSec: Int, maxSec: Int) = viewModelScope.launch {
        getApplication<Ham3LApp>().dataStore.edit {
            it[minBufferKey] = minSec.coerceIn(10, 300)
            it[maxBufferKey] = maxSec.coerceIn(30, 600)
        }
        AppLog.log("Settings", "Puffer gesetzt: " + minSec + "s / " + maxSec + "s (wirkt nach App-Neustart)")
    }
}

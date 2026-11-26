package pw.c0re.stream.ui.settings

import pw.c0re.stream.ui.theme.Brand

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
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.ui.theme.ThemeMode

import android.content.Context

private val Context.dataStore by preferencesDataStore("ham3l_settings")

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val brandKey = stringPreferencesKey("brand")
    private val minBufferKey = intPreferencesKey("min_buffer_sec")
    private val maxBufferKey = intPreferencesKey("max_buffer_sec")

    val themeMode = getApplication<C0reStreamApp>().dataStore.data
        .map { prefs -> prefs[themeKey]?.let { m -> runCatching { ThemeMode.valueOf(m) }.getOrNull() } ?: ThemeMode.DARK }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val brand = getApplication<C0reStreamApp>().dataStore.data
        .map { prefs -> prefs[brandKey]?.let { b -> runCatching { Brand.valueOf(b) }.getOrNull() } ?: Brand.HAM3L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Brand.HAM3L)

    val minBufferSec = getApplication<C0reStreamApp>().dataStore.data
        .map { it[minBufferKey] ?: 50 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 50)

    val maxBufferSec = getApplication<C0reStreamApp>().dataStore.data
        .map { it[maxBufferKey] ?: 120 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 120)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        getApplication<C0reStreamApp>().dataStore.edit { it[themeKey] = mode.name }
    }

    fun setBrand(brand: Brand) = viewModelScope.launch {
        getApplication<C0reStreamApp>().dataStore.edit { it[brandKey] = brand.name }
    }

    fun setBuffer(minSec: Int, maxSec: Int) = viewModelScope.launch {
        getApplication<C0reStreamApp>().dataStore.edit {
            it[minBufferKey] = minSec.coerceIn(10, 300)
            it[maxBufferKey] = maxSec.coerceIn(30, 600)
        }
        AppLog.log("Settings", "Puffer gesetzt: " + minSec + "s / " + maxSec + "s (wirkt nach App-Neustart)")
    }
}

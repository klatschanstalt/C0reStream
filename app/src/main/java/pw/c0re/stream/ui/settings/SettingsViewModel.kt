package pw.c0re.stream.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.AppSettings
import pw.c0re.stream.ui.theme.Brand
import pw.c0re.stream.ui.theme.ThemeMode
import pw.c0re.stream.work.PlaylistRefreshWorker

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app as C0reStreamApp
    private val repository = appContext.settings

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        repository.setThemeMode(mode.name)
    }

    fun setBrand(brand: Brand) = viewModelScope.launch {
        repository.setBrand(brand.name)
    }

    fun setBuffer(minSec: Int, maxSec: Int) = viewModelScope.launch {
        repository.setBuffer(minSec, maxSec)
        AppLog.log("Settings", "Buffer set: ${minSec.coerceIn(5, 300)}s / ${maxSec.coerceIn(5, 600)}s")
    }

    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch {
        repository.setAutoRefresh(enabled)
        val current = repository.settings.first()
        PlaylistRefreshWorker.schedule(appContext, current.refreshHours, enabled)
    }

    fun setRefreshHours(hours: Int) = viewModelScope.launch {
        val normalized = hours.coerceIn(1, 168)
        repository.setRefreshHours(normalized)
        val current = repository.settings.first()
        PlaylistRefreshWorker.schedule(appContext, normalized, current.autoRefresh)
    }

    fun setExpertMode(enabled: Boolean) = viewModelScope.launch {
        repository.setExpertMode(enabled)
    }

    fun setVideoMode(value: String) = viewModelScope.launch {
        repository.setVideoMode(value)
    }

    fun setAudioLanguage(value: String) = viewModelScope.launch {
        repository.setAudioLanguage(value)
    }

    fun setAudioOffsetMs(value: Int) = viewModelScope.launch {
        repository.setAudioOffsetMs(value)
    }

    fun setHwDecoder(enabled: Boolean) = viewModelScope.launch {
        repository.setHwDecoder(enabled)
    }

    fun setDecoderFallback(enabled: Boolean) = viewModelScope.launch {
        repository.setDecoderFallback(enabled)
    }

    fun setSubtitlesEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setSubtitlesEnabled(enabled)
    }

    fun setSubtitleLanguage(value: String) = viewModelScope.launch {
        repository.setSubtitleLanguage(value)
    }

    fun setConnectTimeoutSec(value: Int) = viewModelScope.launch {
        repository.setConnectTimeoutSec(value)
    }

    fun setFullscreen(enabled: Boolean) = viewModelScope.launch {
        repository.setFullscreen(enabled)
    }

    fun setPerformanceProfile(value: String) = viewModelScope.launch {
        repository.setPerformanceProfile(value)
    }

    fun setGestureSeek(enabled: Boolean) = viewModelScope.launch {
        repository.setGestureSeek(enabled)
    }

    fun setEpgEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setEpgEnabled(enabled)
    }

    fun resetVideo() = viewModelScope.launch {
        repository.resetVideo()
        AppLog.log("Settings", "Video settings reset to defaults")
    }

    fun resetToDefaults() = viewModelScope.launch {
        repository.resetToDefaults()
        AppLog.log("Settings", "All preferences reset to defaults")
        val defaults = AppSettings()
        PlaylistRefreshWorker.schedule(appContext, defaults.refreshHours, defaults.autoRefresh)
    }
}

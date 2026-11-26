package pw.c0re.stream.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    val history = db.historyDao().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun clear() = viewModelScope.launch { db.historyDao().clear() }
}

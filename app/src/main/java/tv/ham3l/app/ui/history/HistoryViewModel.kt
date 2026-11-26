package tv.ham3l.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.ham3l.app.Ham3LApp

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as Ham3LApp).database
    val history = db.historyDao().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun clear() = viewModelScope.launch { db.historyDao().clear() }
}

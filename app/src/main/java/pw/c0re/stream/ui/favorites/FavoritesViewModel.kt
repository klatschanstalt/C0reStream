package pw.c0re.stream.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import pw.c0re.stream.C0reStreamApp

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database
    val favorites = db.favoriteDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

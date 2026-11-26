package tv.ham3l.app.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tv.ham3l.app.Ham3LApp

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as Ham3LApp).database
    val favorites = db.favoriteDao().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

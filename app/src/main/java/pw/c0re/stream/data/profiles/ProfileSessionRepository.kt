package pw.c0re.stream.data.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileSession by preferencesDataStore("c0restream_profile_session")

/** Active local profile and in-memory unlock state. PINs never leave PinManager. */
class ProfileSessionRepository(private val context: Context) {
    private val activeKey = longPreferencesKey("active_profile_id")

    val activeId: Flow<Long> = context.profileSession.data.map { it[activeKey] ?: 0L }

    suspend fun setActive(id: Long) = context.profileSession.edit { it[activeKey] = id }

    companion object {
        val pinManager = PinManager()
    }
}

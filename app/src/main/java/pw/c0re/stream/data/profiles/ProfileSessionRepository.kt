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

    /**
     * Distinguishes "no profile selected yet" from "profile 0 selected". The gate must not open
     * on DataStore's initial empty snapshot when a protected profile is stored on disk.
     */
    val activeProfile: Flow<ActiveProfile> = context.profileSession.data.map { preferences ->
        val id = preferences[activeKey]
        if (id == null) ActiveProfile.Unset else ActiveProfile.Selected(id)
    }

    suspend fun setActive(id: Long) = context.profileSession.edit { it[activeKey] = id }

    companion object {
        val pinManager = PinManager()
    }
}

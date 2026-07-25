package app.vera.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vera_settings")

/** Persists which news sources the user enabled. Empty set = "use the catalog defaults". */
class SettingsRepository(private val context: Context) {

    private val enabledKey = stringSetPreferencesKey("enabled_source_ids")

    val enabledSourceIds: Flow<Set<String>> =
        context.dataStore.data.map { it[enabledKey] ?: emptySet() }

    suspend fun toggleSource(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[enabledKey]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(id) else current.remove(id)
            prefs[enabledKey] = current
        }
    }
}

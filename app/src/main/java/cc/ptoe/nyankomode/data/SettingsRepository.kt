package cc.ptoe.nyankomode.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val totalEnabledKey = booleanPreferencesKey("total_enabled")
    private val excludedAppsKey = stringSetPreferencesKey("excluded_apps")

    val totalEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[totalEnabledKey] ?: true
    }

    suspend fun setTotalEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[totalEnabledKey] = enabled
        }
    }

    val excludedApps: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[excludedAppsKey] ?: emptySet()
    }

    suspend fun setExcludedApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            prefs[excludedAppsKey] = apps
        }
    }
}
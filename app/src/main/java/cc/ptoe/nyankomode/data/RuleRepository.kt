package cc.ptoe.nyankomode.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json

class RuleRepository(private val dataStore: DataStore<Preferences>) {

    private val rulesKey = stringPreferencesKey("rules_json")

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val rules: Flow<List<MappingRule>> = dataStore.data
        .map { prefs -> prefs[rulesKey] }
        .distinctUntilChanged()
        .map { payload ->
            if (payload == null) emptyList()
            else runCatching { json.decodeFromString<List<MappingRule>>(payload) }
                .getOrElse { emptyList() }
        }

    suspend fun upsert(rule: MappingRule) {
        dataStore.edit { prefs ->
            val current = decode(prefs[rulesKey])
            val updated = replace(current, rule)
            prefs[rulesKey] = encode(updated)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[rulesKey])
            val updated = current.filterNot { it.id == id }
            prefs[rulesKey] = encode(updated)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = decode(prefs[rulesKey])
            val updated = current.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            prefs[rulesKey] = encode(updated)
        }
    }

    private fun decode(payload: String?): List<MappingRule> {
        if (payload == null) return emptyList()
        return runCatching { json.decodeFromString<List<MappingRule>>(payload) }.getOrElse { emptyList() }
    }

    private fun replace(current: List<MappingRule>, rule: MappingRule): List<MappingRule> {
        val index = current.indexOfFirst { it.id == rule.id }
        return if (index >= 0) {
            current.toMutableList().apply { set(index, rule) }
        } else {
            current + rule
        }
    }

    private fun encode(rules: List<MappingRule>): String = json.encodeToString(rules)
}
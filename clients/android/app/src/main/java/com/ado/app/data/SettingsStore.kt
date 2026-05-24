package com.ado.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ado.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val DEFAULT_SERVER_URL: String = BuildConfig.DEFAULT_SERVER_URL

private val Context.adoDataStore by preferencesDataStore(name = "ado_settings")

class SettingsStore(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val offlineModeKey = booleanPreferencesKey("offline_mode")
    private val rollUpCompletedKey = booleanPreferencesKey("roll_up_completed")

    val serverUrlFlow: Flow<String> = context.adoDataStore.data.map { preferences ->
        preferences[serverUrlKey] ?: DEFAULT_SERVER_URL
    }

    val offlineModeFlow: Flow<Boolean> = context.adoDataStore.data.map { preferences ->
        preferences[offlineModeKey] ?: false
    }

    val rollUpCompletedFlow: Flow<Boolean> = context.adoDataStore.data.map { preferences ->
        preferences[rollUpCompletedKey] ?: true
    }

    suspend fun saveServerUrl(raw: String) {
        val normalized = normalizeServerUrl(raw)
        context.adoDataStore.edit { preferences ->
            preferences[serverUrlKey] = normalized
        }
    }

    suspend fun saveOfflineMode(enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[offlineModeKey] = enabled
        }
    }

    suspend fun saveRollUpCompleted(enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[rollUpCompletedKey] = enabled
        }
    }
}

fun normalizeServerUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isBlank()) return DEFAULT_SERVER_URL
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

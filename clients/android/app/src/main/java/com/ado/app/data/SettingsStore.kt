package com.ado.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adoDataStore by preferencesDataStore(name = "ado_settings")

class SettingsStore(private val context: Context) {
    private val rollUpCompletedKey = booleanPreferencesKey("roll_up_completed")

    val rollUpCompletedFlow: Flow<Boolean> = context.adoDataStore.data.map { preferences ->
        preferences[rollUpCompletedKey] ?: true
    }

    suspend fun saveRollUpCompleted(enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[rollUpCompletedKey] = enabled
        }
    }
}

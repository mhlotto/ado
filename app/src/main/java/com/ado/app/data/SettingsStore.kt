package com.ado.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.adoDataStore by preferencesDataStore(name = "ado_settings")

class SettingsStore(private val context: Context) {
    private val rollUpCompletedKey = booleanPreferencesKey("roll_up_completed")
    private val projectsSimpleViewKey = booleanPreferencesKey("projects_simple_view")
    private val simpleProjectTaskListsKey = stringSetPreferencesKey("simple_project_task_lists")
    private val simpleTaskSubTaskListsKey = stringSetPreferencesKey("simple_task_subtask_lists")
    private val positionNormalizationVersionKey = intPreferencesKey("position_normalization_version")

    val rollUpCompletedFlow: Flow<Boolean> = context.adoDataStore.data.map { preferences ->
        preferences[rollUpCompletedKey] ?: true
    }

    val projectsSimpleViewFlow: Flow<Boolean> = context.adoDataStore.data.map { preferences ->
        preferences[projectsSimpleViewKey] ?: false
    }

    fun projectTasksSimpleViewFlow(projectId: String): Flow<Boolean> =
        context.adoDataStore.data.map { preferences ->
            projectId in preferences[simpleProjectTaskListsKey].orEmpty()
        }

    fun taskSubTasksSimpleViewFlow(taskId: String): Flow<Boolean> =
        context.adoDataStore.data.map { preferences ->
            taskId in preferences[simpleTaskSubTaskListsKey].orEmpty()
        }

    suspend fun saveRollUpCompleted(enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[rollUpCompletedKey] = enabled
        }
    }

    suspend fun saveProjectsSimpleView(enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[projectsSimpleViewKey] = enabled
        }
    }

    suspend fun saveProjectTasksSimpleView(projectId: String, enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[simpleProjectTaskListsKey] =
                preferences[simpleProjectTaskListsKey].orEmpty().withMembership(projectId, enabled)
        }
    }

    suspend fun saveTaskSubTasksSimpleView(taskId: String, enabled: Boolean) {
        context.adoDataStore.edit { preferences ->
            preferences[simpleTaskSubTaskListsKey] =
                preferences[simpleTaskSubTaskListsKey].orEmpty().withMembership(taskId, enabled)
        }
    }

    suspend fun positionNormalizationVersion(): Int =
        context.adoDataStore.data.first()[positionNormalizationVersionKey] ?: 0

    suspend fun savePositionNormalizationVersion(version: Int) {
        context.adoDataStore.edit { preferences ->
            preferences[positionNormalizationVersionKey] = version
        }
    }
}

internal fun Set<String>.withMembership(value: String, included: Boolean): Set<String> =
    if (included) this + value else this - value

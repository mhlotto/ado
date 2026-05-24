package com.ado.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ado.app.data.AdoRepository
import com.ado.app.data.DEFAULT_SERVER_URL
import com.ado.app.data.SettingsStore
import com.ado.app.data.normalizeServerUrl
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    repository: AdoRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val savedServerUrl by settingsStore.serverUrlFlow.collectAsState(initial = DEFAULT_SERVER_URL)
    val offlineMode by repository.offlineModeFlow.collectAsState(initial = false)
    val rollUpCompleted by repository.rollUpCompletedFlow.collectAsState(initial = true)
    var serverUrl by remember { mutableStateOf(savedServerUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(savedServerUrl) {
        serverUrl = savedServerUrl
    }

    fun save() {
        scope.launch {
            settingsStore.saveServerUrl(serverUrl)
            serverUrl = normalizeServerUrl(serverUrl)
            message = "Saved."
        }
    }

    fun testConnection() {
        scope.launch {
            testing = true
            message = null
            settingsStore.saveServerUrl(serverUrl)
            serverUrl = normalizeServerUrl(serverUrl)
            try {
                repository.testConnection()
                message = "Connection succeeded."
            } catch (e: Exception) {
                message = "Connection failed: ${repository.friendlyError(e)}"
            } finally {
                testing = false
            }
        }
    }

    AdoScaffold(
        title = "Settings",
        onBack = onBack,
        offlineMode = offlineMode,
        onToggleOfflineMode = {
            scope.launch {
                repository.setOfflineMode(!offlineMode)
            }
        },
        actions = {
            TextButton(onClick = ::testConnection, enabled = !testing) {
                Text(if (testing) "Testing..." else "Test")
            }
        },
    ) { padding ->
        Column(
            modifier = padding
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                supportingText = { Text("Default: $DEFAULT_SERVER_URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::save, modifier = Modifier.padding(top = 12.dp)) {
                Text("Save")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Roll up completed entries")
                    Text("Start finished task and subtask sections collapsed.")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = rollUpCompleted,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            repository.setRollUpCompleted(enabled)
                        }
                    },
                )
            }
            message?.let {
                Text(text = it, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

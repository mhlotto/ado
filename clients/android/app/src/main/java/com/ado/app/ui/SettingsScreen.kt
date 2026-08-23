package com.ado.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ado.app.data.AdoRepository
import com.ado.app.data.ImportPreview
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rollUpCompleted by repository.rollUpCompletedFlow.collectAsState(initial = true)
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingPreview by remember { mutableStateOf<ImportPreview?>(null) }

    fun applyImport(raw: String, overwrite: Boolean) {
        scope.launch {
            try {
                val result = repository.importData(raw, overwrite)
                message = "Imported ${result.imported} items; skipped ${result.skipped}."
            } catch (e: Exception) {
                message = repository.friendlyError(e)
            } finally {
                pendingImport = null
                pendingPreview = null
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = repository.exportData()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                            ?: throw IllegalStateException("Unable to write backup file.")
                    }
                    message = "Backup exported."
                } catch (e: Exception) {
                    message = repository.friendlyError(e)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val raw = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: throw IllegalStateException("Unable to read backup file.")
                    }
                    val preview = repository.previewImport(raw)
                    if (preview.conflicts > 0) {
                        pendingImport = raw
                        pendingPreview = preview
                    } else {
                        applyImport(raw, overwrite = false)
                    }
                } catch (e: Exception) {
                    message = repository.friendlyError(e)
                }
            }
        }
    }

    AdoScaffold(
        title = "Settings",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = padding
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Roll up completed entries")
                    Text(
                        "Start finished task and subtask sections collapsed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextColor,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = rollUpCompleted,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setRollUpCompleted(enabled) }
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daily and home defaults", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Edit the items used when new generated lists are created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedTextColor,
                )
                Button(onClick = onOpenTemplates) {
                    Text("Templates")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export all local projects, tasks, subtasks, and templates to a JSON file, or restore from one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedTextColor,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { exportLauncher.launch("ado-backup-${LocalDate.now()}.json") }) {
                        Text("Export")
                    }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Text("Import")
                    }
                }
            }

            Column {
                Text("About & Privacy", style = MaterialTheme.typography.titleMedium)
                SettingsNavigationRow(label = "About Ado", onClick = onOpenAbout)
                SettingsNavigationRow(label = "Privacy Policy", onClick = onOpenPrivacyPolicy)
            }

            message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    val preview = pendingPreview
    val raw = pendingImport
    if (preview != null && raw != null) {
        ConfirmChoiceDialog(
            title = "Matching local data found",
            message = "${preview.conflicts} imported records match items already on this device. Overwrite matching items or keep the existing local versions?",
            confirmLabel = "Overwrite",
            dismissLabel = "Keep existing",
            onCancel = {
                pendingImport = null
                pendingPreview = null
            },
            onDismiss = { applyImport(raw, overwrite = false) },
            onConfirm = { applyImport(raw, overwrite = true) },
        )
    }
}

@Composable
private fun SettingsNavigationRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text("›", style = MaterialTheme.typography.titleLarge, color = MutedTextColor)
    }
}

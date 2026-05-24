package com.ado.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ado.app.data.PendingMutation
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<PendingMutation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val offlineMode by repository.offlineModeFlow.collectAsState(initial = false)

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            pending = repository.pendingMutations()
            loading = false
        }
    }

    fun syncNow() {
        scope.launch {
            if (repository.isOfflineMode()) {
                error = null
                message = null
                pending = repository.pendingMutations()
                return@launch
            }
            loading = true
            error = null
            message = null
            try {
                val result = repository.syncPendingMutations()
                message = result.message
                pending = repository.pendingMutations()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
                pending = repository.pendingMutations()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(offlineMode) {
        if (offlineMode) {
            error = null
            message = null
        }
    }

    AdoScaffold(
        title = "Sync",
        onBack = onBack,
        onSettings = onOpenSettings,
        offlineMode = offlineMode,
        onToggleOfflineMode = {
            scope.launch {
                repository.setOfflineMode(!offlineMode)
                message = null
                error = null
            }
        },
        bottomActions = listOf(
            BottomBarAction(
                label = "Sync",
                onClick = { syncNow() },
                enabled = pending.isNotEmpty() && !loading && !offlineMode,
                emphasized = pending.isNotEmpty(),
            ),
            BottomBarAction(
                label = "Refresh",
                onClick = { refresh() },
                enabled = !loading,
            ),
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (!offlineMode && message != null) {
                OfflineBanner(message.orEmpty())
            }
            if (!offlineMode && error != null) {
                ErrorBanner(message = error.orEmpty(), onRetry = { syncNow() })
            }
            when {
                loading && pending.isEmpty() -> LoadingState()
                pending.isEmpty() -> EmptyState("No pending changes.")
                else -> LazyColumn {
                    items(pending, key = { it.id }) { mutation ->
                        PendingMutationRow(mutation)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingMutationRow(mutation: PendingMutation) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${mutation.operation} ${mutation.entityType}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (mutation.attempts > 0) {
                    Text(
                        text = "${mutation.attempts} attempts",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text("Local ID: ${mutation.localId}", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
            Text("Queued: ${mutation.createdAt}", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
            if (mutation.payload.isNotBlank()) {
                Text("Payload: ${mutation.payload}", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
            }
            if (!mutation.lastError.isNullOrBlank()) {
                Text(
                    text = "Last error: ${mutation.lastError}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

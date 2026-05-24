package com.ado.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import com.ado.app.data.AdoRepository
import com.ado.app.data.Template
import kotlinx.coroutines.launch

@Composable
fun TemplateListScreen(
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showingCache by remember { mutableStateOf(false) }
    val offlineMode by repository.offlineModeFlow.collectAsState(initial = false)

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            val cached = repository.getCachedTemplates()
            if (cached.isNotEmpty()) {
                templates = cached
                showingCache = true
            }
            val result = repository.getTemplates()
            templates = result.data
            showingCache = result.fromCache
            error = if (repository.isOfflineMode()) null else result.errorMessage
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(offlineMode) {
        if (offlineMode) {
            error = null
        }
    }

    AdoScaffold(
        title = "Templates",
        onBack = onBack,
        onSettings = onOpenSettings,
        offlineMode = offlineMode,
        onToggleOfflineMode = {
            scope.launch {
                repository.setOfflineMode(!offlineMode)
                error = null
                refresh()
            }
        },
        bottomActions = listOf(
            BottomBarAction(
                label = "Refresh",
                onClick = { refresh() },
            ),
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (!offlineMode && error != null && templates.isEmpty()) {
                ErrorBanner(message = error ?: "Unable to load templates", onRetry = { refresh() })
            } else if (!offlineMode && error != null && showingCache) {
                OfflineBanner("Showing cached templates. ${error.orEmpty()}")
            }

            when {
                loading && templates.isEmpty() -> LoadingState()
                templates.isEmpty() -> EmptyState("No templates.")
                else -> LazyColumn {
                    items(templates, key = { it.templateKey }) { template ->
                        TemplateRow(template = template, onClick = { onOpenTemplate(template.templateKey) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: Template, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text(template.templateKey, style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
            Text("${template.items.size} default subtasks", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
        }
    }
}

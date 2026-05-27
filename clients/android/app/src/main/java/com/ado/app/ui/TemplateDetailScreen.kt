package com.ado.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.ado.app.data.Template
import com.ado.app.data.TemplateItem
import kotlinx.coroutines.launch

@Composable
fun TemplateDetailScreen(
    templateKey: String,
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf<Template?>(null) }
    var items by remember { mutableStateOf<List<TemplateItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<TemplateItem?>(null) }
    var itemToDelete by remember { mutableStateOf<TemplateItem?>(null) }
    val dataRevision by repository.dataRevisionFlow.collectAsState(initial = 0)

    fun setTemplate(next: Template?) {
        template = next
        items = next?.items.orEmpty().sortedBy { it.position }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                setTemplate(repository.getTemplate(templateKey).data ?: template)
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun saveItems(nextItems: List<TemplateItem>) {
        val current = template ?: return
        scope.launch {
            error = null
            try {
                val updated = repository.updateTemplateItems(current, nextItems.mapIndexed { index, item -> item.copy(position = index) })
                setTemplate(updated)
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun addItem(name: String, description: String, tags: List<String>) {
        val next = items + TemplateItem(id = null, name = name, description = description, position = items.size)
        showAddDialog = false
        saveItems(next)
    }

    fun updateItem(item: TemplateItem, name: String, description: String, tags: List<String>) {
        val next = items.map { if (it == item) it.copy(name = name, description = description) else it }
        itemToEdit = null
        saveItems(next)
    }

    fun deleteItem(item: TemplateItem) {
        itemToDelete = null
        saveItems(items.filterNot { it == item })
    }

    fun moveItem(index: Int, delta: Int) {
        val target = index + delta
        if (target !in items.indices) return
        val mutable = items.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        saveItems(mutable)
    }

    LaunchedEffect(templateKey, dataRevision) { refresh() }

    AdoScaffold(
        title = template?.name ?: "Template",
        onBack = onBack,
        onSettings = onOpenSettings,
        bottomActions = listOf(
            BottomBarAction(
                label = "Add",
                onClick = { showAddDialog = true },
                enabled = template != null,
                prominent = true,
            ),
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (error != null) {
                ErrorBanner(message = error ?: "Unable to load template", onRetry = { refresh() })
            }

            template?.let {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(it.templateKey, style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
                    if (it.description.isNotBlank()) {
                        Text(it.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            when {
                loading && template == null -> LoadingState()
                items.isEmpty() -> EmptyState("No default subtasks.")
                else -> LazyColumn {
                    itemsIndexed(items, key = { _, item -> item.id ?: "${item.name}-${item.position}" }) { index, item ->
                        TemplateItemRow(
                            item = item,
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            onMoveUp = { moveItem(index, -1) },
                            onMoveDown = { moveItem(index, 1) },
                            onEdit = { itemToEdit = item },
                            onDelete = { itemToDelete = item },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        EntityFormDialog(
            title = "New default subtask",
            nameLabel = "Subtask name",
            onDismiss = { showAddDialog = false },
            onSubmit = ::addItem,
        )
    }

    itemToEdit?.let { item ->
        EntityFormDialog(
            title = "Edit default subtask",
            nameLabel = "Subtask name",
            initialName = item.name,
            initialDescription = item.description,
            onDismiss = { itemToEdit = null },
            onSubmit = { name, description, tags -> updateItem(item, name, description, tags) },
        )
    }

    itemToDelete?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete default subtask",
            message = "Delete ${item.name} from this template?",
            onDismiss = { itemToDelete = null },
            onConfirm = { deleteItem(item) },
        )
    }
}

@Composable
private fun TemplateItemRow(
    item: TemplateItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            if (item.description.isNotBlank()) {
                Text(item.description, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("Up") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("Down") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

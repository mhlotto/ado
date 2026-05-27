package com.ado.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ado.app.data.AdoRepository
import com.ado.app.data.Project
import com.ado.app.data.SYNC_SYNCED
import com.ado.app.data.SubTask
import com.ado.app.data.Task
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun TaskDetailScreen(
    taskId: String,
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<Task?>(null) }
    var subtasks by remember { mutableStateOf<List<SubTask>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showingCache by remember { mutableStateOf(false) }
    var showCreateSubTaskDialog by remember { mutableStateOf(false) }
    var showEditTaskDialog by remember { mutableStateOf(false) }
    var subTaskToEdit by remember { mutableStateOf<SubTask?>(null) }
    var subTaskToDelete by remember { mutableStateOf<SubTask?>(null) }
    var taskMoveProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var subTaskMoveTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var pendingCount by remember { mutableStateOf(0) }
    var simpleView by remember { mutableStateOf(false) }
    var showOfflinePrompt by remember { mutableStateOf(false) }
    val offlineMode by repository.offlineModeFlow.collectAsState(initial = false)
    val rollUpCompleted by repository.rollUpCompletedFlow.collectAsState(initial = true)
    var finishedExpanded by remember(taskId) { mutableStateOf(!rollUpCompleted) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            repository.getTask(taskId).let { result ->
                task = result.data ?: task
                val resultError = if (repository.isOfflineMode()) null else result.errorMessage
                if (resultError != null) {
                    error = resultError
                    showingCache = result.fromCache
                    showOfflinePrompt = true
                }
            }
            val cachedSubtasks = repository.getCachedSubTasks(taskId)
            if (cachedSubtasks.isNotEmpty()) {
                subtasks = cachedSubtasks
                showingCache = true
            }
            val result = repository.getSubTasks(taskId)
            subtasks = result.data
            showingCache = result.fromCache
            val resultError = if (repository.isOfflineMode()) null else result.errorMessage
            error = resultError ?: error
            if (resultError != null) {
                showOfflinePrompt = true
            }
            pendingCount = repository.pendingMutationCount()
            loading = false
        }
    }

    LaunchedEffect(offlineMode) {
        if (offlineMode) {
            error = null
            showOfflinePrompt = false
        }
    }

    LaunchedEffect(taskId, rollUpCompleted) {
        finishedExpanded = !rollUpCompleted
    }

    fun setOfflineMode(enabled: Boolean) {
        scope.launch {
            repository.setOfflineMode(enabled)
            showOfflinePrompt = false
            error = null
            refresh()
        }
    }

    fun refreshPendingCount() {
        scope.launch {
            pendingCount = repository.pendingMutationCount()
        }
    }

    fun syncNow() {
        scope.launch {
            error = null
            repository.syncPendingMutations()
            pendingCount = repository.pendingMutationCount()
            refresh()
        }
    }

    fun toggleTask() {
        val current = task ?: return
        scope.launch {
            error = null
            try {
                val updated = repository.toggleTaskStatus(current)
                task = updated
                if (updated.syncStatus != SYNC_SYNCED) {
                    pendingCount = repository.pendingMutationCount()
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun toggleSubTask(subTask: SubTask) {
        scope.launch {
            error = null
            try {
                val updated = repository.toggleSubTaskStatus(subTask)
                subtasks = subtasks.map { if (it.id == updated.id) updated else it }
                if (updated.syncStatus != SYNC_SYNCED) {
                    pendingCount = repository.pendingMutationCount()
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createSubTask(name: String, description: String, tags: List<String>) {
        scope.launch {
            error = null
            try {
                val created = repository.createSubTask(taskId, name, description)
                subtasks = subtasks.filterNot { it.id == created.id } + created
                showCreateSubTaskDialog = false
                refreshPendingCount()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createBulkSubTasks(text: String) {
        scope.launch {
            error = null
            val drafts = parseBulkSubTasks(text)
            if (drafts.isEmpty()) {
                error = "Bulk subtask input did not contain any subtasks."
                return@launch
            }
            try {
                val created = mutableListOf<SubTask>()
                for (draft in drafts) {
                    created += repository.createSubTask(taskId, draft.name, draft.description)
                }
                subtasks = repository.getCachedSubTasks(taskId)
                showCreateSubTaskDialog = false
                pendingCount = repository.pendingMutationCount()
                error = if (created.any { it.syncStatus != SYNC_SYNCED }) null else "Created ${created.size} subtasks."
            } catch (e: Exception) {
                subtasks = repository.getCachedSubTasks(taskId)
                pendingCount = repository.pendingMutationCount()
                error = repository.friendlyError(e)
            }
        }
    }

    fun deleteSubTask(subTask: SubTask) {
        scope.launch {
            error = null
            try {
                repository.deleteSubTask(subTask)
                subtasks = subtasks.filterNot { it.id == subTask.id }
                subTaskToDelete = null
                refreshPendingCount()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
                subTaskToDelete = null
            }
        }
    }

    fun openEditTask() {
        scope.launch {
            taskMoveProjects = repository.getTaskMoveProjectOptions()
            showEditTaskDialog = true
        }
    }

    fun openEditSubTask(subTask: SubTask) {
        scope.launch {
            taskMoveProjects = repository.getTaskMoveProjectOptions()
            subTaskMoveTasks = repository.getSubTaskMoveTaskOptions()
            subTaskToEdit = subTask
        }
    }

    fun updateTask(name: String, description: String, projectId: String) {
        val current = task ?: return
        scope.launch {
            error = null
            try {
                val updated = repository.updateTask(current, name, description, projectId)
                task = updated
                showEditTaskDialog = false
                if (updated.syncStatus != SYNC_SYNCED) {
                    pendingCount = repository.pendingMutationCount()
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun updateSubTask(subTask: SubTask, name: String, description: String, targetTaskId: String) {
        scope.launch {
            error = null
            try {
                val updated = repository.updateSubTask(subTask, name, description, targetTaskId)
                subtasks = if (updated.taskId == taskId) {
                    subtasks.map { if (it.id == updated.id) updated else it }
                } else {
                    subtasks.filterNot { it.id == updated.id }
                }
                subTaskToEdit = null
                if (updated.syncStatus != SYNC_SYNCED) {
                    pendingCount = repository.pendingMutationCount()
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    LaunchedEffect(taskId) { refresh() }

    AdoScaffold(
        title = "Subtasks",
        onBack = onBack,
        onSettings = onOpenSettings,
        offlineMode = offlineMode,
        onToggleOfflineMode = { setOfflineMode(!offlineMode) },
        bottomActions = listOf(
            BottomBarAction(
                label = "Edit",
                onClick = { openEditTask() },
                enabled = task != null,
            ),
            BottomBarAction(
                label = "Add",
                onClick = { showCreateSubTaskDialog = true },
                prominent = true,
            ),
            BottomBarAction(
                label = if (simpleView) "Full" else "Simple",
                onClick = { simpleView = !simpleView },
            ),
            BottomBarAction(
                label = "Sync",
                onClick = { syncNow() },
                emphasized = pendingCount > 0,
            ),
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (!offlineMode && error != null && task == null) {
                ErrorBanner(message = error ?: "Unable to load task", onRetry = { refresh() })
            } else if (!offlineMode && error != null && showingCache) {
                OfflineBanner("Showing cached task. ${error.orEmpty()}")
            } else if (!offlineMode && error != null) {
                OfflineBanner(error.orEmpty())
            }

            task?.let {
                TaskHeader(
                    task = it,
                    onLongPress = ::toggleTask,
                )
            }

            when {
                loading && subtasks.isEmpty() -> LoadingState()
                subtasks.isEmpty() -> EmptyState("No subtasks.")
                else -> LazyColumn {
                    val unfinishedSubTasks = subtasks.filterNot { it.isDone }
                    val finishedSubTasks = subtasks.filter { it.isDone }.sortedBy(::subTaskFinishedAt)
                    items(unfinishedSubTasks, key = { it.id }) { subTask ->
                        if (simpleView) {
                            SubTaskSimpleRow(
                                subTask = subTask,
                                onClick = { toggleSubTask(subTask) },
                                onLongPress = { toggleSubTask(subTask) },
                            )
                        } else {
                            SubTaskRow(
                                subTask = subTask,
                                onLongPress = { toggleSubTask(subTask) },
                                onEdit = { openEditSubTask(subTask) },
                                onDelete = { subTaskToDelete = subTask },
                            )
                        }
                    }
                    if (finishedSubTasks.isNotEmpty()) {
                        item(key = "finished-header") {
                            FinishedSectionHeader(
                                count = finishedSubTasks.size,
                                expanded = finishedExpanded,
                                onToggle = { finishedExpanded = !finishedExpanded },
                            )
                        }
                        if (finishedExpanded) {
                            items(finishedSubTasks, key = { it.id }) { subTask ->
                                if (simpleView) {
                                    SubTaskSimpleRow(
                                        subTask = subTask,
                                        onClick = { toggleSubTask(subTask) },
                                        onLongPress = { toggleSubTask(subTask) },
                                    )
                                } else {
                                    SubTaskRow(
                                        subTask = subTask,
                                        showFinishedAt = true,
                                        onLongPress = { toggleSubTask(subTask) },
                                        onEdit = { openEditSubTask(subTask) },
                                        onDelete = { subTaskToDelete = subTask },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSubTaskDialog) {
        BulkCreateDialog(
            title = "New subtask",
            nameLabel = "Subtask name",
            bulkLabel = "Bulk subtasks",
            bulkHelp = "Top-level lines create subtasks. Indented lines become that subtask's description.",
            onDismiss = { showCreateSubTaskDialog = false },
            onSubmitSingle = { name, description -> createSubTask(name, description, emptyList()) },
            onSubmitBulk = ::createBulkSubTasks,
        )
    }

    subTaskToDelete?.let { subTask ->
        ConfirmDeleteDialog(
            title = "Delete subtask",
            message = "Delete ${subTask.name}?",
            onDismiss = { subTaskToDelete = null },
            onConfirm = { deleteSubTask(subTask) },
        )
    }

    task?.let { currentTask ->
        if (showEditTaskDialog) {
            MoveEntityFormDialog(
                title = "Edit task",
                nameLabel = "Task name",
                initialName = currentTask.name,
                initialDescription = currentTask.description,
                destinationLabel = "Project",
                options = taskMoveProjects.map { MoveOption(it.id, it.name) },
                initialDestinationId = currentTask.projectId,
                onDismiss = { showEditTaskDialog = false },
                onSubmit = ::updateTask,
            )
        }
    }

    subTaskToEdit?.let { subTask ->
        val projectNames = taskMoveProjects.associate { it.id to it.name }
        MoveEntityFormDialog(
            title = "Edit subtask",
            nameLabel = "Subtask name",
            initialName = subTask.name,
            initialDescription = subTask.description,
            destinationLabel = "Task",
            options = subTaskMoveTasks.map {
                MoveOption(it.id, "${it.name} (${projectNames[it.projectId] ?: "Project"})")
            },
            initialDestinationId = subTask.taskId,
            onDismiss = { subTaskToEdit = null },
            onSubmit = { name, description, targetTaskId -> updateSubTask(subTask, name, description, targetTaskId) },
        )
    }

    if (showOfflinePrompt) {
        ConfirmChoiceDialog(
            title = "Use offline mode?",
            message = "The server is not reachable. Use cached data and save changes locally?",
            confirmLabel = "Offline",
            dismissLabel = "Stay online",
            onCancel = { showOfflinePrompt = false },
            onDismiss = { showOfflinePrompt = false },
            onConfirm = { setOfflineMode(true) },
        )
    }
}

private fun subTaskFinishedAt(subTask: SubTask): Instant {
    val finishedAt = subTask.finishedAt ?: return Instant.MAX
    return try {
        Instant.parse(finishedAt)
    } catch (_: Exception) {
        Instant.MAX
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskHeader(
    task: Task,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Text(
            text = task.name,
            style = MaterialTheme.typography.headlineSmall,
            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
        )
        if (task.description.isNotBlank()) {
            Text(task.description, style = MaterialTheme.typography.bodyLarge)
        }
        Row {
            Text("Status: ${task.status}", color = MaterialTheme.colorScheme.primary)
        }
        Text("Created: ${task.createdAt}", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
        if (task.finishedAt != null) {
            Text("Finished: ${task.finishedAt}", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
        }
    }
}

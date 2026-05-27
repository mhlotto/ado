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
    var message by remember { mutableStateOf<String?>(null) }
    var showCreateSubTaskDialog by remember { mutableStateOf(false) }
    var showEditTaskDialog by remember { mutableStateOf(false) }
    var subTaskToEdit by remember { mutableStateOf<SubTask?>(null) }
    var subTaskToDelete by remember { mutableStateOf<SubTask?>(null) }
    var taskMoveProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var subTaskMoveTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var simpleView by remember { mutableStateOf(false) }
    val rollUpCompleted by repository.rollUpCompletedFlow.collectAsState(initial = true)
    val dataRevision by repository.dataRevisionFlow.collectAsState(initial = 0)
    var finishedExpanded by remember(taskId) { mutableStateOf(!rollUpCompleted) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            message = null
            try {
                task = repository.getTask(taskId).data ?: task
                subtasks = repository.getSubTasks(taskId).data
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(taskId, rollUpCompleted) {
        finishedExpanded = !rollUpCompleted
    }

    fun toggleTask() {
        val current = task ?: return
        scope.launch {
            error = null
            message = null
            try {
                val updated = repository.toggleTaskStatus(current)
                task = updated
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun toggleSubTask(subTask: SubTask) {
        scope.launch {
            error = null
            message = null
            try {
                val updated = repository.toggleSubTaskStatus(subTask)
                subtasks = subtasks.map { if (it.id == updated.id) updated else it }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createSubTask(name: String, description: String, tags: List<String>) {
        scope.launch {
            error = null
            message = null
            try {
                val created = repository.createSubTask(taskId, name, description)
                subtasks = subtasks.filterNot { it.id == created.id } + created
                showCreateSubTaskDialog = false
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createBulkSubTasks(text: String) {
        scope.launch {
            error = null
            message = null
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
                subtasks = repository.getSubTasks(taskId).data
                showCreateSubTaskDialog = false
                message = "Created ${created.size} subtasks."
            } catch (e: Exception) {
                subtasks = repository.getSubTasks(taskId).data
                error = repository.friendlyError(e)
            }
        }
    }

    fun deleteSubTask(subTask: SubTask) {
        scope.launch {
            error = null
            message = null
            try {
                repository.deleteSubTask(subTask)
                subtasks = subtasks.filterNot { it.id == subTask.id }
                subTaskToDelete = null
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
            message = null
            try {
                val updated = repository.updateTask(current, name, description, projectId)
                task = updated
                showEditTaskDialog = false
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun updateSubTask(subTask: SubTask, name: String, description: String, targetTaskId: String) {
        scope.launch {
            error = null
            message = null
            try {
                val updated = repository.updateSubTask(subTask, name, description, targetTaskId)
                subtasks = if (updated.taskId == taskId) {
                    subtasks.map { if (it.id == updated.id) updated else it }
                } else {
                    subtasks.filterNot { it.id == updated.id }
                }
                subTaskToEdit = null
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    LaunchedEffect(taskId, dataRevision) { refresh() }

    AdoScaffold(
        title = "Subtasks",
        onBack = onBack,
        onSettings = onOpenSettings,
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
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (error != null) {
                ErrorBanner(message = error ?: "Unable to load task", onRetry = { refresh() })
            }
            message?.let { InfoBanner(it) }

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

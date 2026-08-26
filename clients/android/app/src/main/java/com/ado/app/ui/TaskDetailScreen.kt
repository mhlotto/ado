package com.ado.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ado.app.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ado.app.data.AdoRepository
import com.ado.app.data.Project
import com.ado.app.data.SubTask
import com.ado.app.data.Task
import com.ado.app.data.Template
import com.ado.app.data.reorderSubTasksById
import kotlinx.coroutines.launch

@Composable
fun TaskDetailScreen(
    taskId: String,
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var task by remember { mutableStateOf<Task?>(null) }
    var subtasks by remember { mutableStateOf<List<SubTask>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCreateSubTaskDialog by remember { mutableStateOf(false) }
    var showBulkSubTaskDialog by remember { mutableStateOf(false) }
    var showTemplateSubTaskDialog by remember { mutableStateOf(false) }
    var showEditTaskDialog by remember { mutableStateOf(false) }
    var subTaskToEdit by remember { mutableStateOf<SubTask?>(null) }
    var subTaskToDelete by remember { mutableStateOf<SubTask?>(null) }
    var taskMoveProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var subTaskMoveTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var showListTypeDialog by remember { mutableStateOf(false) }
    var simpleView by remember { mutableStateOf(false) }
    var reorderMode by remember(taskId) { mutableStateOf(false) }
    var reorderSaving by remember(taskId) { mutableStateOf(false) }
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
                showBulkSubTaskDialog = false
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

    fun openAddSubTask() {
        showCreateSubTaskDialog = true
    }

    fun openTemplateSubTask() {
        scope.launch {
            try {
                templates = repository.getTemplates().data
                showTemplateSubTaskDialog = true
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
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

    fun updateListType(listType: String) {
        val current = task ?: return
        scope.launch {
            error = null
            try {
                task = repository.updateTaskListType(current, listType)
                showListTypeDialog = false
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun saveReorderedSubTasks(orderedIds: List<String>) {
        subtasks = reorderSubTasksById(subtasks, orderedIds)
        reorderSaving = true
        scope.launch {
            error = null
            message = null
            try {
                subtasks = repository.reorderSubTasks(taskId, orderedIds)
            } catch (e: Exception) {
                subtasks = repository.getSubTasks(taskId).data
                error = repository.friendlyError(e)
            } finally {
                reorderSaving = false
            }
        }
    }

    fun applyTemplate(template: Template) {
        scope.launch {
            error = null
            message = null
            try {
                val result = repository.applyTemplateToTask(taskId, template.templateKey)
                subtasks = repository.getSubTasks(taskId).data
                showTemplateSubTaskDialog = false
                message = if (result.added == 0) {
                    "No new subtasks to add from ${template.name}."
                } else {
                    "Added ${result.added} subtasks from ${template.name}."
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun shareCurrentTask() {
        val currentTask = task ?: return
        shareTask(context, currentTask, subtasks)
    }

    fun printSubTasks() {
        val currentTask = task ?: return
        val items = subtasksInPrintOrder(subtasks).filterNot { it.isDone }.map { ChecklistPrintItem(it.name) }
        printChecklist(context, currentTask.name, items)
    }

    LaunchedEffect(taskId, dataRevision) { refresh() }

    fun leaveReorderMode() {
        if (!reorderSaving) reorderMode = false
    }

    BackHandler(enabled = reorderMode) { leaveReorderMode() }

    AdoScaffold(
        title = task?.name ?: "Task",
        onBack = { if (reorderMode) leaveReorderMode() else onBack() },
        onSettings = if (reorderMode) null else onOpenSettings,
        bottomActions = if (reorderMode) emptyList() else listOf(
            BottomBarAction(
                label = "Edit",
                onClick = { openEditTask() },
                enabled = task != null,
                iconResource = R.drawable.ic_edit_24,
                contentDescription = "Edit task",
            ),
            BottomBarAction(
                label = "Add",
                onClick = { openAddSubTask() },
                prominent = true,
                iconResource = R.drawable.ic_add_24,
                contentDescription = "Add",
                menuActions = listOf(
                    BottomBarMenuAction("Single") { openAddSubTask() },
                    BottomBarMenuAction("Bulk") { showBulkSubTaskDialog = true },
                    BottomBarMenuAction("Template") { openTemplateSubTask() },
                ),
            ),
            if (subtasks.size >= 2) {
                BottomBarAction(
                    label = "Reorder",
                    onClick = { reorderMode = true },
                    iconResource = R.drawable.ic_reorder_24,
                    contentDescription = "Reorder items",
                )
            } else {
                null
            },
            BottomBarAction(
                label = if (simpleView) "Full" else "Simple",
                onClick = { simpleView = !simpleView },
                iconResource = R.drawable.ic_view_mode_24,
                contentDescription = if (simpleView) "Switch to full view" else "Switch to simple view",
            ),
            BottomBarAction(
                label = "Print",
                onClick = ::printSubTasks,
                enabled = task != null && subtasks.any { !it.isDone },
                iconResource = R.drawable.ic_print_24,
                contentDescription = "Print",
            ),
        ).filterNotNull(),
    ) { padding ->
        Column(modifier = padding) {
            if (error != null) {
                ErrorBanner(message = error ?: "Unable to load task", onRetry = { refresh() })
            }
            message?.let { InfoBanner(it) }

            task?.let {
                if (!reorderMode) {
                    TaskHeader(
                        task = it,
                        onLongPress = ::toggleTask,
                        onShare = ::shareCurrentTask,
                        onConfigureListType = { showListTypeDialog = true },
                    )
                }
            }

            when {
                loading && subtasks.isEmpty() -> LoadingState()
                subtasks.isEmpty() -> EmptyState("No subtasks.")
                reorderMode -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (reorderSaving) "Saving order..." else "Reorder items",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = ::leaveReorderMode, enabled = !reorderSaving) {
                            Text("Done")
                        }
                    }
                    ReorderSubTaskList(
                        subTasks = subtasks,
                        enabled = !reorderSaving,
                        modifier = Modifier.weight(1f),
                        onOrderSettled = ::saveReorderedSubTasks,
                    )
                }
                else -> LazyColumn {
                    val unfinishedSubTasks = subtasks.filterNot { it.isDone }
                    val finishedSubTasks = newestFinishedFirst(subtasks.filter { it.isDone }) { it.finishedAt }
                    item(key = "items-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Items",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    items(unfinishedSubTasks, key = { it.id }) { subTask ->
                        if (simpleView) {
                            SubTaskSimpleRow(
                                subTask = subTask,
                                onClick = { toggleSubTask(subTask) },
                                onToggle = { toggleSubTask(subTask) },
                                onLongPress = { toggleSubTask(subTask) },
                            )
                        } else {
                            SubTaskRow(
                                subTask = subTask,
                                onToggle = { toggleSubTask(subTask) },
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
                                        onToggle = { toggleSubTask(subTask) },
                                        onLongPress = { toggleSubTask(subTask) },
                                    )
                                } else {
                                    SubTaskRow(
                                        subTask = subTask,
                                        showFinishedAt = true,
                                        onToggle = { toggleSubTask(subTask) },
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
        EntityFormDialog(
            title = "New subtask",
            nameLabel = "Subtask name",
            autoFocusName = true,
            onDismiss = { showCreateSubTaskDialog = false },
            onSubmit = { name, description, _ -> createSubTask(name, description, emptyList()) },
        )
    }

    if (showBulkSubTaskDialog) {
        BulkTextCreateDialog(
            title = "Bulk subtasks",
            bulkLabel = "Bulk subtasks",
            bulkHelp = "Top-level lines create subtasks. Indented lines become that subtask's description.",
            onDismiss = { showBulkSubTaskDialog = false },
            onSubmitBulk = ::createBulkSubTasks,
        )
    }

    if (showTemplateSubTaskDialog) {
        QuickAddDialog(
            title = "Template",
            actions = templates.map { template ->
                QuickAddAction(template.name) { applyTemplate(template) }
            },
            onDismiss = { showTemplateSubTaskDialog = false },
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
        if (showListTypeDialog) {
            ListTypeDialog(
                title = "Subtask list type",
                currentType = currentTask.listType,
                onDismiss = { showListTypeDialog = false },
                onSave = ::updateListType,
            )
        }
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

private fun subtasksInPrintOrder(subtasks: List<SubTask>): List<SubTask> =
    subtasks.filterNot { it.isDone } + newestFinishedFirst(subtasks.filter { it.isDone }) { it.finishedAt }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskHeader(
    task: Task,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
    onConfigureListType: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            HttpLinkText(
                text = task.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
            )
            TextButton(onClick = onShare) {
                Text("Share")
            }
            ListTypeSettingsButton(onClick = onConfigureListType)
        }
        SpecialListTypeLabel(task.listType)
        if (task.description.isNotBlank()) {
            HttpLinkText(task.description, style = MaterialTheme.typography.bodyLarge)
        }
        FinishedAtMetadata(task.finishedAt)
    }
}

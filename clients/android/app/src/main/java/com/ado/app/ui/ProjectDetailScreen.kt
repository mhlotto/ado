package com.ado.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ado.app.data.AdoRepository
import com.ado.app.data.CalendarDailyItem
import com.ado.app.data.CalendarEventReader
import com.ado.app.data.Project
import com.ado.app.data.Task
import com.ado.app.data.Template
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun ProjectDetailScreen(
    projectId: String,
    repository: AdoRepository,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val calendarReader = remember(context) { CalendarEventReader(context.contentResolver) }
    var project by remember { mutableStateOf<Project?>(null) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskMoveProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var showListTypeDialog by remember { mutableStateOf(false) }
    var showPrintTaskSelection by remember { mutableStateOf(false) }
    var pendingDailyDate by remember { mutableStateOf<String?>(null) }
    var pendingCalendarDailyGeneration by remember { mutableStateOf<PendingDailyGeneration?>(null) }
    var simpleView by remember { mutableStateOf(false) }
    var subTaskCounts by remember { mutableStateOf<Map<String, OpenDoneCounts>>(emptyMap()) }
    val rollUpCompleted by repository.rollUpCompletedFlow.collectAsState(initial = true)
    val dataRevision by repository.dataRevisionFlow.collectAsState(initial = 0)
    var finishedExpanded by remember(projectId) { mutableStateOf(!rollUpCompleted) }

    fun refreshSubTaskCounts(sourceTasks: List<Task>) {
        scope.launch {
            subTaskCounts = sourceTasks.associate { task ->
                task.id to calculateSubTaskCounts(repository.getSubTasks(task.id).data)
            }
        }
    }

    fun refresh(messageAfter: String? = null) {
        scope.launch {
            loading = true
            error = null
            message = null
            try {
                project = repository.getProject(projectId).data ?: project
                tasks = repository.getTasks(projectId).data
                refreshSubTaskCounts(tasks)
                if (messageAfter != null) {
                    message = messageAfter
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(projectId, rollUpCompleted) {
        finishedExpanded = !rollUpCompleted
    }

    fun toggle(task: Task) {
        scope.launch {
            error = null
            message = null
            try {
                val updated = repository.toggleTaskStatus(task)
                tasks = tasks.map { if (it.id == updated.id) updated else it }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createTask(name: String, description: String, tags: List<String>) {
        scope.launch {
            error = null
            message = null
            try {
                val created = repository.createTask(projectId, name, description)
                tasks = tasks.filterNot { it.id == created.id } + created
                showCreateTaskDialog = false
                refresh()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createBulkTasks(text: String) {
        scope.launch {
            error = null
            message = null
            val drafts = parseBulkTasks(text)
            if (drafts.isEmpty()) {
                error = "Bulk task input did not contain any tasks."
                return@launch
            }
            try {
                val createdTasks = mutableListOf<Task>()
                var createdSubTasks = 0
                for (draft in drafts) {
                    val created = repository.createTask(projectId, draft.name, draft.description)
                    createdTasks += created
                    for (subTask in draft.subtasks) {
                        repository.createSubTask(created.id, subTask.name, subTask.description)
                        createdSubTasks += 1
                    }
                }
                tasks = repository.getTasks(projectId).data
                refreshSubTaskCounts(tasks)
                showCreateTaskDialog = false
                message = "Created ${createdTasks.size} tasks and $createdSubTasks subtasks."
            } catch (e: Exception) {
                tasks = repository.getTasks(projectId).data
                refreshSubTaskCounts(tasks)
                error = repository.friendlyError(e)
            }
        }
    }

    fun openAddTask() {
        scope.launch {
            try {
                templates = repository.getTemplates().data
                showCreateTaskDialog = true
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createTaskFromTemplate(template: Template) {
        scope.launch {
            error = null
            message = null
            try {
                val created = repository.createTaskFromTemplate(projectId, template.templateKey)
                tasks = repository.getTasks(projectId).data
                refreshSubTaskCounts(tasks)
                showCreateTaskDialog = false
                message = "Created ${created.name} from ${template.name}."
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun deleteTask(task: Task) {
        scope.launch {
            error = null
            message = null
            try {
                repository.deleteTask(task)
                tasks = tasks.filterNot { it.id == task.id }
                taskToDelete = null
                refresh()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
                taskToDelete = null
            }
        }
    }

    fun openEditTask(task: Task) {
        scope.launch {
            taskMoveProjects = repository.getTaskMoveProjectOptions()
            taskToEdit = task
        }
    }

    fun updateTask(task: Task, name: String, description: String, targetProjectId: String) {
        scope.launch {
            error = null
            message = null
            try {
                val updated = repository.updateTask(task, name, description, targetProjectId)
                tasks = if (updated.projectId == projectId) {
                    tasks.map { if (it.id == updated.id) updated else it }
                } else {
                    tasks.filterNot { it.id == updated.id }
                }
                taskToEdit = null
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun updateListType(listType: String) {
        val current = project ?: return
        scope.launch {
            error = null
            try {
                project = repository.updateProjectListType(current, listType)
                showListTypeDialog = false
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun generateDaily(date: String, carryOver: Boolean, calendarItems: List<CalendarDailyItem> = emptyList(), calendarMessage: String? = null) {
        scope.launch {
            error = null
            message = null
            try {
                repository.generateDaily(date, carryOver = carryOver, calendarItems = calendarItems)
                refresh(calendarMessage)
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun generateDailyWithCalendar(request: PendingDailyGeneration, calendarAllowed: Boolean) {
        scope.launch {
            val targetDate = repository.dailyTargetDate(request.date)
            val calendarResult = if (calendarAllowed) {
                calendarReader.readEventsForDate(targetDate)
            } else {
                null
            }
            val calendarItems = calendarResult?.items.orEmpty()
            val calendarMessage = when {
                !calendarAllowed -> "Calendar permission denied. Generated without calendar items."
                calendarResult?.errorMessage != null -> calendarResult.errorMessage
                else -> null
            }
            generateDaily(
                date = request.date,
                carryOver = request.carryOver,
                calendarItems = calendarItems,
                calendarMessage = calendarMessage,
            )
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingCalendarDailyGeneration
        pendingCalendarDailyGeneration = null
        if (request != null) {
            generateDailyWithCalendar(request, calendarAllowed = granted)
        }
    }

    fun startDailyGeneration(date: String, carryOver: Boolean) {
        val request = PendingDailyGeneration(date, carryOver)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            generateDailyWithCalendar(request, calendarAllowed = true)
        } else {
            pendingCalendarDailyGeneration = request
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    fun generateSeasonal(templateKey: String) {
        scope.launch {
            error = null
            message = null
            try {
                repository.generateSeasonal(templateKey)
                refresh()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun printTasks(selectedTaskIds: Set<String>) {
        val currentProject = project ?: return
        scope.launch {
            error = null
            message = null
            try {
                val orderedTasks = tasksInChecklistOrder(currentProject, tasks).filter { !it.isDone && it.id in selectedTaskIds }
                val items = buildList {
                    orderedTasks.forEach { task ->
                        add(ChecklistPrintItem(task.name))
                        subTasksInChecklistOrder(repository.getSubTasks(task.id).data).filterNot { it.isDone }.forEach { subTask ->
                            add(ChecklistPrintItem(subTask.name, indentLevel = 1))
                        }
                    }
                }
                printChecklist(context, currentProject.name, items)
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    LaunchedEffect(projectId, dataRevision) { refresh() }

    AdoScaffold(
        title = "Tasks",
        onBack = onBack,
        onSettings = onOpenSettings,
        bottomActions = listOf(
            BottomBarAction(
                label = "Add",
                onClick = { openAddTask() },
                prominent = true,
            ),
            BottomBarAction(
                label = if (simpleView) "Full" else "Simple",
                onClick = { simpleView = !simpleView },
            ),
            BottomBarAction(
                label = "Print",
                onClick = { showPrintTaskSelection = true },
                enabled = project != null && tasks.any { !it.isDone },
            ),
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (error != null) {
                ErrorBanner(message = error ?: "Unable to load tasks", onRetry = { refresh() })
            }
            message?.let { InfoBanner(it) }

            project?.let {
                ProjectHeader(
                    project = it,
                    onConfigureListType = { showListTypeDialog = true },
                )
            }

            when {
                loading && tasks.isEmpty() -> LoadingState()
                tasks.isEmpty() -> EmptyState("No tasks for this project.")
                else -> LazyColumn {
                    val displayTasks = sortedTasksForProject(project, tasks)
                    val unfinishedTasks = displayTasks.filterNot { it.isDone }
                    val finishedTasks = displayTasks.filter { it.isDone }.sortedBy(::taskFinishedAt)
                    items(unfinishedTasks, key = { it.id }) { task ->
                        if (simpleView) {
                            TaskSimpleRow(
                                task = task,
                                subTaskCounts = subTaskCounts[task.id],
                                onClick = { onOpenTask(task.id) },
                                onToggle = { toggle(task) },
                                onLongPress = { toggle(task) },
                            )
                        } else {
                            TaskRow(
                                task = task,
                                subTaskCounts = subTaskCounts[task.id],
                                onClick = { onOpenTask(task.id) },
                                onToggle = { toggle(task) },
                                onLongPress = { toggle(task) },
                                onEdit = { openEditTask(task) },
                                onDelete = { taskToDelete = task },
                            )
                        }
                    }
                    if (finishedTasks.isNotEmpty()) {
                        item(key = "finished-header") {
                            FinishedSectionHeader(
                                count = finishedTasks.size,
                                expanded = finishedExpanded,
                                onToggle = { finishedExpanded = !finishedExpanded },
                            )
                        }
                        if (finishedExpanded) {
                            items(finishedTasks, key = { it.id }) { task ->
                                if (simpleView) {
                                    TaskSimpleRow(
                                        task = task,
                                        subTaskCounts = subTaskCounts[task.id],
                                        onClick = { onOpenTask(task.id) },
                                        onToggle = { toggle(task) },
                                        onLongPress = { toggle(task) },
                                    )
                                } else {
                                    TaskRow(
                                        task = task,
                                        subTaskCounts = subTaskCounts[task.id],
                                        showFinishedAt = true,
                                        onClick = { onOpenTask(task.id) },
                                        onToggle = { toggle(task) },
                                        onLongPress = { toggle(task) },
                                        onEdit = { openEditTask(task) },
                                        onDelete = { taskToDelete = task },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTaskDialog) {
        BulkCreateDialog(
            title = "New task",
            nameLabel = "Task name",
            bulkLabel = "Bulk tasks",
            bulkHelp = "Top-level lines create tasks. Indented lines create subtasks. Deeper indented lines become subtask descriptions.",
            quickActionsLabel = "Generate / template",
            quickActions = projectQuickAddActions(
                project = project,
                templates = templates,
                onGenerateDaily = { date ->
                    showCreateTaskDialog = false
                    pendingDailyDate = date
                },
                onGenerateSeasonal = { templateKey ->
                    showCreateTaskDialog = false
                    generateSeasonal(templateKey)
                },
                onCreateFromTemplate = { template ->
                    createTaskFromTemplate(template)
                },
            ),
            onDismiss = { showCreateTaskDialog = false },
            onSubmitSingle = { name, description -> createTask(name, description, emptyList()) },
            onSubmitBulk = ::createBulkTasks,
        )
    }

    pendingDailyDate?.let { dailyDate ->
        ConfirmChoiceDialog(
            title = "Carry over unfinished items?",
            message = "Generate ${dailyDateLabel(dailyDate)} and carry over unfinished non-default items from the most recent Daily list?",
            confirmLabel = "Yes",
            dismissLabel = "No",
            onCancel = { pendingDailyDate = null },
            onDismiss = {
                pendingDailyDate = null
                startDailyGeneration(dailyDate, carryOver = false)
            },
            onConfirm = {
                pendingDailyDate = null
                startDailyGeneration(dailyDate, carryOver = true)
            },
        )
    }

    taskToDelete?.let { task ->
        ConfirmDeleteDialog(
            title = "Delete task",
            message = "Delete ${task.name} and its subtasks?",
            onDismiss = { taskToDelete = null },
            onConfirm = { deleteTask(task) },
        )
    }

    project?.let { currentProject ->
        if (showListTypeDialog) {
            ListTypeDialog(
                title = "Task list type",
                currentType = currentProject.listType,
                onDismiss = { showListTypeDialog = false },
                onSave = ::updateListType,
            )
        }
    }

    taskToEdit?.let { task ->
        MoveEntityFormDialog(
            title = "Edit task",
            nameLabel = "Task name",
            initialName = task.name,
            initialDescription = task.description,
            destinationLabel = "Project",
            options = taskMoveProjects.map { MoveOption(it.id, it.name) },
            initialDestinationId = task.projectId,
            onDismiss = { taskToEdit = null },
            onSubmit = { name, description, destinationProjectId -> updateTask(task, name, description, destinationProjectId) },
        )
    }

    if (showPrintTaskSelection) {
        PrintTaskSelectionDialog(
            tasks = tasksInChecklistOrder(project, tasks).filterNot { it.isDone },
            onDismiss = { showPrintTaskSelection = false },
            onPrint = { selectedTaskIds ->
                showPrintTaskSelection = false
                printTasks(selectedTaskIds)
            },
        )
    }

}

private data class PendingDailyGeneration(val date: String, val carryOver: Boolean)

@Composable
private fun PrintTaskSelectionDialog(
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onPrint: (Set<String>) -> Unit,
) {
    val allTaskIds = tasks.mapTo(linkedSetOf()) { it.id }
    var selectedTaskIds by remember(tasks) { mutableStateOf<Set<String>>(allTaskIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print checklist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select tasks to print. Subtasks for selected tasks are included.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selectedTaskIds = allTaskIds }) { Text("Select all") }
                    TextButton(onClick = { selectedTaskIds = emptySet() }) { Text("Clear") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        val selected = task.id in selectedTaskIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTaskIds = if (selected) selectedTaskIds - task.id else selectedTaskIds + task.id
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                            )
                            Text(task.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedTaskIds.isNotEmpty(),
                onClick = { onPrint(selectedTaskIds) },
            ) { Text("Print") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun calculateSubTaskCounts(subTasks: List<com.ado.app.data.SubTask>): OpenDoneCounts =
    OpenDoneCounts(
        open = subTasks.count { !it.isDone },
        done = subTasks.count { it.isDone },
    )

private fun sortedTasksForProject(project: Project?, tasks: List<Task>): List<Task> {
    if (project?.coreKey != "daily") return tasks
    return tasks.sortedByDescending { taskCreatedAt(it) }
}

private fun tasksInChecklistOrder(project: Project?, tasks: List<Task>): List<Task> {
    val sorted = sortedTasksForProject(project, tasks)
    return sorted.filterNot { it.isDone } + sorted.filter { it.isDone }.sortedBy(::taskFinishedAt)
}

private fun subTasksInChecklistOrder(subTasks: List<com.ado.app.data.SubTask>): List<com.ado.app.data.SubTask> =
    subTasks.filterNot { it.isDone } + subTasks.filter { it.isDone }.sortedBy {
        it.finishedAt?.let { finishedAt ->
            try {
                Instant.parse(finishedAt)
            } catch (_: Exception) {
                Instant.MAX
            }
        } ?: Instant.MAX
    }

private fun taskCreatedAt(task: Task): Instant =
    try {
        Instant.parse(task.createdAt)
    } catch (_: Exception) {
        Instant.EPOCH
    }

private fun taskFinishedAt(task: Task): Instant {
    val finishedAt = task.finishedAt ?: return Instant.MAX
    return try {
        Instant.parse(finishedAt)
    } catch (_: Exception) {
        Instant.MAX
    }
}

private fun projectQuickAddActions(
    project: Project?,
    templates: List<Template>,
    onGenerateDaily: (String) -> Unit,
    onGenerateSeasonal: (String) -> Unit,
    onCreateFromTemplate: (Template) -> Unit,
): List<QuickAddAction> {
    val generatedActions = when (project?.coreKey) {
        "daily" -> listOf(
            QuickAddAction("Daily Today") { onGenerateDaily("today") },
            QuickAddAction("Daily Tomorrow") { onGenerateDaily("tomorrow") },
        )
        "home" -> listOf(
            QuickAddAction("Summer") { onGenerateSeasonal("summer_chores") },
            QuickAddAction("Fall") { onGenerateSeasonal("fall_chores") },
            QuickAddAction("Winter") { onGenerateSeasonal("winter_chores") },
            QuickAddAction("Spring") { onGenerateSeasonal("spring_chores") },
            QuickAddAction("Leaving house") { onGenerateSeasonal("leaving_house") },
        )
        else -> emptyList()
    }
    return generatedActions + templates.map { template ->
        QuickAddAction("From template: ${template.name}") { onCreateFromTemplate(template) }
    }
}

@Composable
private fun ProjectHeader(
    project: Project,
    onConfigureListType: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = project.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            ListTypeSettingsButton(onClick = onConfigureListType)
        }
        Text(
            text = "list: ${listTypeLabel(project.listType)}",
            style = MaterialTheme.typography.bodySmall,
            color = MutedTextColor,
        )
        if (project.description.isNotBlank()) {
            Text(project.description, style = MaterialTheme.typography.bodyMedium, color = MutedTextColor)
        }
        OpenDoneStatTiles(
            counts = OpenDoneCounts(open = project.taskCounts.open, done = project.taskCounts.done),
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}

private fun dailyDateLabel(date: String): String =
    when (date) {
        "today" -> "today's Daily list"
        "tomorrow" -> "tomorrow's Daily list"
        else -> "the Daily list for $date"
    }

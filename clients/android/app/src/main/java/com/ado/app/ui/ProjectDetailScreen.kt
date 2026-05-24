package com.ado.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ado.app.data.AdoRepository
import com.ado.app.data.CalendarDailyItem
import com.ado.app.data.CalendarEventReader
import com.ado.app.data.Project
import com.ado.app.data.SYNC_SYNCED
import com.ado.app.data.Task
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
    var showingCache by remember { mutableStateOf(false) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskMoveProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var pendingCount by remember { mutableStateOf(0) }
    var pendingDailyDate by remember { mutableStateOf<String?>(null) }
    var pendingCalendarDailyGeneration by remember { mutableStateOf<PendingDailyGeneration?>(null) }
    var simpleView by remember { mutableStateOf(false) }
    var subTaskCounts by remember { mutableStateOf<Map<String, OpenDoneCounts>>(emptyMap()) }
    var showOfflinePrompt by remember { mutableStateOf(false) }
    val offlineMode by repository.offlineModeFlow.collectAsState(initial = false)
    val rollUpCompleted by repository.rollUpCompletedFlow.collectAsState(initial = true)
    var finishedExpanded by remember(projectId) { mutableStateOf(!rollUpCompleted) }

    fun refreshSubTaskCounts(sourceTasks: List<Task>) {
        scope.launch {
            val cachedCounts = sourceTasks.associate { it.id to calculateSubTaskCounts(repository.getCachedSubTasks(it.id)) }
            subTaskCounts = cachedCounts
            sourceTasks.forEach { task ->
                val result = repository.getSubTasks(task.id)
                subTaskCounts = subTaskCounts + (task.id to calculateSubTaskCounts(result.data))
            }
        }
    }

    fun refresh(messageAfter: String? = null) {
        scope.launch {
            loading = true
            error = null
            repository.getProject(projectId).let { result ->
                project = result.data ?: project
                val resultError = if (repository.isOfflineMode()) null else result.errorMessage
                if (resultError != null) {
                    error = resultError
                    showingCache = result.fromCache
                    showOfflinePrompt = true
                }
            }
            val cachedTasks = repository.getCachedTasks(projectId)
            if (cachedTasks.isNotEmpty()) {
                tasks = cachedTasks
                showingCache = true
            }
            val result = repository.getTasks(projectId)
            tasks = result.data
            refreshSubTaskCounts(result.data)
            showingCache = result.fromCache
            val resultError = if (repository.isOfflineMode()) null else result.errorMessage
            error = resultError ?: error
            if (resultError != null) {
                showOfflinePrompt = true
            }
            pendingCount = repository.pendingMutationCount()
            loading = false
            if (messageAfter != null && error == null) {
                error = messageAfter
            }
        }
    }

    LaunchedEffect(offlineMode) {
        if (offlineMode) {
            error = null
            showOfflinePrompt = false
        }
    }

    LaunchedEffect(projectId, rollUpCompleted) {
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

    fun toggle(task: Task) {
        scope.launch {
            error = null
            try {
                val updated = repository.toggleTaskStatus(task)
                tasks = tasks.map { if (it.id == updated.id) updated else it }
                if (updated.syncStatus != SYNC_SYNCED) {
                    pendingCount = repository.pendingMutationCount()
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createTask(name: String, description: String, tags: List<String>) {
        scope.launch {
            error = null
            try {
                val created = repository.createTask(projectId, name, description)
                tasks = tasks.filterNot { it.id == created.id } + created
                showCreateTaskDialog = false
                refreshPendingCount()
                refresh()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun createBulkTasks(text: String) {
        scope.launch {
            error = null
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
                tasks = repository.getCachedTasks(projectId)
                refreshSubTaskCounts(tasks)
                showCreateTaskDialog = false
                pendingCount = repository.pendingMutationCount()
                error = if (createdTasks.any { it.syncStatus != SYNC_SYNCED }) null else "Created ${createdTasks.size} tasks and $createdSubTasks subtasks."
            } catch (e: Exception) {
                tasks = repository.getCachedTasks(projectId)
                refreshSubTaskCounts(tasks)
                pendingCount = repository.pendingMutationCount()
                error = repository.friendlyError(e)
            }
        }
    }

    fun deleteTask(task: Task) {
        scope.launch {
            error = null
            try {
                repository.deleteTask(task)
                tasks = tasks.filterNot { it.id == task.id }
                taskToDelete = null
                refreshPendingCount()
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
            try {
                val updated = repository.updateTask(task, name, description, targetProjectId)
                tasks = if (updated.projectId == projectId) {
                    tasks.map { if (it.id == updated.id) updated else it }
                } else {
                    tasks.filterNot { it.id == updated.id }
                }
                taskToEdit = null
                if (updated.syncStatus != SYNC_SYNCED) {
                    pendingCount = repository.pendingMutationCount()
                }
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun generateDaily(date: String, carryOver: Boolean, calendarItems: List<CalendarDailyItem> = emptyList(), calendarMessage: String? = null) {
        scope.launch {
            error = null
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
            try {
                repository.generateSeasonal(templateKey)
                refresh()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    LaunchedEffect(projectId) { refresh() }

    AdoScaffold(
        title = "Tasks",
        onBack = onBack,
        onSettings = onOpenSettings,
        offlineMode = offlineMode,
        onToggleOfflineMode = { setOfflineMode(!offlineMode) },
        bottomActions = listOf(
            BottomBarAction(
                label = "Add",
                onClick = { showCreateTaskDialog = true },
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
            if (!offlineMode && error != null && tasks.isEmpty()) {
                ErrorBanner(message = error ?: "Unable to load tasks", onRetry = { refresh() })
            } else if (!offlineMode && error != null && showingCache) {
                OfflineBanner("Showing cached tasks. ${error.orEmpty()}")
            } else if (!offlineMode && error != null) {
                OfflineBanner(error.orEmpty())
            }

            project?.let {
                ProjectHeader(
                    project = it,
                )
            }

            when {
                loading && tasks.isEmpty() -> LoadingState()
                tasks.isEmpty() -> EmptyState("No tasks for this project.")
                else -> LazyColumn {
                    val displayTasks = sortedTasksForProject(project, tasks)
                    val unfinishedTasks = displayTasks.filterNot { it.isDone }
                    val finishedTasks = displayTasks.filter { it.isDone }
                    items(unfinishedTasks, key = { it.id }) { task ->
                        if (simpleView) {
                            TaskSimpleRow(
                                task = task,
                                subTaskCounts = subTaskCounts[task.id],
                                onClick = { onOpenTask(task.id) },
                                onLongPress = { toggle(task) },
                            )
                        } else {
                            TaskRow(
                                task = task,
                                subTaskCounts = subTaskCounts[task.id],
                                onClick = { onOpenTask(task.id) },
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
                                        onLongPress = { toggle(task) },
                                    )
                                } else {
                                    TaskRow(
                                        task = task,
                                        subTaskCounts = subTaskCounts[task.id],
                                        onClick = { onOpenTask(task.id) },
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
            quickActionsLabel = "Generate",
            quickActions = projectQuickAddActions(
                project = project,
                onGenerateDaily = { date ->
                    showCreateTaskDialog = false
                    pendingDailyDate = date
                },
                onGenerateSeasonal = { templateKey ->
                    showCreateTaskDialog = false
                    generateSeasonal(templateKey)
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

private data class PendingDailyGeneration(val date: String, val carryOver: Boolean)

private fun calculateSubTaskCounts(subTasks: List<com.ado.app.data.SubTask>): OpenDoneCounts =
    OpenDoneCounts(
        open = subTasks.count { !it.isDone },
        done = subTasks.count { it.isDone },
    )

private fun sortedTasksForProject(project: Project?, tasks: List<Task>): List<Task> {
    if (project?.coreKey != "daily") return tasks
    return tasks.sortedByDescending { taskCreatedAt(it) }
}

private fun taskCreatedAt(task: Task): Instant =
    try {
        Instant.parse(task.createdAt)
    } catch (_: Exception) {
        Instant.EPOCH
    }

private fun projectQuickAddActions(
    project: Project?,
    onGenerateDaily: (String) -> Unit,
    onGenerateSeasonal: (String) -> Unit,
): List<QuickAddAction> =
    when (project?.coreKey) {
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

@Composable
private fun ProjectHeader(
    project: Project,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.headlineSmall,
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

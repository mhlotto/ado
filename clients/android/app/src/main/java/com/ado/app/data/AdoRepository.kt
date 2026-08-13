package com.ado.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

private data class DailyGeneratedSubTask(val name: String, val description: String)
private const val POSITION_NORMALIZATION_VERSION = 1
private data class ImportedDataset(
    val projects: List<Project>,
    val tasks: List<Task>,
    val subtasks: List<SubTask>,
    val templates: List<Template>,
)

data class LoadResult<T>(val data: T)

data class ImportPreview(
    val conflicts: Int,
    val projects: Int,
    val tasks: Int,
    val subtasks: Int,
    val templates: Int,
)

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val conflicts: Int,
)

data class TemplateApplyResult(val added: Int)

/** Android is self-contained. Room is the canonical storage for all app data. */
class AdoRepository(
    private val localStore: LocalStore,
    private val settingsStore: SettingsStore,
) {
    val rollUpCompletedFlow: Flow<Boolean> = settingsStore.rollUpCompletedFlow
    private val dataRevision = MutableStateFlow(0)
    val dataRevisionFlow: Flow<Int> = dataRevision

    private var initialized = false

    suspend fun setRollUpCompleted(enabled: Boolean) {
        settingsStore.saveRollUpCompleted(enabled)
    }

    suspend fun initialize() {
        ensureInitialized()
    }

    suspend fun getProjects(): LoadResult<List<Project>> {
        ensureInitialized()
        return LoadResult(projectsWithCounts())
    }

    suspend fun createProject(name: String, description: String, tags: List<String>): Project {
        ensureInitialized()
        val cleanName = requiredName(name)
        if (localStore.getProjects().any { it.name.equals(cleanName, ignoreCase = true) }) {
            throw IllegalArgumentException("A project with that name already exists.")
        }
        val project = Project(
            id = newId(),
            name = cleanName,
            description = description.trim(),
            tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
            isCore = false,
            coreKey = null,
            createdAt = now(),
            updatedAt = now(),
            deletedAt = null,
        )
        localStore.saveProject(project)
        return project
    }

    suspend fun getProject(projectId: String): LoadResult<Project?> {
        ensureInitialized()
        return LoadResult(localStore.getProject(projectId)?.let { withTaskCounts(it) })
    }

    suspend fun updateProject(project: Project, name: String, description: String, tags: List<String>): Project {
        ensureInitialized()
        val cleanName = requiredName(name)
        if (project.isCore && cleanName != project.name) {
            throw IllegalArgumentException("Core projects cannot be renamed.")
        }
        if (localStore.getProjects().any { it.id != project.id && it.name.equals(cleanName, ignoreCase = true) }) {
            throw IllegalArgumentException("A project with that name already exists.")
        }
        val updated = project.copy(
            name = cleanName,
            description = description.trim(),
            tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
            updatedAt = now(),
        )
        localStore.saveProject(updated)
        dataRevision.value += 1
        return withTaskCounts(updated)
    }

    suspend fun updateProjectListType(project: Project, listType: String): Project {
        ensureInitialized()
        val updated = project.copy(
            listType = validListType(listType),
            updatedAt = now(),
        )
        localStore.saveProject(updated)
        dataRevision.value += 1
        return withTaskCounts(updated)
    }

    suspend fun deleteProject(project: Project) {
        ensureInitialized()
        if (project.isCore) throw IllegalArgumentException("Core projects cannot be deleted.")
        localStore.getTasks(project.id).forEach { localStore.deleteTask(it) }
        localStore.deleteProject(project.id)
    }

    suspend fun getTasks(projectId: String): LoadResult<List<Task>> {
        ensureInitialized()
        return LoadResult(orderedTasks(localStore.getTasks(projectId)))
    }

    suspend fun getTaskMoveProjectOptions(): List<Project> {
        ensureInitialized()
        return localStore.getProjects().sortedBy { it.name.lowercase() }
    }

    suspend fun getSubTaskMoveTaskOptions(): List<Task> {
        ensureInitialized()
        return localStore.getProjects().flatMap { orderedTasks(localStore.getTasks(it.id)) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun createTask(
        projectId: String,
        name: String,
        description: String,
        listType: String = LIST_TYPE_NORMAL,
    ): Task {
        ensureInitialized()
        requireProject(projectId)
        val task = Task(
            id = newId(),
            projectId = projectId,
            name = requiredName(name),
            description = description.trim(),
            status = STATUS_TODO,
            createdAt = now(),
            finishedAt = null,
            updatedAt = now(),
            deletedAt = null,
            listType = listType,
            position = nextTaskPosition(projectId),
        )
        localStore.saveTask(task)
        return task
    }

    suspend fun getTask(taskId: String): LoadResult<Task?> {
        ensureInitialized()
        return LoadResult(localStore.getTask(taskId))
    }

    suspend fun updateTask(task: Task, name: String, description: String, projectId: String = task.projectId): Task {
        ensureInitialized()
        requireProject(projectId)
        val updated = task.copy(
            projectId = projectId,
            name = requiredName(name),
            description = description.trim(),
            updatedAt = now(),
            position = if (projectId == task.projectId) task.position else nextTaskPosition(projectId),
        )
        localStore.saveTask(updated)
        dataRevision.value += 1
        return updated
    }

    suspend fun updateTaskListType(task: Task, listType: String): Task {
        ensureInitialized()
        val updated = task.copy(
            listType = validListType(listType),
            updatedAt = now(),
        )
        localStore.saveTask(updated)
        dataRevision.value += 1
        return updated
    }

    suspend fun deleteTask(task: Task) {
        ensureInitialized()
        localStore.deleteTask(task)
    }

    suspend fun getSubTasks(taskId: String): LoadResult<List<SubTask>> {
        ensureInitialized()
        return LoadResult(orderedSubTasks(localStore.getSubTasks(taskId)))
    }

    suspend fun createSubTask(taskId: String, name: String, description: String): SubTask {
        ensureInitialized()
        requireTask(taskId)
        val subTask = SubTask(
            id = newId(),
            taskId = taskId,
            name = requiredName(name),
            description = description.trim(),
            status = STATUS_TODO,
            createdAt = now(),
            finishedAt = null,
            updatedAt = now(),
            deletedAt = null,
            position = nextSubTaskPosition(taskId),
        )
        localStore.saveSubTask(subTask)
        return subTask
    }

    suspend fun updateSubTask(subTask: SubTask, name: String, description: String, taskId: String = subTask.taskId): SubTask {
        ensureInitialized()
        requireTask(taskId)
        val updated = subTask.copy(
            taskId = taskId,
            name = requiredName(name),
            description = description.trim(),
            updatedAt = now(),
            position = if (taskId == subTask.taskId) subTask.position else nextSubTaskPosition(taskId),
        )
        localStore.saveSubTask(updated)
        return updated
    }

    suspend fun deleteSubTask(subTask: SubTask) {
        ensureInitialized()
        localStore.deleteSubTask(subTask)
    }

    suspend fun toggleTaskStatus(task: Task): Task {
        ensureInitialized()
        val status = toggledStatus(task.status)
        val updated = task.copy(
            status = status,
            finishedAt = if (status == STATUS_DONE) now() else null,
            updatedAt = now(),
        )
        localStore.saveTask(updated)
        return updated
    }

    suspend fun toggleSubTaskStatus(subTask: SubTask): SubTask {
        ensureInitialized()
        val status = toggledStatus(subTask.status)
        val updated = subTask.copy(
            status = status,
            finishedAt = if (status == STATUS_DONE) now() else null,
            updatedAt = now(),
        )
        localStore.saveSubTask(updated)
        return updated
    }

    suspend fun moveUnfinishedSubTask(taskId: String, subTaskId: String, delta: Int): List<SubTask> {
        ensureInitialized()
        val ordered = orderedSubTasks(localStore.getSubTasks(taskId))
        val unfinished = ordered.filterNot { it.isDone }.toMutableList()
        val from = unfinished.indexOfFirst { it.id == subTaskId }
        val to = from + delta
        if (from < 0 || to !in unfinished.indices) return ordered
        val item = unfinished.removeAt(from)
        unfinished.add(to, item)
        val savedPositions = ordered.filterNot { it.isDone }.map { it.position }.sorted()
        unfinished.forEachIndexed { index, subTask ->
            localStore.saveSubTask(subTask.copy(position = savedPositions[index], updatedAt = now()))
        }
        return orderedSubTasks(localStore.getSubTasks(taskId))
    }

    suspend fun generateDailyToday(carryOver: Boolean = false) {
        generateDaily("today", carryOver)
    }

    fun dailyTargetDate(dateAlias: String): LocalDate = resolveDailyTargetDate(dateAlias)

    suspend fun generateDaily(
        date: String,
        carryOver: Boolean = false,
        calendarItems: List<CalendarDailyItem> = emptyList(),
    ) {
        ensureInitialized()
        val targetDate = resolveDailyTargetDate(date)
        val dailyProject = coreProject("daily")
        if (localStore.getTasks(dailyProject.id).any { dailyTaskDate(it.name) == targetDate }) {
            throw IllegalArgumentException("That daily list already exists.")
        }
        val template = localStore.getTemplate("daily") ?: throw IllegalStateException("Daily template not found.")
        val defaults = template.items.sortedBy { it.position }.map { DailyGeneratedSubTask(it.name, it.description) }
        val carried = if (carryOver) dailyCarryOver(dailyProject.id, targetDate, defaults.map { it.name }.toSet()) else emptyList()
        val fromCalendar = calendarItems.map { DailyGeneratedSubTask(it.name, it.description) }
        createGeneratedTask(dailyProject.id, dailyTaskName(targetDate), LIST_TYPE_DAILY, defaults + carried + fromCalendar)
    }

    suspend fun generateSeasonal(templateKey: String) {
        ensureInitialized()
        val template = localStore.getTemplate(templateKey) ?: throw IllegalArgumentException("Template not found.")
        val homeProject = coreProject("home")
        val name = when (templateKey) {
            "summer_chores" -> "Summer chores ${LocalDate.now().year}"
            "fall_chores" -> "Fall chores ${LocalDate.now().year}"
            "winter_chores" -> "Winter chores ${LocalDate.now().year}"
            "spring_chores" -> "Spring chores ${LocalDate.now().year}"
            "leaving_house" -> "Leaving house"
            else -> template.name
        }
        if (localStore.getTasks(homeProject.id).any { it.name == name }) {
            throw IllegalArgumentException("That list already exists.")
        }
        createGeneratedTask(
            projectId = homeProject.id,
            name = name,
            listType = template.listType ?: LIST_TYPE_CHECKLIST,
            items = template.items.sortedBy { it.position }.map { DailyGeneratedSubTask(it.name, it.description) },
        )
    }

    suspend fun getTemplates(): LoadResult<List<Template>> {
        ensureInitialized()
        return LoadResult(localStore.getTemplates())
    }

    suspend fun getTemplate(templateKey: String): LoadResult<Template?> {
        ensureInitialized()
        return LoadResult(localStore.getTemplate(templateKey))
    }

    suspend fun updateTemplateItems(template: Template, items: List<TemplateItem>): Template {
        ensureInitialized()
        val updated = template.copy(
            items = items.mapIndexed { index, item ->
                item.copy(id = item.id ?: newId(), position = index)
            },
        )
        localStore.saveTemplate(updated)
        return updated
    }

    suspend fun updateTemplateListType(template: Template, listType: String): Template {
        ensureInitialized()
        val updated = template.copy(listType = validListType(listType))
        localStore.saveTemplate(updated)
        dataRevision.value += 1
        return updated
    }

    suspend fun applyTemplateToTask(taskId: String, templateKey: String): TemplateApplyResult {
        ensureInitialized()
        requireTask(taskId)
        val template = localStore.getTemplate(templateKey) ?: throw IllegalArgumentException("Template not found.")
        val existingNames = orderedSubTasks(localStore.getSubTasks(taskId))
            .filterNot { it.isDone }
            .mapTo(mutableSetOf()) { it.name.trim().lowercase() }
        var added = 0
        template.items.sortedBy { it.position }.forEach { item ->
            val normalizedName = item.name.trim().lowercase()
            if (normalizedName.isNotEmpty() && normalizedName !in existingNames) {
                createSubTask(taskId, item.name, item.description)
                existingNames += normalizedName
                added += 1
            }
        }
        return TemplateApplyResult(added)
    }

    suspend fun createTaskFromTemplate(projectId: String, templateKey: String): Task {
        ensureInitialized()
        val template = localStore.getTemplate(templateKey) ?: throw IllegalArgumentException("Template not found.")
        val task = createTask(
            projectId = projectId,
            name = template.name,
            description = template.description,
            listType = template.listType ?: LIST_TYPE_NORMAL,
        )
        template.items.sortedBy { it.position }.forEach { item ->
            createSubTask(task.id, item.name, item.description)
        }
        return task
    }

    suspend fun exportData(): String {
        ensureInitialized()
        val projects = localStore.getProjects()
        val tasks = projects.flatMap { localStore.getTasks(it.id) }
        val subtasks = tasks.flatMap { localStore.getSubTasks(it.id) }
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", CURRENT_BACKUP_VERSION)
            .put("exported_at", now())
            .put("projects", projects.map(::exportProject).asArray())
            .put("tasks", tasks.map(::exportTask).asArray())
            .put("subtasks", subtasks.map(::exportSubTask).asArray())
            .put("templates", localStore.getTemplates().map { it.toJson() }.asArray())
        return root.toString(2)
    }

    suspend fun previewImport(json: String): ImportPreview {
        val incoming = parseImport(json)
        ensureInitialized()
        val existingProjects = localStore.getProjects()
        val existingTasks = existingProjects.flatMap { localStore.getTasks(it.id) }
        val existingSubTasks = existingTasks.flatMap { localStore.getSubTasks(it.id) }
        val existingTemplates = localStore.getTemplates()
        val conflicts = incoming.projects.count { imported -> existingProjects.any { it.id == imported.id || (imported.coreKey != null && it.coreKey == imported.coreKey) } } +
            incoming.tasks.count { imported -> existingTasks.any { it.id == imported.id } } +
            incoming.subtasks.count { imported -> existingSubTasks.any { it.id == imported.id } } +
            incoming.templates.count { imported -> existingTemplates.any { it.templateKey == imported.templateKey } }
        return ImportPreview(conflicts, incoming.projects.size, incoming.tasks.size, incoming.subtasks.size, incoming.templates.size)
    }

    suspend fun importData(json: String, overwrite: Boolean): ImportResult {
        val incoming = parseImport(json)
        ensureInitialized()
        var imported = 0
        var skipped = 0
        var conflicts = 0
        val projectMap = mutableMapOf<String, String>()
        incoming.projects.forEach { source ->
            val existing = localStore.getProjects().firstOrNull { it.id == source.id || (source.coreKey != null && it.coreKey == source.coreKey) }
            if (existing != null) {
                conflicts++
                projectMap[source.id] = existing.id
                if (overwrite) {
                    localStore.saveProject(source.copy(id = existing.id, isCore = existing.isCore || source.isCore, coreKey = existing.coreKey ?: source.coreKey))
                    imported++
                } else skipped++
            } else {
                localStore.saveProject(source)
                projectMap[source.id] = source.id
                imported++
            }
        }
        incoming.tasks.forEach { source ->
            val targetProjectId = projectMap[source.projectId] ?: source.projectId
            if (localStore.getProject(targetProjectId) == null) { skipped++; return@forEach }
            val existing = localStore.getTask(source.id)
            if (existing != null) {
                conflicts++
                if (!overwrite) { skipped++; return@forEach }
            }
            localStore.saveTask(source.copy(projectId = targetProjectId))
            imported++
        }
        incoming.subtasks.forEach { source ->
            if (localStore.getTask(source.taskId) == null) { skipped++; return@forEach }
            val existing = localStore.getSubTask(source.id)
            if (existing != null) {
                conflicts++
                if (!overwrite) { skipped++; return@forEach }
            }
            localStore.saveSubTask(source)
            imported++
        }
        incoming.templates.forEach { source ->
            val existing = localStore.getTemplate(source.templateKey)
            if (existing != null) {
                conflicts++
                if (!overwrite) { skipped++; return@forEach }
            }
            localStore.saveTemplate(source)
            imported++
        }
        normalizeStoredPositions()
        dataRevision.value += 1
        return ImportResult(imported = imported, skipped = skipped, conflicts = conflicts)
    }

    fun friendlyError(error: Throwable): String = error.message ?: "Unable to complete operation."

    private suspend fun ensureInitialized() {
        if (initialized) return
        if (settingsStore.positionNormalizationVersion() < POSITION_NORMALIZATION_VERSION) {
            normalizeStoredPositions()
            settingsStore.savePositionNormalizationVersion(POSITION_NORMALIZATION_VERSION)
        }
        ensureCoreProject("Daily", "daily", LIST_TYPE_DAILY)
        ensureCoreProject("Home", "home", LIST_TYPE_CHECKLIST)
        ensureTemplates()
        initialized = true
    }

    private suspend fun normalizeStoredPositions() {
        localStore.getProjects().forEach { project ->
            val storedTasks = localStore.getTasks(project.id)
            val normalizedTaskList = normalizedTasks(storedTasks)
            storedTasks.zip(normalizedTaskList).forEach { (stored, normalized) ->
                if (stored != normalized) localStore.saveTask(normalized)

                val storedSubTasks = localStore.getSubTasks(stored.id)
                val normalizedSubTaskList = normalizedSubTasks(storedSubTasks)
                storedSubTasks.zip(normalizedSubTaskList).forEach { (storedSubTask, normalizedSubTask) ->
                    if (storedSubTask != normalizedSubTask) localStore.saveSubTask(normalizedSubTask)
                }
            }
        }
    }

    private suspend fun ensureCoreProject(name: String, coreKey: String, listType: String): Project {
        val existing = localStore.getProjects().firstOrNull { it.coreKey == coreKey }
        if (existing != null) return existing
        val created = Project(
            id = newId(), name = name, description = "", tags = emptyList(),
            isCore = true, coreKey = coreKey, createdAt = now(), updatedAt = now(), deletedAt = null,
            listType = listType,
        )
        localStore.saveProject(created)
        return created
    }

    private suspend fun ensureTemplates() {
        val templates = listOf(
            localTemplate("daily", "Daily", "daily", LIST_TYPE_DAILY, listOf("Review calendar", "Set priorities")),
            localTemplate("summer_chores", "Summer chores", "home", LIST_TYPE_CHECKLIST, listOf("Seasonal home check")),
            localTemplate("fall_chores", "Fall chores", "home", LIST_TYPE_CHECKLIST, listOf("Seasonal home check")),
            localTemplate("winter_chores", "Winter chores", "home", LIST_TYPE_CHECKLIST, listOf("Seasonal home check")),
            localTemplate("spring_chores", "Spring chores", "home", LIST_TYPE_CHECKLIST, listOf("Seasonal home check")),
            localTemplate("leaving_house", "Leaving house", "home", LIST_TYPE_CHECKLIST, listOf("Lights off", "Small appliances unplugged", "Refrigerator / freezer doors shut", "Oven / stove off", "Doors locked", "Garage door closed", "Alarm set")),
            localTemplate("market", "Market", null, LIST_TYPE_MARKET, listOf("Produce", "Bread", "Meat", "Dairy", "Frozen", "Household", "Other")),
        )
        val existing = localStore.getTemplates().associateBy { it.templateKey }
        templates.forEach { seed ->
            val saved = existing[seed.templateKey]
            when {
                saved == null -> localStore.saveTemplate(seed)
                saved.listType == null -> localStore.saveTemplate(saved.copy(listType = seed.listType))
            }
        }
    }

    private fun localTemplate(key: String, name: String, coreKey: String?, listType: String, names: List<String>): Template = Template(
        templateKey = key,
        name = name,
        projectCoreKey = coreKey,
        listType = listType,
        items = names.mapIndexed { position, item -> TemplateItem(newId(), item, "", position) },
    )

    private suspend fun projectsWithCounts(): List<Project> {
        val countsByProject = localStore.getTaskCountsByProject()
        return localStore.getProjects()
            .map { project -> project.copy(taskCounts = countsByProject[project.id] ?: TaskCounts()) }
            .sortedBy { it.name.lowercase() }
    }

    private suspend fun withTaskCounts(project: Project): Project {
        return project.copy(taskCounts = localStore.getTaskCountsByProject()[project.id] ?: TaskCounts())
    }

    private suspend fun coreProject(key: String): Project = localStore.getProjects().first { it.coreKey == key }
    private suspend fun requireProject(id: String) = localStore.getProject(id) ?: throw IllegalArgumentException("Project not found.")
    private suspend fun requireTask(id: String) = localStore.getTask(id) ?: throw IllegalArgumentException("Task not found.")

    private suspend fun createGeneratedTask(projectId: String, name: String, listType: String, items: List<DailyGeneratedSubTask>) {
        val task = createTask(projectId, name, "", listType)
        items.distinctBy { "${it.name.lowercase()}|${it.description.lowercase()}" }.forEach { createSubTask(task.id, it.name, it.description) }
    }

    private suspend fun dailyCarryOver(projectId: String, targetDate: LocalDate, defaults: Set<String>): List<DailyGeneratedSubTask> {
        val prior = localStore.getTasks(projectId)
            .mapNotNull { task -> dailyTaskDate(task.name)?.takeIf { it < targetDate }?.let { it to task } }
            .maxByOrNull { it.first }?.second ?: return emptyList()
        return localStore.getSubTasks(prior.id)
            .filter { !it.isDone && it.name !in defaults }
            .map { DailyGeneratedSubTask(it.name, it.description) }
    }

    private fun resolveDailyTargetDate(value: String): LocalDate = when (value.lowercase()) {
        "today" -> LocalDate.now()
        "tomorrow" -> LocalDate.now().plusDays(1)
        "yesterday" -> LocalDate.now().minusDays(1)
        else -> try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw IllegalArgumentException("Invalid daily date.") }
    }

    private fun dailyTaskDate(name: String): LocalDate? = try { LocalDate.parse(name.take(10)) } catch (_: Exception) { null }
    private fun dailyTaskName(date: LocalDate): String = "${date} ${date.format(DateTimeFormatter.ofPattern("EEEE"))}"
    private fun requiredName(value: String): String = value.trim().takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Name is required.")
    private fun validListType(value: String): String =
        value.takeIf { it in LIST_TYPES } ?: throw IllegalArgumentException("Invalid list type.")
    private fun newId(): String = UUID.randomUUID().toString()
    private fun now(): String = Instant.now().toString()

    private suspend fun nextTaskPosition(projectId: String): Int =
        localStore.getTasks(projectId).maxOfOrNull { it.position }?.coerceAtLeast(-1)?.plus(1) ?: 0

    private suspend fun nextSubTaskPosition(taskId: String): Int =
        localStore.getSubTasks(taskId).maxOfOrNull { it.position }?.coerceAtLeast(-1)?.plus(1) ?: 0

    private fun normalizedTasks(tasks: List<Task>): List<Task> {
        if (tasks.none { it.position >= 0 }) return tasks.mapIndexed { index, task -> task.copy(position = index) }
        var next = (tasks.maxOfOrNull { it.position } ?: -1) + 1
        return tasks.map { task -> if (task.position < 0) task.copy(position = next++) else task }
    }

    private fun normalizedSubTasks(subTasks: List<SubTask>): List<SubTask> {
        if (subTasks.none { it.position >= 0 }) return subTasks.mapIndexed { index, subTask -> subTask.copy(position = index) }
        var next = (subTasks.maxOfOrNull { it.position } ?: -1) + 1
        return subTasks.map { subTask -> if (subTask.position < 0) subTask.copy(position = next++) else subTask }
    }

    private fun orderedTasks(tasks: List<Task>): List<Task> =
        tasks.sortedWith(compareBy<Task> { it.position.takeIf { position -> position >= 0 } ?: Int.MAX_VALUE }.thenBy { it.createdAt })

    private fun orderedSubTasks(subTasks: List<SubTask>): List<SubTask> =
        subTasks.sortedWith(compareBy<SubTask> { it.position.takeIf { position -> position >= 0 } ?: Int.MAX_VALUE }.thenBy { it.createdAt })

    private fun parseImport(raw: String): ImportedDataset {
        val root = parseCurrentBackup(raw)
        fun objects(name: String): List<JSONObject> {
            val array = root.getJSONArray(name)
            return try {
                List(array.length()) { array.getJSONObject(it) }
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid backup file: '$name' must contain JSON objects.")
            }
        }
        return ImportedDataset(
            projects = objects("projects").map(Project::fromJson),
            tasks = objects("tasks").map(Task::fromJson),
            subtasks = objects("subtasks").map(SubTask::fromJson),
            templates = objects("templates").map(Template::fromJson),
        )
    }

    private fun exportProject(project: Project): JSONObject = JSONObject()
        .put("id", project.id).put("name", project.name).put("description", project.description)
        .put("tags", JSONArray(project.tags)).put("is_core", project.isCore).put("core_key", project.coreKey)
        .put("created_at", project.createdAt).put("updated_at", project.updatedAt).put("deleted_at", project.deletedAt)
        .put("list_type", project.listType)

    private fun exportTask(task: Task): JSONObject = JSONObject()
        .put("id", task.id).put("project_id", task.projectId).put("name", task.name).put("description", task.description)
        .put("status", task.status).put("created_at", task.createdAt).put("finished_at", task.finishedAt)
        .put("updated_at", task.updatedAt).put("deleted_at", task.deletedAt).put("list_type", task.listType).put("position", task.position)

    private fun exportSubTask(subTask: SubTask): JSONObject = JSONObject()
        .put("id", subTask.id).put("task_id", subTask.taskId).put("name", subTask.name).put("description", subTask.description)
        .put("status", subTask.status).put("created_at", subTask.createdAt).put("finished_at", subTask.finishedAt)
        .put("updated_at", subTask.updatedAt).put("deleted_at", subTask.deletedAt).put("position", subTask.position)

    private fun List<JSONObject>.asArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }
}

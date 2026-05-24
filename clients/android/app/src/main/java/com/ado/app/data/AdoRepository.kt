package com.ado.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private data class DailyGeneratedSubTask(val name: String, val description: String)
private class OfflineModeException : Exception("offline mode")

data class LoadResult<T>(
    val data: T,
    val fromCache: Boolean,
    val errorMessage: String? = null,
)

class AdoRepository(
    private val apiClient: ApiClient,
    private val localStore: LocalStore,
    private val settingsStore: SettingsStore,
) {
    val offlineModeFlow: Flow<Boolean> = settingsStore.offlineModeFlow
    val rollUpCompletedFlow: Flow<Boolean> = settingsStore.rollUpCompletedFlow

    suspend fun setOfflineMode(enabled: Boolean) {
        settingsStore.saveOfflineMode(enabled)
    }

    suspend fun setRollUpCompleted(enabled: Boolean) {
        settingsStore.saveRollUpCompleted(enabled)
    }

    suspend fun isOfflineMode(): Boolean = settingsStore.offlineModeFlow.first()

    suspend fun getProjects(): LoadResult<List<Project>> {
        val cached = localStore.getProjects()
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val fresh = apiClient.getProjects()
            val reconciled = fresh.map { reconcileProject(it, preservePending = true) }
            localStore.saveProjects(reconciled)
            LoadResult(localStore.getProjects(), fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun getCachedProjects(): List<Project> = localStore.getProjects()

    suspend fun createProject(name: String, description: String, tags: List<String>): Project {
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val created = reconcileProject(apiClient.createProject(name, description, tags))
            localStore.saveProject(created)
            created
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            val local = Project(
                id = newId(),
                serverId = null,
                name = name,
                description = description,
                tags = tags,
                isCore = false,
                coreKey = null,
                createdAt = now(),
                updatedAt = now(),
                deletedAt = null,
                syncStatus = SYNC_PENDING_CREATE,
            )
            localStore.saveProject(local)
            queue(ENTITY_PROJECT, MUTATION_CREATE, local.id)
            local
        }
    }

    suspend fun getProject(projectId: String): LoadResult<Project?> {
        val cached = localStore.getProject(projectId)
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val remoteId = cached?.serverId ?: projectId
            val fresh = reconcileProject(apiClient.getProject(remoteId), cached?.id, preservePending = true)
            localStore.saveProject(fresh)
            LoadResult(fresh, fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun updateProject(project: Project, name: String, description: String, tags: List<String>): Project {
        val local = project.copy(
            name = name,
            description = description,
            tags = tags,
            updatedAt = now(),
            syncStatus = if (project.syncStatus == SYNC_PENDING_CREATE) SYNC_PENDING_CREATE else SYNC_PENDING_UPDATE,
        )
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteId = project.serverId ?: throw IllegalStateException("project has not synced yet")
            val updated = reconcileProject(apiClient.updateProject(remoteId, name, description, tags), project.id)
            localStore.saveProject(updated)
            updated
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveProject(local)
            if (project.syncStatus != SYNC_PENDING_CREATE) {
                queue(ENTITY_PROJECT, MUTATION_UPDATE, project.id)
            }
            local
        }
    }

    suspend fun deleteProject(project: Project) {
        if (project.serverId == null || project.syncStatus == SYNC_PENDING_CREATE) {
            localStore.deleteProject(project.id)
            localStore.deletePendingMutationsForLocalId(project.id)
            return
        }
        try {
            if (isOfflineMode()) throw OfflineModeException()
            apiClient.deleteProject(project.serverId)
            localStore.deleteProject(project.id)
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveProject(project.copy(syncStatus = SYNC_PENDING_DELETE, deletedAt = now(), updatedAt = now()))
            queue(ENTITY_PROJECT, MUTATION_DELETE, project.id)
        }
    }

    suspend fun getTasks(projectId: String): LoadResult<List<Task>> {
        val cached = localStore.getTasks(projectId)
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val project = localStore.getProject(projectId)
            val remoteProjectId = project?.serverId ?: projectId
            val fresh = apiClient.getTasks(remoteProjectId).map { reconcileTask(it, projectId, preservePending = true) }
            localStore.saveTasks(projectId, fresh)
            LoadResult(localStore.getTasks(projectId), fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun getCachedTasks(projectId: String): List<Task> = localStore.getTasks(projectId)

    suspend fun getTaskMoveProjectOptions(): List<Project> {
        val cached = localStore.getProjects()
        if (cached.isNotEmpty() || isOfflineMode()) {
            return cached.filterNot { it.syncStatus == SYNC_PENDING_DELETE }.sortedBy { it.name.lowercase() }
        }
        return getProjects().data.filterNot { it.syncStatus == SYNC_PENDING_DELETE }.sortedBy { it.name.lowercase() }
    }

    suspend fun getSubTaskMoveTaskOptions(): List<Task> {
        val projects = getTaskMoveProjectOptions()
        if (!isOfflineMode()) {
            projects.forEach { project ->
                getTasks(project.id)
            }
        }
        return projects
            .flatMap { localStore.getTasks(it.id) }
            .filterNot { it.syncStatus == SYNC_PENDING_DELETE }
            .sortedWith(compareBy<Task> { it.name.lowercase() }.thenBy { it.createdAt })
    }

    suspend fun createTask(projectId: String, name: String, description: String): Task {
        val project = localStore.getProject(projectId)
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteProjectId = project?.serverId ?: projectId
            val created = reconcileTask(apiClient.createTask(remoteProjectId, name, description), projectId)
            localStore.saveTask(created)
            created
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            val task = Task(
                id = newId(),
                serverId = null,
                projectId = projectId,
                name = name,
                description = description,
                status = STATUS_TODO,
                createdAt = now(),
                finishedAt = null,
                updatedAt = now(),
                deletedAt = null,
                syncStatus = SYNC_PENDING_CREATE,
            )
            localStore.saveTask(task)
            queue(ENTITY_TASK, MUTATION_CREATE, task.id)
            task
        }
    }

    suspend fun getTask(taskId: String): LoadResult<Task?> {
        val cached = localStore.getTask(taskId)
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val remoteId = cached?.serverId ?: taskId
            val fresh = reconcileTask(apiClient.getTask(remoteId), cached?.projectId, cached?.id, preservePending = true)
            localStore.saveTask(fresh)
            LoadResult(fresh, fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun updateTask(task: Task, name: String, description: String, projectId: String = task.projectId): Task {
        val local = task.copy(
            projectId = projectId,
            name = name,
            description = description,
            updatedAt = now(),
            syncStatus = pendingUpdateStatus(task.syncStatus),
        )
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteId = task.serverId ?: throw IllegalStateException("task has not synced yet")
            val remoteProjectId = remoteProjectId(projectId)
            val updated = reconcileTask(apiClient.updateTask(remoteId, name, description, remoteProjectId), projectId, task.id)
            localStore.saveTask(updated)
            updated
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveTask(local)
            if (task.syncStatus != SYNC_PENDING_CREATE) {
                queue(ENTITY_TASK, MUTATION_UPDATE, task.id)
            }
            local
        }
    }

    suspend fun deleteTask(task: Task) {
        if (task.serverId == null || task.syncStatus == SYNC_PENDING_CREATE) {
            localStore.deleteTask(task)
            localStore.deletePendingMutationsForLocalId(task.id)
            return
        }
        try {
            if (isOfflineMode()) throw OfflineModeException()
            apiClient.deleteTask(task.serverId)
            localStore.deleteTask(task)
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveTask(task.copy(syncStatus = SYNC_PENDING_DELETE, deletedAt = now(), updatedAt = now()))
            queue(ENTITY_TASK, MUTATION_DELETE, task.id)
        }
    }

    suspend fun getSubTasks(taskId: String): LoadResult<List<SubTask>> {
        val cached = localStore.getSubTasks(taskId)
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val task = localStore.getTask(taskId)
            val remoteTaskId = task?.serverId ?: taskId
            val fresh = apiClient.getSubTasks(remoteTaskId).map { reconcileSubTask(it, taskId, preservePending = true) }
            localStore.saveSubTasks(taskId, fresh)
            LoadResult(localStore.getSubTasks(taskId), fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun getCachedSubTasks(taskId: String): List<SubTask> = localStore.getSubTasks(taskId)

    suspend fun createSubTask(taskId: String, name: String, description: String): SubTask {
        val task = localStore.getTask(taskId)
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteTaskId = task?.serverId ?: taskId
            val created = reconcileSubTask(apiClient.createSubTask(remoteTaskId, name, description), taskId)
            localStore.saveSubTask(created)
            created
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            val subTask = SubTask(
                id = newId(),
                serverId = null,
                taskId = taskId,
                name = name,
                description = description,
                status = STATUS_TODO,
                createdAt = now(),
                finishedAt = null,
                updatedAt = now(),
                deletedAt = null,
                syncStatus = SYNC_PENDING_CREATE,
            )
            localStore.saveSubTask(subTask)
            queue(ENTITY_SUBTASK, MUTATION_CREATE, subTask.id)
            subTask
        }
    }

    suspend fun updateSubTask(subTask: SubTask, name: String, description: String, taskId: String = subTask.taskId): SubTask {
        val local = subTask.copy(
            taskId = taskId,
            name = name,
            description = description,
            updatedAt = now(),
            syncStatus = pendingUpdateStatus(subTask.syncStatus),
        )
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteId = subTask.serverId ?: throw IllegalStateException("subtask has not synced yet")
            val remoteTaskId = remoteTaskId(taskId)
            val updated = reconcileSubTask(apiClient.updateSubTask(remoteId, name, description, remoteTaskId), taskId, subTask.id)
            localStore.saveSubTask(updated)
            updated
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveSubTask(local)
            if (subTask.syncStatus != SYNC_PENDING_CREATE) {
                queue(ENTITY_SUBTASK, MUTATION_UPDATE, subTask.id)
            }
            local
        }
    }

    suspend fun deleteSubTask(subTask: SubTask) {
        if (subTask.serverId == null || subTask.syncStatus == SYNC_PENDING_CREATE) {
            localStore.deleteSubTask(subTask)
            localStore.deletePendingMutationsForLocalId(subTask.id)
            return
        }
        try {
            if (isOfflineMode()) throw OfflineModeException()
            apiClient.deleteSubTask(subTask.serverId)
            localStore.deleteSubTask(subTask)
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveSubTask(subTask.copy(syncStatus = SYNC_PENDING_DELETE, deletedAt = now(), updatedAt = now()))
            queue(ENTITY_SUBTASK, MUTATION_DELETE, subTask.id)
        }
    }

    suspend fun toggleTaskStatus(task: Task): Task {
        val status = toggledStatus(task.status)
        val local = task.copy(
            status = status,
            finishedAt = if (status == STATUS_DONE) now() else null,
            updatedAt = now(),
            syncStatus = pendingUpdateStatus(task.syncStatus),
        )
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteId = task.serverId ?: throw IllegalStateException("task has not synced yet")
            apiClient.patchTaskStatus(remoteId, status)
            val updated = reconcileTask(apiClient.getTask(remoteId), task.projectId, task.id)
            localStore.saveTask(updated)
            updated
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveTask(local)
            if (task.syncStatus != SYNC_PENDING_CREATE) {
                queue(ENTITY_TASK, MUTATION_UPDATE, task.id)
            }
            local
        }
    }

    suspend fun toggleSubTaskStatus(subTask: SubTask): SubTask {
        val status = toggledStatus(subTask.status)
        val local = subTask.copy(
            status = status,
            finishedAt = if (status == STATUS_DONE) now() else null,
            updatedAt = now(),
            syncStatus = pendingUpdateStatus(subTask.syncStatus),
        )
        return try {
            if (isOfflineMode()) throw OfflineModeException()
            val remoteId = subTask.serverId ?: throw IllegalStateException("subtask has not synced yet")
            apiClient.patchSubTaskStatus(remoteId, status)
            val updated = reconcileSubTask(apiClient.getSubTask(remoteId), subTask.taskId, subTask.id)
            localStore.saveSubTask(updated)
            updated
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
            localStore.saveSubTask(local)
            if (subTask.syncStatus != SYNC_PENDING_CREATE) {
                queue(ENTITY_SUBTASK, MUTATION_UPDATE, subTask.id)
            }
            local
        }
    }

    suspend fun generateDailyToday(carryOver: Boolean = false) {
        generateDaily("today", carryOver)
    }

    fun dailyTargetDate(dateAlias: String): LocalDate = resolveDailyTargetDate(dateAlias)

    suspend fun generateDaily(
        dateAlias: String,
        carryOver: Boolean = false,
        calendarItems: List<CalendarDailyItem> = emptyList(),
    ) {
        generateDaily(resolveDailyTargetDate(dateAlias), carryOver, calendarItems)
    }

    suspend fun generateSeasonal(templateKey: String) {
        generateSeasonal(templateKey, LocalDate.now().year)
    }

    suspend fun getTemplates(): LoadResult<List<Template>> {
        val cached = localStore.getTemplates()
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val fresh = apiClient.getTemplates()
            localStore.saveTemplates(fresh)
            LoadResult(fresh, fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun getCachedTemplates(): List<Template> = localStore.getTemplates()

    suspend fun getTemplate(templateKey: String): LoadResult<Template?> {
        val cached = localStore.getTemplate(templateKey)
        if (isOfflineMode()) return LoadResult(cached, fromCache = true)
        return try {
            val fresh = apiClient.getTemplate(templateKey)
            localStore.saveTemplate(fresh)
            LoadResult(fresh, fromCache = false)
        } catch (e: Exception) {
            LoadResult(cached, fromCache = true, errorMessage = friendlyError(e))
        }
    }

    suspend fun updateTemplateItems(template: Template, items: List<TemplateItem>): Template {
        if (isOfflineMode()) throw OfflineModeException()
        val updated = apiClient.updateTemplateItems(template.templateKey, items)
        localStore.saveTemplate(updated)
        return updated
    }

    suspend fun testConnection(): Boolean {
        val ok = apiClient.testConnection()
        if (ok) setOfflineMode(false)
        return ok
    }

    suspend fun pendingMutationCount(): Int = localStore.pendingMutationCount()

    suspend fun pendingMutations(): List<PendingMutation> = localStore.getPendingMutations()

    suspend fun syncPendingMutations(): SyncResult {
        if (isOfflineMode()) {
            return SyncResult(localStore.pendingMutationCount(), 0, 0)
        }
        val mutations = localStore.getPendingMutations().sortedWith(mutationComparator())
        var synced = 0
        var failed = 0
        for (mutation in mutations) {
            try {
                val completed = replayMutation(mutation)
                if (completed) {
                    localStore.deletePendingMutation(mutation.id)
                    synced += 1
                } else {
                    failed += 1
                    localStore.updatePendingMutationFailure(
                        mutation.id,
                        mutation.attempts + 1,
                        "Waiting for parent item to sync.",
                    )
                }
            } catch (e: Exception) {
                failed += 1
                localStore.updatePendingMutationFailure(
                    mutation.id,
                    mutation.attempts + 1,
                    friendlyError(e),
                )
            }
        }
        return SyncResult(mutations.size, synced, failed)
    }

    fun friendlyError(error: Throwable): String {
        if (error is AdoApiException) {
            if (error.statusCode == 409) return "That list already exists."
            return error.message
        }
        return error.message ?: "Request failed"
    }

    private suspend fun replayMutation(mutation: PendingMutation): Boolean {
        return when (mutation.entityType to mutation.operation) {
            ENTITY_PROJECT to MUTATION_CREATE -> syncCreateProject(mutation.localId)
            ENTITY_PROJECT to MUTATION_UPDATE -> syncUpdateProject(mutation.localId)
            ENTITY_PROJECT to MUTATION_DELETE -> syncDeleteProject(mutation.localId)
            ENTITY_TASK to MUTATION_CREATE -> syncCreateTask(mutation.localId)
            ENTITY_TASK to MUTATION_UPDATE -> syncUpdateTask(mutation.localId)
            ENTITY_TASK to MUTATION_DELETE -> syncDeleteTask(mutation.localId)
            ENTITY_SUBTASK to MUTATION_CREATE -> syncCreateSubTask(mutation.localId)
            ENTITY_SUBTASK to MUTATION_UPDATE -> syncUpdateSubTask(mutation.localId)
            ENTITY_SUBTASK to MUTATION_DELETE -> syncDeleteSubTask(mutation.localId)
            ENTITY_GENERATION to MUTATION_GENERATE -> syncGenerate(mutation)
            else -> true
        }
    }

    private suspend fun syncCreateProject(localId: String): Boolean {
        val project = localStore.getProject(localId) ?: return true
        if (project.serverId != null) return true
        val created = apiClient.createProject(project.name, project.description, project.tags)
        localStore.saveProject(project.copy(serverId = created.id, syncStatus = SYNC_SYNCED, createdAt = created.createdAt, updatedAt = created.updatedAt))
        return true
    }

    private suspend fun syncUpdateProject(localId: String): Boolean {
        val project = localStore.getProject(localId) ?: return true
        val remoteId = project.serverId ?: return false
        val updated = reconcileProject(apiClient.updateProject(remoteId, project.name, project.description, project.tags), project.id)
        localStore.saveProject(updated)
        return true
    }

    private suspend fun syncDeleteProject(localId: String): Boolean {
        val project = localStore.getProject(localId) ?: return true
        project.serverId?.let { apiClient.deleteProject(it) }
        localStore.deleteProject(localId)
        return true
    }

    private suspend fun syncCreateTask(localId: String): Boolean {
        val task = localStore.getTask(localId) ?: return true
        if (task.serverId != null) return true
        val project = localStore.getProject(task.projectId) ?: return false
        val remoteProjectId = project.serverId ?: return false
        var created = apiClient.createTask(remoteProjectId, task.name, task.description)
        if (task.status != created.status) {
            created = apiClient.patchTaskStatus(created.id, task.status)
        }
        localStore.saveTask(task.copy(
            serverId = created.id,
            status = created.status,
            finishedAt = created.finishedAt,
            createdAt = created.createdAt,
            updatedAt = created.updatedAt,
            syncStatus = SYNC_SYNCED,
        ))
        return true
    }

    private suspend fun syncUpdateTask(localId: String): Boolean {
        val task = localStore.getTask(localId) ?: return true
        val remoteId = task.serverId ?: return false
        val remoteProjectId = localStore.getProject(task.projectId)?.serverId ?: return false
        apiClient.updateTask(remoteId, task.name, task.description, remoteProjectId)
        val updated = reconcileTask(apiClient.patchTaskStatus(remoteId, task.status), task.projectId, task.id)
        localStore.saveTask(updated)
        return true
    }

    private suspend fun syncDeleteTask(localId: String): Boolean {
        val task = localStore.getTask(localId) ?: return true
        task.serverId?.let { apiClient.deleteTask(it) }
        localStore.deleteTask(task)
        return true
    }

    private suspend fun syncCreateSubTask(localId: String): Boolean {
        val subTask = localStore.getSubTask(localId) ?: return true
        if (subTask.serverId != null) return true
        val task = localStore.getTask(subTask.taskId) ?: return false
        val remoteTaskId = task.serverId ?: return false
        var created = apiClient.createSubTask(remoteTaskId, subTask.name, subTask.description)
        if (subTask.status != created.status) {
            created = apiClient.patchSubTaskStatus(created.id, subTask.status)
        }
        localStore.saveSubTask(subTask.copy(
            serverId = created.id,
            status = created.status,
            finishedAt = created.finishedAt,
            createdAt = created.createdAt,
            updatedAt = created.updatedAt,
            syncStatus = SYNC_SYNCED,
        ))
        return true
    }

    private suspend fun syncUpdateSubTask(localId: String): Boolean {
        val subTask = localStore.getSubTask(localId) ?: return true
        val remoteId = subTask.serverId ?: return false
        val remoteTaskId = localStore.getTask(subTask.taskId)?.serverId ?: return false
        apiClient.updateSubTask(remoteId, subTask.name, subTask.description, remoteTaskId)
        val updated = reconcileSubTask(apiClient.patchSubTaskStatus(remoteId, subTask.status), subTask.taskId, subTask.id)
        localStore.saveSubTask(updated)
        return true
    }

    private suspend fun syncDeleteSubTask(localId: String): Boolean {
        val subTask = localStore.getSubTask(localId) ?: return true
        subTask.serverId?.let { apiClient.deleteSubTask(it) }
        localStore.deleteSubTask(subTask)
        return true
    }

    private suspend fun reconcileProject(remote: Project, localId: String? = null, preservePending: Boolean = false): Project {
        val existing = localId?.let { localStore.getProject(it) } ?: remote.serverId?.let { localStore.getProjectByServerId(it) }
        if (preservePending && existing?.syncStatus != null && existing.syncStatus != SYNC_SYNCED) {
            return existing
        }
        return remote.copy(id = existing?.id ?: remote.id, serverId = remote.serverId ?: remote.id, syncStatus = SYNC_SYNCED)
    }

    private suspend fun reconcileTask(
        remote: Task,
        projectId: String? = null,
        localId: String? = null,
        preservePending: Boolean = false,
    ): Task {
        val existing = localId?.let { localStore.getTask(it) } ?: remote.serverId?.let { localStore.getTaskByServerId(it) }
        if (preservePending && existing?.syncStatus != null && existing.syncStatus != SYNC_SYNCED) {
            return existing
        }
        return remote.copy(
            id = existing?.id ?: remote.id,
            serverId = remote.serverId ?: remote.id,
            projectId = projectId ?: existing?.projectId ?: remote.projectId,
            syncStatus = SYNC_SYNCED,
        )
    }

    private suspend fun reconcileSubTask(
        remote: SubTask,
        taskId: String? = null,
        localId: String? = null,
        preservePending: Boolean = false,
    ): SubTask {
        val existing = localId?.let { localStore.getSubTask(it) } ?: remote.serverId?.let { localStore.getSubTaskByServerId(it) }
        if (preservePending && existing?.syncStatus != null && existing.syncStatus != SYNC_SYNCED) {
            return existing
        }
        return remote.copy(
            id = existing?.id ?: remote.id,
            serverId = remote.serverId ?: remote.id,
            taskId = taskId ?: existing?.taskId ?: remote.taskId,
            syncStatus = SYNC_SYNCED,
        )
    }

    private suspend fun remoteProjectId(projectId: String): String {
        val project = localStore.getProject(projectId)
        return project?.serverId ?: throw IllegalStateException("target project has not synced yet")
    }

    private suspend fun remoteTaskId(taskId: String): String {
        val task = localStore.getTask(taskId)
        return task?.serverId ?: throw IllegalStateException("target task has not synced yet")
    }

    private suspend fun generateDaily(
        date: LocalDate,
        carryOver: Boolean,
        calendarItems: List<CalendarDailyItem> = emptyList(),
    ) {
        val project = localStore.getProjects().firstOrNull { it.coreKey == "daily" }
        val dateName = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val taskName = dailyTaskName(date)
        val carryOverItems = if (carryOver && project != null) dailyCarryOverItems(project, date) else emptyList()
        val normalizedCalendarItems = dedupeGeneratedSubTasks(calendarItems.map { it.toGeneratedSubTask() })
        val payload = generationPayload("daily", "date", dateName, carryOverItems, normalizedCalendarItems)
        try {
            if (isOfflineMode()) throw OfflineModeException()
            val generated = apiClient.generateDaily(dateName)
            if (project != null && (carryOverItems.isNotEmpty() || normalizedCalendarItems.isNotEmpty())) {
                addExtraItemsToGeneratedDaily(project, generated, carryOverItems, normalizedCalendarItems)
            }
            return
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
        }
        if (project != null) {
            createGeneratedPlaceholder(project, "daily", taskName, payload, carryOverItems, normalizedCalendarItems)
        }
    }

    private suspend fun generateSeasonal(templateKey: String, year: Int) {
        val project = localStore.getProjects().firstOrNull { it.coreKey == "home" }
        val payload = generationPayload(templateKey, "year", year.toString())
        try {
            if (isOfflineMode()) throw OfflineModeException()
            apiClient.generateSeasonal(templateKey, year)
            return
        } catch (e: Exception) {
            if (!shouldStoreOffline(e)) throw e
        }
        if (project != null) {
            createGeneratedPlaceholder(project, templateKey, seasonalTaskName(templateKey, year), payload)
        }
    }

    private suspend fun createGeneratedPlaceholder(
        project: Project,
        templateKey: String,
        name: String,
        payload: String,
        carryOverItems: List<DailyGeneratedSubTask> = emptyList(),
        calendarItems: List<DailyGeneratedSubTask> = emptyList(),
    ) {
        val existing = localStore.getTasks(project.id).firstOrNull { it.name == name && it.syncStatus == SYNC_PENDING_CREATE }
        if (existing != null) return
        val createdAt = now()
        val task = Task(
            id = newId(),
            serverId = null,
            projectId = project.id,
            name = name,
            description = "Pending generated list",
            status = STATUS_TODO,
            createdAt = createdAt,
            finishedAt = null,
            updatedAt = createdAt,
            deletedAt = null,
            syncStatus = SYNC_PENDING_CREATE,
        )
        localStore.saveTask(task)
        val template = localStore.getTemplate(templateKey)
        template?.items.orEmpty().sortedBy { it.position }.forEach { item ->
            localStore.saveSubTask(
                SubTask(
                    id = newId(),
                    serverId = null,
                    taskId = task.id,
                    name = item.name,
                    description = item.description,
                    status = STATUS_TODO,
                    createdAt = createdAt,
                    finishedAt = null,
                    updatedAt = createdAt,
                    deletedAt = null,
                    syncStatus = SYNC_PENDING_CREATE,
                ),
            )
        }
        dedupeGeneratedSubTasks(carryOverItems + calendarItems).forEach { item ->
            localStore.saveSubTask(
                SubTask(
                    id = newId(),
                    serverId = null,
                    taskId = task.id,
                    name = item.name,
                    description = item.description,
                    status = STATUS_TODO,
                    createdAt = createdAt,
                    finishedAt = null,
                    updatedAt = createdAt,
                    deletedAt = null,
                    syncStatus = SYNC_PENDING_CREATE,
                ),
            )
        }
        queue(ENTITY_GENERATION, MUTATION_GENERATE, task.id, payload)
    }

    private suspend fun syncGenerate(mutation: PendingMutation): Boolean {
        val json = org.json.JSONObject(mutation.payload)
        val templateKey = json.optString("template_key")
        val carryOverItems = generatedSubTasksFromPayload(json, "carry_over_items")
        val calendarItems = generatedSubTasksFromPayload(json, "calendar_items")
        val generated = try {
            if (templateKey == "daily") {
                apiClient.generateDaily(json.optString("date"))
            } else {
                apiClient.generateSeasonal(templateKey, json.optInt("year", LocalDate.now().year))
            }
        } catch (e: AdoApiException) {
            if (e.statusCode == 409) {
                localStore.getTask(mutation.localId)?.let { localStore.deleteTask(it) }
                return true
            }
            throw e
        }
        val placeholder = localStore.getTask(mutation.localId)
        if (placeholder != null) {
            dedupeGeneratedSubTasks(carryOverItems + calendarItems).forEach { item ->
                apiClient.createSubTask(generated.taskId, item.name, item.description)
            }
            val synced = placeholder.copy(
                serverId = generated.taskId,
                projectId = placeholder.projectId,
                name = generated.name,
                description = "",
                syncStatus = SYNC_SYNCED,
                updatedAt = now(),
            )
            localStore.saveTask(synced)
            val remoteSubTasks = apiClient.getSubTasks(generated.taskId).map { reconcileSubTask(it, placeholder.id) }
            localStore.saveSubTasks(placeholder.id, remoteSubTasks)
        }
        return true
    }

    private suspend fun queue(entityType: String, operation: String, localId: String, payload: String = "") {
        val existing = localStore.getPendingMutations().firstOrNull {
            it.entityType == entityType &&
                it.operation == operation &&
                (it.localId == localId || (payload.isNotBlank() && it.payload == payload))
        }
        localStore.savePendingMutation(
            existing ?: PendingMutation(
                id = newId(),
                entityType = entityType,
                operation = operation,
                localId = localId,
                createdAt = now(),
                payload = payload,
            ),
        )
    }

    private fun pendingUpdateStatus(current: String): String =
        if (current == SYNC_PENDING_CREATE) SYNC_PENDING_CREATE else SYNC_PENDING_UPDATE

    private fun shouldStoreOffline(error: Exception): Boolean =
        error !is AdoApiException || error.statusCode == null || error.statusCode >= 500

    private fun mutationComparator(): Comparator<PendingMutation> {
        fun operationRank(operation: String) = when (operation) {
            MUTATION_CREATE -> 0
            MUTATION_UPDATE -> 1
            MUTATION_DELETE -> 2
            else -> 3
        }
        fun entityRank(entityType: String) = when (entityType) {
            ENTITY_PROJECT -> 0
            ENTITY_TASK -> 1
            ENTITY_SUBTASK -> 2
            ENTITY_GENERATION -> 3
            else -> 3
        }
        return compareBy<PendingMutation> { operationRank(it.operation) }
            .thenBy { entityRank(it.entityType) }
            .thenBy { it.createdAt }
    }

    private fun now(): String = Instant.now().toString()

    private fun newId(): String = UUID.randomUUID().toString()

    private suspend fun dailyCarryOverItems(project: Project, targetDate: LocalDate): List<DailyGeneratedSubTask> {
        val defaultNames = dailyDefaultNames()
        val tasks = freshOrCachedDailyTasks(project)
        val previousTask = tasks
            .mapNotNull { task -> dailyTaskDate(task.name)?.let { it to task } }
            .filter { (date, _) -> date.isBefore(targetDate) }
            .maxByOrNull { (date, _) -> date }
            ?.second
            ?: return emptyList()

        val subtasks = freshOrCachedSubTasks(previousTask)
        val seen = mutableSetOf<String>()
        return subtasks.mapNotNull { subTask ->
            val normalized = normalizeCarryOverName(subTask.name)
            if (normalized.isBlank() ||
                normalized in defaultNames ||
                normalized in seen ||
                subTask.status == STATUS_DONE ||
                subTask.status == "archived"
            ) {
                null
            } else {
                seen += normalized
                DailyGeneratedSubTask(subTask.name, subTask.description)
            }
        }
    }

    private suspend fun dailyDefaultNames(): Set<String> {
        val cached = localStore.getTemplate("daily")
        if (isOfflineMode()) {
            return cached?.items?.map { normalizeCarryOverName(it.name) }?.toSet()
                ?: setOf("review calendar", "set priorities").map(::normalizeCarryOverName).toSet()
        }
        val template = cached ?: try {
            apiClient.getTemplate("daily").also { localStore.saveTemplate(it) }
        } catch (_: Exception) {
            null
        }
        return template?.items?.map { normalizeCarryOverName(it.name) }?.toSet()
            ?: setOf("review calendar", "set priorities").map(::normalizeCarryOverName).toSet()
    }

    private suspend fun freshOrCachedDailyTasks(project: Project): List<Task> {
        if (isOfflineMode()) {
            return localStore.getTasks(project.id)
        }
        return try {
            val remoteProjectId = project.serverId ?: project.id
            val fresh = apiClient.getTasks(remoteProjectId).map { reconcileTask(it, project.id, preservePending = true) }
            localStore.saveTasks(project.id, fresh)
            localStore.getTasks(project.id)
        } catch (_: Exception) {
            localStore.getTasks(project.id)
        }
    }

    private suspend fun freshOrCachedSubTasks(task: Task): List<SubTask> {
        if (isOfflineMode()) {
            return localStore.getSubTasks(task.id)
        }
        return try {
            val remoteTaskId = task.serverId ?: task.id
            val fresh = apiClient.getSubTasks(remoteTaskId).map { reconcileSubTask(it, task.id, preservePending = true) }
            localStore.saveSubTasks(task.id, fresh)
            localStore.getSubTasks(task.id)
        } catch (_: Exception) {
            localStore.getSubTasks(task.id)
        }
    }

    private suspend fun addExtraItemsToGeneratedDaily(
        project: Project,
        generated: GeneratedTask,
        carryOverItems: List<DailyGeneratedSubTask>,
        calendarItems: List<DailyGeneratedSubTask>,
    ) {
        dedupeGeneratedSubTasks(carryOverItems + calendarItems).forEach { item ->
            apiClient.createSubTask(generated.taskId, item.name, item.description)
        }
        val task = reconcileTask(apiClient.getTask(generated.taskId), project.id)
        localStore.saveTask(task)
        val subtasks = apiClient.getSubTasks(generated.taskId).map { reconcileSubTask(it, task.id) }
        localStore.saveSubTasks(task.id, subtasks)
    }

    private fun generatedSubTasksFromPayload(json: org.json.JSONObject, key: String): List<DailyGeneratedSubTask> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: org.json.JSONObject()
            DailyGeneratedSubTask(
                name = item.optString("name"),
                description = item.optString("description"),
            )
        }.filter { it.name.isNotBlank() }
    }

    private fun CalendarDailyItem.toGeneratedSubTask(): DailyGeneratedSubTask =
        DailyGeneratedSubTask(
            name = name,
            description = description.ifBlank { CALENDAR_ITEM_TAG },
        )

    private fun dedupeGeneratedSubTasks(items: List<DailyGeneratedSubTask>): List<DailyGeneratedSubTask> {
        val seen = mutableSetOf<String>()
        return items.filter { item ->
            val key = "${normalizeCarryOverName(item.name)}|${normalizeCarryOverName(item.description)}"
            key.isNotBlank() && seen.add(key)
        }
    }

    private fun dailyTaskDate(name: String): LocalDate? =
        try {
            LocalDate.parse(name.trim().take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun resolveDailyTargetDate(raw: String): LocalDate {
        return when (raw.trim().lowercase()) {
            "", "today" -> LocalDate.now()
            "tomorrow" -> LocalDate.now().plusDays(1)
            "yesterday" -> LocalDate.now().minusDays(1)
            else -> LocalDate.parse(raw.trim().take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    private fun dailyTaskName(date: LocalDate): String =
        "${date.format(DateTimeFormatter.ISO_LOCAL_DATE)} ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}"

    private fun normalizeCarryOverName(name: String): String =
        name.trim().lowercase().replace(Regex("""\s+"""), " ")

    private fun generationPayload(
        templateKey: String,
        valueName: String,
        value: String,
        carryOverItems: List<DailyGeneratedSubTask> = emptyList(),
        calendarItems: List<DailyGeneratedSubTask> = emptyList(),
    ): String =
        org.json.JSONObject()
            .put("template_key", templateKey)
            .put(valueName, value)
            .put(
                "carry_over_items",
                org.json.JSONArray().also { array ->
                    carryOverItems.forEach { item ->
                        array.put(
                            org.json.JSONObject()
                                .put("name", item.name)
                                .put("description", item.description),
                        )
                    }
                },
            )
            .put(
                "calendar_items",
                org.json.JSONArray().also { array ->
                    calendarItems.forEach { item ->
                        array.put(
                            org.json.JSONObject()
                                .put("name", item.name)
                                .put("description", item.description),
                        )
                    }
                },
            )
            .toString()

    private fun seasonalTaskName(templateKey: String, year: Int): String {
        if (templateKey == "leaving_house") {
            return "Leaving house"
        }
        val label = when (templateKey) {
            "summer_chores" -> "Summer chores"
            "fall_chores" -> "Fall chores"
            "winter_chores" -> "Winter chores"
            "spring_chores" -> "Spring chores"
            else -> templateKey
        }
        return "$label $year"
    }
}

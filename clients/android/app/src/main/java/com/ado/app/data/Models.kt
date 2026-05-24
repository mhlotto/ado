package com.ado.app.data

import org.json.JSONArray
import org.json.JSONObject

const val STATUS_TODO = "todo"
const val STATUS_DONE = "done"
const val SYNC_SYNCED = "synced"
const val SYNC_PENDING_CREATE = "pending_create"
const val SYNC_PENDING_UPDATE = "pending_update"
const val SYNC_PENDING_DELETE = "pending_delete"
const val ENTITY_PROJECT = "project"
const val ENTITY_TASK = "task"
const val ENTITY_SUBTASK = "subtask"
const val ENTITY_GENERATION = "generation"
const val MUTATION_CREATE = "create"
const val MUTATION_UPDATE = "update"
const val MUTATION_DELETE = "delete"
const val MUTATION_GENERATE = "generate"

data class TaskCounts(
    val total: Int = 0,
    val open: Int = 0,
    val todo: Int = 0,
    val inProgress: Int = 0,
    val done: Int = 0,
    val archived: Int = 0,
) {
    companion object {
        fun fromJson(json: JSONObject?): TaskCounts {
            if (json == null) return TaskCounts()
            return TaskCounts(
                total = json.optInt("total"),
                open = json.optInt("open"),
                todo = json.optInt("todo"),
                inProgress = json.optInt("in_progress"),
                done = json.optInt("done"),
                archived = json.optInt("archived"),
            )
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("total", total)
        .put("open", open)
        .put("todo", todo)
        .put("in_progress", inProgress)
        .put("done", done)
        .put("archived", archived)
}

data class TemplateAction(
    val templateKey: String,
    val name: String,
    val generateEndpoint: String,
) {
    companion object {
        fun fromJson(json: JSONObject): TemplateAction = TemplateAction(
            templateKey = json.optString("template_key"),
            name = json.optString("name"),
            generateEndpoint = json.optString("generate_endpoint"),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("template_key", templateKey)
        .put("name", name)
        .put("generate_endpoint", generateEndpoint)
}

data class Project(
    val id: String,
    val serverId: String?,
    val name: String,
    val description: String,
    val tags: List<String>,
    val isCore: Boolean,
    val coreKey: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
    val taskCounts: TaskCounts = TaskCounts(),
    val templateActions: List<TemplateAction> = emptyList(),
    val syncStatus: String = SYNC_SYNCED,
) {
    val isPendingDelete: Boolean get() = syncStatus == SYNC_PENDING_DELETE
    val displaySyncStatus: String? get() = syncStatus.takeIf { it != SYNC_SYNCED }

    companion object {
        fun fromJson(json: JSONObject): Project = Project(
            id = json.optString("id"),
            serverId = json.optServerId(),
            name = json.optString("name"),
            description = json.optString("description"),
            tags = json.optStringArray("tags"),
            isCore = json.optBoolean("is_core"),
            coreKey = json.optNullableString("core_key"),
            createdAt = json.optString("created_at"),
            updatedAt = json.optString("updated_at"),
            deletedAt = json.optNullableString("deleted_at"),
            taskCounts = TaskCounts.fromJson(json.optJSONObject("task_counts")),
            templateActions = json.optObjectArray("template_actions").map(TemplateAction::fromJson),
            syncStatus = json.optString("sync_status", SYNC_SYNCED),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .putNullable("server_id", serverId)
        .put("name", name)
        .put("description", description)
        .put("tags", tags.toJsonArray())
        .put("is_core", isCore)
        .putNullable("core_key", coreKey)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .putNullable("deleted_at", deletedAt)
        .put("task_counts", taskCounts.toJson())
        .put("template_actions", templateActions.map { it.toJson() }.toJsonArray())
        .put("sync_status", syncStatus)
}

data class Task(
    val id: String,
    val serverId: String?,
    val projectId: String,
    val name: String,
    val description: String,
    val status: String,
    val createdAt: String,
    val finishedAt: String?,
    val updatedAt: String,
    val deletedAt: String?,
    val syncStatus: String = SYNC_SYNCED,
) {
    val isDone: Boolean get() = status == STATUS_DONE
    val isPendingDelete: Boolean get() = syncStatus == SYNC_PENDING_DELETE
    val displaySyncStatus: String? get() = syncStatus.takeIf { it != SYNC_SYNCED }

    companion object {
        fun fromJson(json: JSONObject): Task = Task(
            id = json.optString("id"),
            serverId = json.optServerId(),
            projectId = json.optString("project_id"),
            name = json.optString("name"),
            description = json.optString("description"),
            status = json.optString("status", STATUS_TODO),
            createdAt = json.optString("created_at"),
            finishedAt = json.optNullableString("finished_at"),
            updatedAt = json.optString("updated_at"),
            deletedAt = json.optNullableString("deleted_at"),
            syncStatus = json.optString("sync_status", SYNC_SYNCED),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .putNullable("server_id", serverId)
        .put("project_id", projectId)
        .put("name", name)
        .put("description", description)
        .put("status", status)
        .put("created_at", createdAt)
        .putNullable("finished_at", finishedAt)
        .put("updated_at", updatedAt)
        .putNullable("deleted_at", deletedAt)
        .put("sync_status", syncStatus)
}

data class SubTask(
    val id: String,
    val serverId: String?,
    val taskId: String,
    val name: String,
    val description: String,
    val status: String,
    val createdAt: String,
    val finishedAt: String?,
    val updatedAt: String,
    val deletedAt: String?,
    val syncStatus: String = SYNC_SYNCED,
) {
    val isDone: Boolean get() = status == STATUS_DONE
    val isPendingDelete: Boolean get() = syncStatus == SYNC_PENDING_DELETE
    val displaySyncStatus: String? get() = syncStatus.takeIf { it != SYNC_SYNCED }

    companion object {
        fun fromJson(json: JSONObject): SubTask = SubTask(
            id = json.optString("id"),
            serverId = json.optServerId(),
            taskId = json.optString("task_id"),
            name = json.optString("name"),
            description = json.optString("description"),
            status = json.optString("status", STATUS_TODO),
            createdAt = json.optString("created_at"),
            finishedAt = json.optNullableString("finished_at"),
            updatedAt = json.optString("updated_at"),
            deletedAt = json.optNullableString("deleted_at"),
            syncStatus = json.optString("sync_status", SYNC_SYNCED),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .putNullable("server_id", serverId)
        .put("task_id", taskId)
        .put("name", name)
        .put("description", description)
        .put("status", status)
        .put("created_at", createdAt)
        .putNullable("finished_at", finishedAt)
        .put("updated_at", updatedAt)
        .putNullable("deleted_at", deletedAt)
        .put("sync_status", syncStatus)
}

data class PendingMutation(
    val id: String,
    val entityType: String,
    val operation: String,
    val localId: String,
    val createdAt: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val payload: String = "",
)

data class SyncResult(
    val pendingBefore: Int,
    val synced: Int,
    val failed: Int,
) {
    val message: String
        get() = when {
            pendingBefore == 0 -> "No pending changes."
            failed == 0 -> "Synced $synced pending changes."
            else -> "Synced $synced changes; $failed still pending."
        }
}

data class Template(
    val templateKey: String,
    val name: String,
    val projectCoreKey: String?,
    val description: String = "",
    val items: List<TemplateItem> = emptyList(),
) {
    companion object {
        fun fromJson(json: JSONObject): Template = Template(
            templateKey = json.optString("template_key"),
            name = json.optString("name"),
            projectCoreKey = json.optNullableString("project_core_key"),
            description = json.optString("description"),
            items = json.optObjectArray("items").map(TemplateItem::fromJson),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("template_key", templateKey)
        .put("name", name)
        .putNullable("project_core_key", projectCoreKey)
        .put("description", description)
        .put("items", items.map { it.toJson() }.toJsonArray())
}

data class TemplateItem(
    val id: String?,
    val name: String,
    val description: String,
    val position: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): TemplateItem = TemplateItem(
            id = json.optNullableString("id"),
            name = json.optString("name"),
            description = json.optString("description"),
            position = json.optInt("position"),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .putNullable("id", id)
        .put("name", name)
        .put("description", description)
        .put("position", position)
}

data class GeneratedTask(
    val taskId: String,
    val projectId: String,
    val name: String,
    val subtasksCreated: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): GeneratedTask = GeneratedTask(
            taskId = json.optString("task_id"),
            projectId = json.optString("project_id"),
            name = json.optString("name"),
            subtasksCreated = json.optInt("subtasks_created"),
        )
    }
}

fun toggledStatus(current: String): String = if (current == STATUS_DONE) STATUS_TODO else STATUS_DONE

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONObject.optServerId(): String? {
    if (has("server_id")) {
        return optNullableString("server_id")
    }
    return optString("id").takeIf { it.isNotBlank() }
}

private fun JSONObject.optStringArray(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optString(index) }
}

private fun JSONObject.optObjectArray(name: String): List<JSONObject> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optJSONObject(index) ?: JSONObject() }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
    return this
}

fun List<Any>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

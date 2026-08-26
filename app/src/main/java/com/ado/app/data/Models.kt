package com.ado.app.data

import org.json.JSONArray
import org.json.JSONObject

const val STATUS_TODO = "todo"
const val STATUS_DONE = "done"
const val LIST_TYPE_NORMAL = "normal"
const val LIST_TYPE_DAILY = "daily"
const val LIST_TYPE_MARKET = "market"
const val LIST_TYPE_CHECKLIST = "checklist"
const val LIST_TYPE_CUSTOM = "custom"
val LIST_TYPES = listOf(
    LIST_TYPE_NORMAL,
    LIST_TYPE_DAILY,
    LIST_TYPE_MARKET,
    LIST_TYPE_CHECKLIST,
    LIST_TYPE_CUSTOM,
)

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

data class Project(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val isCore: Boolean,
    val coreKey: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
    val taskCounts: TaskCounts = TaskCounts(),
    val listType: String = LIST_TYPE_NORMAL,
) {
    companion object {
        fun fromJson(json: JSONObject): Project = Project(
            id = json.optString("id"),
            name = json.optString("name"),
            description = json.optString("description"),
            tags = json.optStringArray("tags"),
            isCore = json.optBoolean("is_core"),
            coreKey = json.optNullableString("core_key"),
            createdAt = json.optString("created_at"),
            updatedAt = json.optString("updated_at"),
            deletedAt = json.optNullableString("deleted_at"),
            taskCounts = TaskCounts.fromJson(json.optJSONObject("task_counts")),
            listType = json.optString("list_type", LIST_TYPE_NORMAL).ifBlank { LIST_TYPE_NORMAL },
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("tags", tags.toJsonArray())
        .put("is_core", isCore)
        .putNullable("core_key", coreKey)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .putNullable("deleted_at", deletedAt)
        .put("task_counts", taskCounts.toJson())
        .put("list_type", listType)
}

data class Task(
    val id: String,
    val projectId: String,
    val name: String,
    val description: String,
    val status: String,
    val createdAt: String,
    val finishedAt: String?,
    val updatedAt: String,
    val deletedAt: String?,
    val listType: String = LIST_TYPE_NORMAL,
    val position: Int = -1,
) {
    val isDone: Boolean get() = status == STATUS_DONE

    companion object {
        fun fromJson(json: JSONObject): Task = Task(
            id = json.optString("id"),
            projectId = json.optString("project_id"),
            name = json.optString("name"),
            description = json.optString("description"),
            status = json.optString("status", STATUS_TODO),
            createdAt = json.optString("created_at"),
            finishedAt = json.optNullableString("finished_at"),
            updatedAt = json.optString("updated_at"),
            deletedAt = json.optNullableString("deleted_at"),
            listType = json.optString("list_type", LIST_TYPE_NORMAL).ifBlank { LIST_TYPE_NORMAL },
            position = json.optPosition(),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("project_id", projectId)
        .put("name", name)
        .put("description", description)
        .put("status", status)
        .put("created_at", createdAt)
        .putNullable("finished_at", finishedAt)
        .put("updated_at", updatedAt)
        .putNullable("deleted_at", deletedAt)
        .put("list_type", listType)
        .put("position", position)
}

data class SubTask(
    val id: String,
    val taskId: String,
    val name: String,
    val description: String,
    val status: String,
    val createdAt: String,
    val finishedAt: String?,
    val updatedAt: String,
    val deletedAt: String?,
    val position: Int = -1,
) {
    val isDone: Boolean get() = status == STATUS_DONE

    companion object {
        fun fromJson(json: JSONObject): SubTask = SubTask(
            id = json.optString("id"),
            taskId = json.optString("task_id"),
            name = json.optString("name"),
            description = json.optString("description"),
            status = json.optString("status", STATUS_TODO),
            createdAt = json.optString("created_at"),
            finishedAt = json.optNullableString("finished_at"),
            updatedAt = json.optString("updated_at"),
            deletedAt = json.optNullableString("deleted_at"),
            position = json.optPosition(),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("task_id", taskId)
        .put("name", name)
        .put("description", description)
        .put("status", status)
        .put("created_at", createdAt)
        .putNullable("finished_at", finishedAt)
        .put("updated_at", updatedAt)
        .putNullable("deleted_at", deletedAt)
        .put("position", position)
}

data class Template(
    val templateKey: String,
    val name: String,
    val projectCoreKey: String?,
    val description: String = "",
    val listType: String? = null,
    val items: List<TemplateItem> = emptyList(),
) {
    companion object {
        fun fromJson(json: JSONObject): Template = Template(
            templateKey = json.optString("template_key"),
            name = json.optString("name"),
            projectCoreKey = json.optNullableString("project_core_key"),
            description = json.optString("description"),
            listType = json.optNullableString("list_type"),
            items = json.optObjectArray("items").map(TemplateItem::fromJson),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("template_key", templateKey)
        .put("name", name)
        .putNullable("project_core_key", projectCoreKey)
        .put("description", description)
        .putNullable("list_type", listType)
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

fun toggledStatus(current: String): String = if (current == STATUS_DONE) STATUS_TODO else STATUS_DONE

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
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

private fun JSONObject.optPosition(): Int = if (has("position") && !isNull("position")) optInt("position", -1) else -1

fun List<Any>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

package com.ado.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AdoApiException(
    val statusCode: Int?,
    val errorCode: String?,
    override val message: String,
) : Exception(message)

class ApiClient(
    private val settingsStore: SettingsStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getProjects(): List<Project> {
        val array = jsonArrayOrEmpty(request("GET", "/api/v1/projects"))
        return List(array.length()) { Project.fromJson(array.getJSONObject(it)) }
    }

    suspend fun createProject(name: String, description: String, tags: List<String>): Project {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("tags", tags.toJsonArray())
        return Project.fromJson(JSONObject(request("POST", "/api/v1/projects", body)))
    }

    suspend fun getProject(projectId: String): Project =
        Project.fromJson(JSONObject(request("GET", "/api/v1/projects/${projectId.urlPart()}")))

    suspend fun updateProject(projectId: String, name: String, description: String, tags: List<String>): Project {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("tags", tags.toJsonArray())
        return Project.fromJson(JSONObject(request("PATCH", "/api/v1/projects/${projectId.urlPart()}", body)))
    }

    suspend fun deleteProject(projectId: String) {
        request("DELETE", "/api/v1/projects/${projectId.urlPart()}")
    }

    suspend fun getTasks(projectId: String): List<Task> {
        val array = jsonArrayOrEmpty(request("GET", "/api/v1/projects/${projectId.urlPart()}/tasks"))
        return List(array.length()) { Task.fromJson(array.getJSONObject(it)) }
    }

    suspend fun createTask(projectId: String, name: String, description: String): Task {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
        return Task.fromJson(JSONObject(request("POST", "/api/v1/projects/${projectId.urlPart()}/tasks", body)))
    }

    suspend fun getTask(taskId: String): Task =
        Task.fromJson(JSONObject(request("GET", "/api/v1/tasks/${taskId.urlPart()}")))

    suspend fun updateTask(taskId: String, name: String, description: String, projectId: String? = null): Task {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
        if (projectId != null) {
            body.put("project_id", projectId)
        }
        return Task.fromJson(JSONObject(request("PATCH", "/api/v1/tasks/${taskId.urlPart()}", body)))
    }

    suspend fun deleteTask(taskId: String) {
        request("DELETE", "/api/v1/tasks/${taskId.urlPart()}")
    }

    suspend fun getSubTasks(taskId: String): List<SubTask> {
        val array = jsonArrayOrEmpty(request("GET", "/api/v1/tasks/${taskId.urlPart()}/subtasks"))
        return List(array.length()) { SubTask.fromJson(array.getJSONObject(it)) }
    }

    suspend fun getSubTask(subTaskId: String): SubTask =
        SubTask.fromJson(JSONObject(request("GET", "/api/v1/subtasks/${subTaskId.urlPart()}")))

    suspend fun createSubTask(taskId: String, name: String, description: String): SubTask {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
        return SubTask.fromJson(JSONObject(request("POST", "/api/v1/tasks/${taskId.urlPart()}/subtasks", body)))
    }

    suspend fun deleteSubTask(subTaskId: String) {
        request("DELETE", "/api/v1/subtasks/${subTaskId.urlPart()}")
    }

    suspend fun updateSubTask(subTaskId: String, name: String, description: String, taskId: String? = null): SubTask {
        val body = JSONObject()
            .put("name", name)
            .put("description", description)
        if (taskId != null) {
            body.put("task_id", taskId)
        }
        return SubTask.fromJson(JSONObject(request("PATCH", "/api/v1/subtasks/${subTaskId.urlPart()}", body)))
    }

    suspend fun patchTaskStatus(taskId: String, status: String): Task {
        val body = JSONObject().put("status", status)
        return Task.fromJson(JSONObject(request("PATCH", "/api/v1/tasks/${taskId.urlPart()}", body)))
    }

    suspend fun patchSubTaskStatus(subTaskId: String, status: String): SubTask {
        val body = JSONObject().put("status", status)
        return SubTask.fromJson(JSONObject(request("PATCH", "/api/v1/subtasks/${subTaskId.urlPart()}", body)))
    }

    suspend fun generateDaily(date: String): GeneratedTask {
        val body = JSONObject().put("date", date)
        return GeneratedTask.fromJson(JSONObject(request("POST", "/api/v1/templates/daily/generate", body)))
    }

    suspend fun generateSeasonal(templateKey: String, year: Int): GeneratedTask {
        val body = JSONObject().put("year", year)
        return GeneratedTask.fromJson(JSONObject(request("POST", "/api/v1/templates/${templateKey.urlPart()}/generate", body)))
    }

    suspend fun getTemplates(): List<Template> {
        val array = jsonArrayOrEmpty(request("GET", "/api/v1/templates"))
        return List(array.length()) { Template.fromJson(array.getJSONObject(it)) }
    }

    suspend fun getTemplate(templateKey: String): Template =
        Template.fromJson(JSONObject(request("GET", "/api/v1/templates/${templateKey.urlPart()}")))

    suspend fun updateTemplateItems(templateKey: String, items: List<TemplateItem>): Template {
        val normalized = items.mapIndexed { index, item ->
            item.copy(position = index)
        }
        val body = JSONObject().put("items", normalized.map { it.toJson() }.toJsonArray())
        return Template.fromJson(JSONObject(request("PATCH", "/api/v1/templates/${templateKey.urlPart()}", body)))
    }

    suspend fun testConnection(): Boolean {
        request("GET", "/healthz")
        return true
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val requestBody = body?.toString()?.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw parseApiError(response.code, responseBody)
                }
                responseBody
            }
        } catch (e: AdoApiException) {
            throw e
        } catch (e: IOException) {
            throw AdoApiException(null, "network_error", e.message ?: "Server unavailable")
        } catch (e: Exception) {
            throw AdoApiException(null, "invalid_response", e.message ?: "Invalid server response")
        }
    }

    private suspend fun buildUrl(path: String): String {
        val base = normalizeServerUrl(settingsStore.serverUrlFlow.first())
        return base.trimEnd('/') + path
    }

    private fun parseApiError(statusCode: Int, body: String): AdoApiException {
        return try {
            val error = JSONObject(body).optJSONObject("error")
            AdoApiException(
                statusCode = statusCode,
                errorCode = error?.optString("code"),
                message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Request failed",
            )
        } catch (_: Exception) {
            AdoApiException(statusCode, null, "Request failed with HTTP $statusCode")
        }
    }
}

private fun jsonArrayOrEmpty(raw: String): JSONArray {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed == "null") {
        return JSONArray()
    }
    return JSONArray(trimmed)
}

private fun String.urlPart(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

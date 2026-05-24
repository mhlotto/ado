package com.ado.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface LocalStore {
    suspend fun getProjects(): List<Project>
    suspend fun saveProjects(projects: List<Project>)
    suspend fun getProject(projectId: String): Project?
    suspend fun getProjectByServerId(serverId: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(projectId: String)
    suspend fun getTasks(projectId: String): List<Task>
    suspend fun saveTasks(projectId: String, tasks: List<Task>)
    suspend fun getTask(taskId: String): Task?
    suspend fun getTaskByServerId(serverId: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun deleteTask(task: Task)
    suspend fun getSubTasks(taskId: String): List<SubTask>
    suspend fun saveSubTasks(taskId: String, subtasks: List<SubTask>)
    suspend fun getSubTask(subTaskId: String): SubTask?
    suspend fun getSubTaskByServerId(serverId: String): SubTask?
    suspend fun saveSubTask(subTask: SubTask)
    suspend fun deleteSubTask(subTask: SubTask)
    suspend fun getTemplates(): List<Template>
    suspend fun saveTemplates(templates: List<Template>)
    suspend fun getTemplate(templateKey: String): Template?
    suspend fun saveTemplate(template: Template)
    suspend fun getPendingMutations(): List<PendingMutation>
    suspend fun savePendingMutation(mutation: PendingMutation)
    suspend fun deletePendingMutation(mutationId: String)
    suspend fun deletePendingMutationsForLocalId(localId: String)
    suspend fun deletePendingMutationsForPayload(payload: String)
    suspend fun updatePendingMutationFailure(mutationId: String, attempts: Int, lastError: String?)
    suspend fun pendingMutationCount(): Int
}

class JsonFileLocalStore(context: Context) : LocalStore {
    private val cacheDir = File(context.filesDir, "ado-cache")

    override suspend fun getProjects(): List<Project> = withContext(Dispatchers.IO) {
        readArray(projectsFile()).map(Project::fromJson)
    }

    override suspend fun saveProjects(projects: List<Project>) = withContext(Dispatchers.IO) {
        writeArray(projectsFile(), projects.map { it.toJson() })
        projects.forEach { saveProjectInternal(it) }
    }

    override suspend fun getProject(projectId: String): Project? = withContext(Dispatchers.IO) {
        readObject(projectFile(projectId))?.let(Project::fromJson)
    }

    override suspend fun getProjectByServerId(serverId: String): Project? = withContext(Dispatchers.IO) {
        getProjectsInternal().firstOrNull { it.serverId == serverId }
    }

    override suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
        saveProjectInternal(project)
        val projects = getProjectsInternal().filterNot { it.id == project.id } + project
        writeArray(projectsFile(), projects.map { it.toJson() })
    }

    override suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        projectFile(projectId).delete()
        tasksFile(projectId).delete()
        val projects = getProjectsInternal().filterNot { it.id == projectId }
        writeArray(projectsFile(), projects.map { it.toJson() })
    }

    override suspend fun getTasks(projectId: String): List<Task> = withContext(Dispatchers.IO) {
        readArray(tasksFile(projectId)).map(Task::fromJson)
    }

    override suspend fun saveTasks(projectId: String, tasks: List<Task>) = withContext(Dispatchers.IO) {
        writeArray(tasksFile(projectId), tasks.map { it.toJson() })
        tasks.forEach { writeObject(taskFile(it.id), it.toJson()) }
    }

    override suspend fun getTask(taskId: String): Task? = withContext(Dispatchers.IO) {
        readObject(taskFile(taskId))?.let(Task::fromJson)
    }

    override suspend fun getTaskByServerId(serverId: String): Task? = withContext(Dispatchers.IO) {
        readArray(File(cacheDir, "tasks")).map(Task::fromJson).firstOrNull { it.serverId == serverId }
    }

    override suspend fun saveTask(task: Task) = withContext(Dispatchers.IO) {
        val previous = readObject(taskFile(task.id))?.let(Task::fromJson)
        writeObject(taskFile(task.id), task.toJson())
        if (previous != null && previous.projectId != task.projectId) {
            val previousTasks = getTasksInternal(previous.projectId).filterNot { it.id == task.id }
            writeArray(tasksFile(previous.projectId), previousTasks.map { it.toJson() })
        }
        val tasks = getTasksInternal(task.projectId).filterNot { it.id == task.id } + task
        writeArray(tasksFile(task.projectId), tasks.map { it.toJson() })
    }

    override suspend fun deleteTask(task: Task) = withContext(Dispatchers.IO) {
        taskFile(task.id).delete()
        subTasksFile(task.id).delete()
        val tasks = getTasksInternal(task.projectId).filterNot { it.id == task.id }
        writeArray(tasksFile(task.projectId), tasks.map { it.toJson() })
    }

    override suspend fun getSubTasks(taskId: String): List<SubTask> = withContext(Dispatchers.IO) {
        readArray(subTasksFile(taskId)).map(SubTask::fromJson)
    }

    override suspend fun getSubTask(subTaskId: String): SubTask? = withContext(Dispatchers.IO) {
        readObject(subTaskFile(subTaskId))?.let(SubTask::fromJson)
    }

    override suspend fun getSubTaskByServerId(serverId: String): SubTask? = withContext(Dispatchers.IO) {
        readArray(File(cacheDir, "subtasks")).map(SubTask::fromJson).firstOrNull { it.serverId == serverId }
    }

    override suspend fun saveSubTasks(taskId: String, subtasks: List<SubTask>) = withContext(Dispatchers.IO) {
        writeArray(subTasksFile(taskId), subtasks.map { it.toJson() })
        subtasks.forEach { writeObject(subTaskFile(it.id), it.toJson()) }
    }

    override suspend fun saveSubTask(subTask: SubTask) = withContext(Dispatchers.IO) {
        val previous = readObject(subTaskFile(subTask.id))?.let(SubTask::fromJson)
        writeObject(subTaskFile(subTask.id), subTask.toJson())
        if (previous != null && previous.taskId != subTask.taskId) {
            val previousSubTasks = getSubTasksInternal(previous.taskId).filterNot { it.id == subTask.id }
            writeArray(subTasksFile(previous.taskId), previousSubTasks.map { it.toJson() })
        }
        val subtasks = getSubTasksInternal(subTask.taskId).filterNot { it.id == subTask.id } + subTask
        writeArray(subTasksFile(subTask.taskId), subtasks.map { it.toJson() })
    }

    override suspend fun deleteSubTask(subTask: SubTask) = withContext(Dispatchers.IO) {
        subTaskFile(subTask.id).delete()
        val subtasks = getSubTasksInternal(subTask.taskId).filterNot { it.id == subTask.id }
        writeArray(subTasksFile(subTask.taskId), subtasks.map { it.toJson() })
    }

    override suspend fun getTemplates(): List<Template> = withContext(Dispatchers.IO) {
        readArray(templatesFile()).map(Template::fromJson)
    }

    override suspend fun saveTemplates(templates: List<Template>) = withContext(Dispatchers.IO) {
        writeArray(templatesFile(), templates.map { it.toJson() })
        templates.forEach { writeObject(templateFile(it.templateKey), it.toJson()) }
    }

    override suspend fun getTemplate(templateKey: String): Template? = withContext(Dispatchers.IO) {
        readObject(templateFile(templateKey))?.let(Template::fromJson)
    }

    override suspend fun saveTemplate(template: Template) = withContext(Dispatchers.IO) {
        writeObject(templateFile(template.templateKey), template.toJson())
        val templates = getTemplatesInternal().filterNot { it.templateKey == template.templateKey } + template
        writeArray(templatesFile(), templates.map { it.toJson() })
    }

    override suspend fun getPendingMutations(): List<PendingMutation> = emptyList()

    override suspend fun savePendingMutation(mutation: PendingMutation) = Unit

    override suspend fun deletePendingMutation(mutationId: String) = Unit

    override suspend fun deletePendingMutationsForLocalId(localId: String) = Unit

    override suspend fun deletePendingMutationsForPayload(payload: String) = Unit

    override suspend fun updatePendingMutationFailure(mutationId: String, attempts: Int, lastError: String?) = Unit

    override suspend fun pendingMutationCount(): Int = 0

    private fun saveProjectInternal(project: Project) {
        writeObject(projectFile(project.id), project.toJson())
    }

    private fun getProjectsInternal(): List<Project> = readArray(projectsFile()).map(Project::fromJson)

    private fun getTasksInternal(projectId: String): List<Task> = readArray(tasksFile(projectId)).map(Task::fromJson)

    private fun getSubTasksInternal(taskId: String): List<SubTask> = readArray(subTasksFile(taskId)).map(SubTask::fromJson)

    private fun getTemplatesInternal(): List<Template> = readArray(templatesFile()).map(Template::fromJson)

    private fun projectsFile() = File(cacheDir, "projects.json")
    private fun projectFile(projectId: String) = File(File(cacheDir, "projects"), "$projectId.json")
    private fun tasksFile(projectId: String) = File(File(cacheDir, "tasks_by_project"), "$projectId.json")
    private fun taskFile(taskId: String) = File(File(cacheDir, "tasks"), "$taskId.json")
    private fun subTasksFile(taskId: String) = File(File(cacheDir, "subtasks_by_task"), "$taskId.json")
    private fun subTaskFile(subTaskId: String) = File(File(cacheDir, "subtasks"), "$subTaskId.json")
    private fun templatesFile() = File(cacheDir, "templates.json")
    private fun templateFile(templateKey: String) = File(File(cacheDir, "templates"), "$templateKey.json")

    private fun readArray(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun readObject(file: File): JSONObject? {
        if (!file.exists()) return null
        return JSONObject(file.readText())
    }

    private fun writeArray(file: File, objects: List<JSONObject>) {
        val array = JSONArray()
        objects.forEach { array.put(it) }
        writeText(file, array.toString())
    }

    private fun writeObject(file: File, json: JSONObject) {
        writeText(file, json.toString())
    }

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}

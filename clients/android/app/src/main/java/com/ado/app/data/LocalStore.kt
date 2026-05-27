package com.ado.app.data

interface LocalStore {
    suspend fun getProjects(): List<Project>
    suspend fun getProject(projectId: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(projectId: String)
    suspend fun getTasks(projectId: String): List<Task>
    suspend fun getTask(taskId: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun deleteTask(task: Task)
    suspend fun getSubTasks(taskId: String): List<SubTask>
    suspend fun getSubTask(subTaskId: String): SubTask?
    suspend fun saveSubTask(subTask: SubTask)
    suspend fun deleteSubTask(subTask: SubTask)
    suspend fun getTemplates(): List<Template>
    suspend fun getTemplate(templateKey: String): Template?
    suspend fun saveTemplate(template: Template)
}

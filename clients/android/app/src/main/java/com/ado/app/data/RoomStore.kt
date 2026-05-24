package com.ado.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val syncStatus: String,
    val json: String,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val projectId: String,
    val syncStatus: String,
    val json: String,
)

@Entity(tableName = "subtasks")
data class SubTaskEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val taskId: String,
    val syncStatus: String,
    val json: String,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val templateKey: String,
    val json: String,
)

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val operation: String,
    val localId: String,
    val createdAt: String,
    val attempts: Int,
    val lastError: String?,
    val payload: String,
)

@Dao
interface AdoDao {
    @Query("SELECT json FROM projects WHERE syncStatus != :pendingDelete ORDER BY json")
    suspend fun getProjects(pendingDelete: String): List<String>

    @Query("SELECT json FROM projects WHERE id = :id")
    suspend fun getProject(id: String): String?

    @Query("SELECT json FROM projects WHERE serverId = :serverId LIMIT 1")
    suspend fun getProjectByServerId(serverId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Query("SELECT json FROM tasks WHERE projectId = :projectId AND syncStatus != :pendingDelete ORDER BY json")
    suspend fun getTasks(projectId: String, pendingDelete: String): List<String>

    @Query("SELECT json FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): String?

    @Query("SELECT json FROM tasks WHERE serverId = :serverId LIMIT 1")
    suspend fun getTaskByServerId(serverId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun deleteTasksForProject(projectId: String)

    @Query("SELECT json FROM subtasks WHERE taskId = :taskId AND syncStatus != :pendingDelete ORDER BY json")
    suspend fun getSubTasks(taskId: String, pendingDelete: String): List<String>

    @Query("SELECT json FROM subtasks WHERE id = :id")
    suspend fun getSubTask(id: String): String?

    @Query("SELECT json FROM subtasks WHERE serverId = :serverId LIMIT 1")
    suspend fun getSubTaskByServerId(serverId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubTasks(subtasks: List<SubTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubTask(subtask: SubTaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubTask(id: String)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksForTask(taskId: String)

    @Query("SELECT json FROM templates ORDER BY templateKey")
    suspend fun getTemplates(): List<String>

    @Query("SELECT json FROM templates WHERE templateKey = :templateKey")
    suspend fun getTemplate(templateKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: TemplateEntity)

    @Query("SELECT * FROM pending_mutations ORDER BY createdAt")
    suspend fun getPendingMutations(): List<PendingMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingMutation(mutation: PendingMutationEntity)

    @Query("DELETE FROM pending_mutations WHERE id = :id")
    suspend fun deletePendingMutation(id: String)

    @Query("DELETE FROM pending_mutations WHERE localId = :localId")
    suspend fun deletePendingMutationsForLocalId(localId: String)

    @Query("DELETE FROM pending_mutations WHERE payload = :payload")
    suspend fun deletePendingMutationsForPayload(payload: String)

    @Query("UPDATE pending_mutations SET attempts = :attempts, lastError = :lastError WHERE id = :id")
    suspend fun updatePendingMutationFailure(id: String, attempts: Int, lastError: String?)

    @Query("SELECT COUNT(*) FROM pending_mutations")
    suspend fun pendingMutationCount(): Int
}

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        SubTaskEntity::class,
        TemplateEntity::class,
        PendingMutationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AdoDatabase : RoomDatabase() {
    abstract fun adoDao(): AdoDao

    companion object {
        @Volatile private var instance: AdoDatabase? = null

        fun get(context: Context): AdoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdoDatabase::class.java,
                    "ado.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE projects ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'synced'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'synced'")
                db.execSQL("ALTER TABLE subtasks ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE subtasks ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'synced'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_mutations (
                        id TEXT NOT NULL PRIMARY KEY,
                        entityType TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        localId TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        lastError TEXT,
                        payload TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_mutations ADD COLUMN payload TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

class RoomLocalStore(context: Context) : LocalStore {
    private val dao = AdoDatabase.get(context).adoDao()

    override suspend fun getProjects(): List<Project> =
        dao.getProjects(SYNC_PENDING_DELETE).map { Project.fromJson(JSONObject(it)) }

    override suspend fun saveProjects(projects: List<Project>) {
        dao.upsertProjects(projects.map(::projectEntity))
    }

    override suspend fun getProject(projectId: String): Project? =
        dao.getProject(projectId)?.let { Project.fromJson(JSONObject(it)) }

    override suspend fun getProjectByServerId(serverId: String): Project? =
        dao.getProjectByServerId(serverId)?.let { Project.fromJson(JSONObject(it)) }

    override suspend fun saveProject(project: Project) {
        dao.upsertProject(projectEntity(project))
    }

    override suspend fun deleteProject(projectId: String) {
        dao.deleteProject(projectId)
        dao.deleteTasksForProject(projectId)
    }

    override suspend fun getTasks(projectId: String): List<Task> =
        dao.getTasks(projectId, SYNC_PENDING_DELETE).map { Task.fromJson(JSONObject(it)) }

    override suspend fun saveTasks(projectId: String, tasks: List<Task>) {
        dao.upsertTasks(tasks.map(::taskEntity))
    }

    override suspend fun getTask(taskId: String): Task? =
        dao.getTask(taskId)?.let { Task.fromJson(JSONObject(it)) }

    override suspend fun getTaskByServerId(serverId: String): Task? =
        dao.getTaskByServerId(serverId)?.let { Task.fromJson(JSONObject(it)) }

    override suspend fun saveTask(task: Task) {
        dao.upsertTask(taskEntity(task))
    }

    override suspend fun deleteTask(task: Task) {
        dao.deleteTask(task.id)
        dao.deleteSubTasksForTask(task.id)
    }

    override suspend fun getSubTasks(taskId: String): List<SubTask> =
        dao.getSubTasks(taskId, SYNC_PENDING_DELETE).map { SubTask.fromJson(JSONObject(it)) }

    override suspend fun saveSubTasks(taskId: String, subtasks: List<SubTask>) {
        dao.upsertSubTasks(subtasks.map(::subTaskEntity))
    }

    override suspend fun getSubTask(subTaskId: String): SubTask? {
        return dao.getSubTask(subTaskId)?.let { SubTask.fromJson(JSONObject(it)) }
    }

    override suspend fun getSubTaskByServerId(serverId: String): SubTask? {
        return dao.getSubTaskByServerId(serverId)?.let { SubTask.fromJson(JSONObject(it)) }
    }

    override suspend fun saveSubTask(subTask: SubTask) {
        dao.upsertSubTask(subTaskEntity(subTask))
    }

    override suspend fun deleteSubTask(subTask: SubTask) {
        dao.deleteSubTask(subTask.id)
    }

    override suspend fun getTemplates(): List<Template> =
        dao.getTemplates().map { Template.fromJson(JSONObject(it)) }

    override suspend fun saveTemplates(templates: List<Template>) {
        dao.upsertTemplates(templates.map { TemplateEntity(it.templateKey, it.toJson().toString()) })
    }

    override suspend fun getTemplate(templateKey: String): Template? =
        dao.getTemplate(templateKey)?.let { Template.fromJson(JSONObject(it)) }

    override suspend fun saveTemplate(template: Template) {
        dao.upsertTemplate(TemplateEntity(template.templateKey, template.toJson().toString()))
    }

    override suspend fun getPendingMutations(): List<PendingMutation> =
        dao.getPendingMutations().map { it.toPendingMutation() }

    override suspend fun savePendingMutation(mutation: PendingMutation) {
        dao.upsertPendingMutation(mutation.toEntity())
    }

    override suspend fun deletePendingMutation(mutationId: String) {
        dao.deletePendingMutation(mutationId)
    }

    override suspend fun deletePendingMutationsForLocalId(localId: String) {
        dao.deletePendingMutationsForLocalId(localId)
    }

    override suspend fun deletePendingMutationsForPayload(payload: String) {
        dao.deletePendingMutationsForPayload(payload)
    }

    override suspend fun updatePendingMutationFailure(mutationId: String, attempts: Int, lastError: String?) {
        dao.updatePendingMutationFailure(mutationId, attempts, lastError)
    }

    override suspend fun pendingMutationCount(): Int = dao.pendingMutationCount()

    private fun projectEntity(project: Project) =
        ProjectEntity(project.id, project.serverId, project.syncStatus, project.toJson().toString())

    private fun taskEntity(task: Task) =
        TaskEntity(task.id, task.serverId, task.projectId, task.syncStatus, task.toJson().toString())

    private fun subTaskEntity(subTask: SubTask) =
        SubTaskEntity(subTask.id, subTask.serverId, subTask.taskId, subTask.syncStatus, subTask.toJson().toString())
}

private fun PendingMutationEntity.toPendingMutation(): PendingMutation =
    PendingMutation(id, entityType, operation, localId, createdAt, attempts, lastError, payload)

private fun PendingMutation.toEntity(): PendingMutationEntity =
    PendingMutationEntity(id, entityType, operation, localId, createdAt, attempts, lastError, payload)

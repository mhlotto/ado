package com.ado.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
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
    val json: String,
)

@Entity(tableName = "tasks", indices = [Index(value = ["projectId"])])
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    @ColumnInfo(defaultValue = "'todo'") val status: String,
    val json: String,
)

data class ProjectTaskCountsRow(
    val projectId: String,
    val totalCount: Int,
    val openCount: Int,
    val todoCount: Int,
    val inProgressCount: Int,
    val doneCount: Int,
    val archivedCount: Int,
)

@Entity(tableName = "subtasks", indices = [Index(value = ["taskId"])])
data class SubTaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val json: String,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val templateKey: String,
    val json: String,
)

@Dao
interface AdoDao {
    @Query("SELECT json FROM projects ORDER BY json")
    suspend fun getProjects(): List<String>

    @Query("SELECT json FROM projects WHERE id = :id")
    suspend fun getProject(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Query(
        """
        SELECT projectId,
            CAST(COUNT(*) AS INTEGER) AS totalCount,
            CAST(SUM(CASE WHEN status NOT IN ('done', 'archived') THEN 1 ELSE 0 END) AS INTEGER) AS openCount,
            CAST(SUM(CASE WHEN status = 'todo' THEN 1 ELSE 0 END) AS INTEGER) AS todoCount,
            CAST(SUM(CASE WHEN status = 'in_progress' THEN 1 ELSE 0 END) AS INTEGER) AS inProgressCount,
            CAST(SUM(CASE WHEN status = 'done' THEN 1 ELSE 0 END) AS INTEGER) AS doneCount,
            CAST(SUM(CASE WHEN status = 'archived' THEN 1 ELSE 0 END) AS INTEGER) AS archivedCount
        FROM tasks
        GROUP BY projectId
        """,
    )
    suspend fun getTaskCountsByProject(): List<ProjectTaskCountsRow>

    @Query("SELECT json FROM tasks WHERE projectId = :projectId ORDER BY json")
    suspend fun getTasks(projectId: String): List<String>

    @Query("SELECT json FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun deleteTasksForProject(projectId: String)

    @Query("SELECT json FROM subtasks WHERE taskId = :taskId ORDER BY json")
    suspend fun getSubTasks(taskId: String): List<String>

    @Query("SELECT json FROM subtasks WHERE id = :id")
    suspend fun getSubTask(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubTask(subtask: SubTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubTasks(subtasks: List<SubTaskEntity>)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubTask(id: String)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksForTask(taskId: String)

    @Query("SELECT json FROM templates ORDER BY templateKey")
    suspend fun getTemplates(): List<String>

    @Query("SELECT json FROM templates WHERE templateKey = :templateKey")
    suspend fun getTemplate(templateKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: TemplateEntity)
}

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        SubTaskEntity::class,
        TemplateEntity::class,
    ],
    version = 6,
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
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
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
                        lastError TEXT
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE projects_new (id TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                db.execSQL("INSERT INTO projects_new (id, json) SELECT id, json FROM projects WHERE syncStatus != 'pending_delete'")
                db.execSQL("DROP TABLE projects")
                db.execSQL("ALTER TABLE projects_new RENAME TO projects")

                db.execSQL("CREATE TABLE tasks_new (id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, json TEXT NOT NULL)")
                db.execSQL("INSERT INTO tasks_new (id, projectId, json) SELECT id, projectId, json FROM tasks WHERE syncStatus != 'pending_delete' AND projectId IN (SELECT id FROM projects)")
                db.execSQL("DROP TABLE tasks")
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")

                db.execSQL("CREATE TABLE subtasks_new (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, json TEXT NOT NULL)")
                db.execSQL("INSERT INTO subtasks_new (id, taskId, json) SELECT id, taskId, json FROM subtasks WHERE syncStatus != 'pending_delete' AND taskId IN (SELECT id FROM tasks)")
                db.execSQL("DROP TABLE subtasks")
                db.execSQL("ALTER TABLE subtasks_new RENAME TO subtasks")

                db.execSQL("DROP TABLE IF EXISTS pending_mutations")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_projectId ON tasks (projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_subtasks_taskId ON subtasks (taskId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'todo'")
                val statuses = mutableListOf<Pair<String, String>>()
                db.query("SELECT id, json FROM tasks").use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow("id")
                    val jsonColumn = cursor.getColumnIndexOrThrow("json")
                    while (cursor.moveToNext()) {
                        val status = try {
                            JSONObject(cursor.getString(jsonColumn)).optString("status", STATUS_TODO).ifBlank { STATUS_TODO }
                        } catch (_: Exception) {
                            STATUS_TODO
                        }
                        statuses += cursor.getString(idColumn) to status
                    }
                }
                statuses.forEach { (id, status) ->
                    db.execSQL("UPDATE tasks SET status = ? WHERE id = ?", arrayOf(status, id))
                }
            }
        }
    }
}

class RoomLocalStore(context: Context) : LocalStore {
    private val dao = AdoDatabase.get(context).adoDao()

    override suspend fun getProjects(): List<Project> =
        dao.getProjects().map { Project.fromJson(JSONObject(it)) }

    override suspend fun getProject(projectId: String): Project? =
        dao.getProject(projectId)?.let { Project.fromJson(JSONObject(it)) }

    override suspend fun saveProject(project: Project) {
        dao.upsertProject(projectEntity(project))
    }

    override suspend fun deleteProject(projectId: String) {
        dao.deleteProject(projectId)
        dao.deleteTasksForProject(projectId)
    }

    override suspend fun getTaskCountsByProject(): Map<String, TaskCounts> =
        dao.getTaskCountsByProject().associate { row ->
            row.projectId to TaskCounts(
                total = row.totalCount,
                open = row.openCount,
                todo = row.todoCount,
                inProgress = row.inProgressCount,
                done = row.doneCount,
                archived = row.archivedCount,
            )
        }

    override suspend fun getTasks(projectId: String): List<Task> =
        dao.getTasks(projectId).map { Task.fromJson(JSONObject(it)) }

    override suspend fun getTask(taskId: String): Task? =
        dao.getTask(taskId)?.let { Task.fromJson(JSONObject(it)) }

    override suspend fun saveTask(task: Task) {
        dao.upsertTask(taskEntity(task))
    }

    override suspend fun deleteTask(task: Task) {
        dao.deleteTask(task.id)
        dao.deleteSubTasksForTask(task.id)
    }

    override suspend fun getSubTasks(taskId: String): List<SubTask> =
        dao.getSubTasks(taskId).map { SubTask.fromJson(JSONObject(it)) }

    override suspend fun getSubTask(subTaskId: String): SubTask? =
        dao.getSubTask(subTaskId)?.let { SubTask.fromJson(JSONObject(it)) }

    override suspend fun saveSubTask(subTask: SubTask) {
        dao.upsertSubTask(subTaskEntity(subTask))
    }

    override suspend fun saveSubTasks(subTasks: List<SubTask>) {
        dao.upsertSubTasks(subTasks.map(::subTaskEntity))
    }

    override suspend fun deleteSubTask(subTask: SubTask) {
        dao.deleteSubTask(subTask.id)
    }

    override suspend fun getTemplates(): List<Template> =
        dao.getTemplates().map { Template.fromJson(JSONObject(it)) }

    override suspend fun getTemplate(templateKey: String): Template? =
        dao.getTemplate(templateKey)?.let { Template.fromJson(JSONObject(it)) }

    override suspend fun saveTemplate(template: Template) {
        dao.upsertTemplate(TemplateEntity(template.templateKey, template.toJson().toString()))
    }

    private fun projectEntity(project: Project) =
        ProjectEntity(project.id, project.toJson().toString())

    private fun taskEntity(task: Task) =
        TaskEntity(task.id, task.projectId, task.status, task.toJson().toString())

    private fun subTaskEntity(subTask: SubTask) =
        SubTaskEntity(subTask.id, subTask.taskId, subTask.toJson().toString())
}

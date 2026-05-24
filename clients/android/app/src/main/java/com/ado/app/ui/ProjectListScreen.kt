package com.ado.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ado.app.data.AdoRepository
import com.ado.app.data.Project
import kotlinx.coroutines.launch

@Composable
fun ProjectListScreen(
    repository: AdoRepository,
    onOpenProject: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showingCache by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    var simpleView by remember { mutableStateOf(false) }
    var showOfflinePrompt by remember { mutableStateOf(false) }
    val offlineMode by repository.offlineModeFlow.collectAsState(initial = false)

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            val cached = repository.getCachedProjects()
            if (cached.isNotEmpty()) {
                projects = cached
                showingCache = true
            }
            val result = repository.getProjects()
            projects = result.data
            showingCache = result.fromCache
            val resultError = if (repository.isOfflineMode()) null else result.errorMessage
            error = resultError
            if (resultError != null) {
                showOfflinePrompt = true
            }
            pendingCount = repository.pendingMutationCount()
            loading = false
        }
    }

    LaunchedEffect(offlineMode) {
        if (offlineMode) {
            error = null
            showOfflinePrompt = false
        }
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

    fun createProject(name: String, description: String, tags: List<String>) {
        scope.launch {
            error = null
            try {
                val created = repository.createProject(name, description, tags)
                projects = (projects.filterNot { it.id == created.id } + created).sortedBy { it.name }
                showCreateDialog = false
                refreshPendingCount()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    fun deleteProject(project: Project) {
        scope.launch {
            error = null
            try {
                repository.deleteProject(project)
                projects = projects.filterNot { it.id == project.id }
                projectToDelete = null
                refreshPendingCount()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
                projectToDelete = null
            }
        }
    }

    fun updateProject(project: Project, name: String, description: String, tags: List<String>) {
        scope.launch {
            error = null
            try {
                val updated = repository.updateProject(project, name, description, tags)
                projects = (projects.filterNot { it.id == updated.id } + updated).sortedBy { it.name }
                projectToEdit = null
                refreshPendingCount()
            } catch (e: Exception) {
                error = repository.friendlyError(e)
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    AdoScaffold(
        title = "Projects",
        onSettings = onOpenSettings,
        offlineMode = offlineMode,
        onToggleOfflineMode = { setOfflineMode(!offlineMode) },
        actions = {
            TextButton(onClick = onOpenTemplates) { Text("Templates") }
        },
        bottomActions = listOf(
            BottomBarAction(
                label = "Add",
                onClick = { showCreateDialog = true },
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
            BottomBarAction(
                label = "Queue",
                onClick = onOpenSync,
            ),
        ),
    ) { padding ->
        Column(modifier = padding) {
            if (!offlineMode && error != null && projects.isEmpty()) {
                ErrorBanner(message = error ?: "Unable to load projects", onRetry = { refresh() })
            } else if (!offlineMode && error != null && showingCache) {
                OfflineBanner("Showing cached projects. ${error.orEmpty()}")
            }

            when {
                loading && projects.isEmpty() -> LoadingState()
                projects.isEmpty() -> EmptyState("No projects yet.")
                else -> LazyColumn {
                    items(projects, key = { it.id }) { project ->
                        if (simpleView) {
                            ProjectSimpleRow(
                                project = project,
                                onClick = { onOpenProject(project.id) },
                            )
                        } else {
                            ProjectRow(
                                project = project,
                                onClick = { onOpenProject(project.id) },
                                onEdit = { projectToEdit = project },
                                onDelete = if (project.isCore) null else ({ projectToDelete = project }),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        EntityFormDialog(
            title = "New project",
            nameLabel = "Project name",
            includeTags = true,
            onDismiss = { showCreateDialog = false },
            onSubmit = ::createProject,
        )
    }

    projectToDelete?.let { project ->
        ConfirmDeleteDialog(
            title = "Delete project",
            message = "Delete ${project.name} and its tasks?",
            onDismiss = { projectToDelete = null },
            onConfirm = { deleteProject(project) },
        )
    }

    projectToEdit?.let { project ->
        EntityFormDialog(
            title = "Edit project",
            nameLabel = "Project name",
            includeTags = true,
            initialName = project.name,
            initialDescription = project.description,
            initialTags = project.tags,
            onDismiss = { projectToEdit = null },
            onSubmit = { name, description, tags -> updateProject(project, name, description, tags) },
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

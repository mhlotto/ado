package com.ado.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ado.app.R
import com.ado.app.data.LIST_TYPES
import com.ado.app.data.LIST_TYPE_CHECKLIST
import com.ado.app.data.LIST_TYPE_CUSTOM
import com.ado.app.data.LIST_TYPE_DAILY
import com.ado.app.data.LIST_TYPE_MARKET
import com.ado.app.data.LIST_TYPE_NORMAL
import com.ado.app.data.Project
import com.ado.app.data.SubTask
import com.ado.app.data.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class OpenDoneCounts(val open: Int, val done: Int) {
    val total: Int
        get() = open + done
}

data class BottomBarAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val prominent: Boolean = false,
    val emphasized: Boolean = false,
    val iconResource: Int? = null,
    val contentDescription: String = label,
    val menuActions: List<BottomBarMenuAction> = emptyList(),
)

data class BottomBarMenuAction(
    val label: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdoScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    bottomActions: List<BottomBarAction> = emptyList(),
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    AppBarNavigationSlot(onBack = onBack)
                },
                actions = {
                    if (onSettings != null) {
                        TextButton(onClick = onSettings) { Text("Settings") }
                    }
                },
            )
        },
        bottomBar = {
            if (bottomActions.isNotEmpty()) {
                BottomFloatingActionBar(
                    actions = bottomActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
fun BottomFloatingActionBar(
    actions: List<BottomBarAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    BottomActionControl(action)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomActionControl(action: BottomBarAction) {
    var menuExpanded by remember(action.label) { mutableStateOf(false) }

    Box {
        if (action.iconResource != null && action.menuActions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .combinedClickable(
                        enabled = action.enabled,
                        role = Role.Button,
                        onClick = { menuExpanded = true },
                        onLongClick = {
                            // Consume the hold so releasing it cannot become a delayed tap.
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(action.iconResource),
                    contentDescription = action.contentDescription,
                    tint = if (action.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        } else if (action.iconResource != null) {
            IconButton(onClick = action.onClick, enabled = action.enabled) {
                Icon(
                    painter = painterResource(action.iconResource),
                    contentDescription = action.contentDescription,
                )
            }
        } else if (action.prominent) {
            Button(onClick = action.onClick, enabled = action.enabled) {
                Text(action.label)
            }
        } else {
            TextButton(onClick = action.onClick, enabled = action.enabled) {
                Text(
                    text = action.label,
                    fontWeight = if (action.emphasized) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            action.menuActions.forEach { menuAction ->
                DropdownMenuItem(
                    text = { Text(menuAction.label) },
                    onClick = {
                        menuExpanded = false
                        menuAction.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun AppBarNavigationSlot(onBack: (() -> Unit)?) {
    val navigationModifier = if (onBack != null) {
        Modifier
            .clickable(onClick = onBack)
            .semantics { contentDescription = "Back" }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .then(navigationModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Text(
                text = "<",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.topbar_icon),
                contentDescription = "ado",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun LoadingState(label: String = "Loading...") {
    Text(
        text = label,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun EmptyState(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ErrorBanner(message: String, retryLabel: String = "Retry", onRetry: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(retryLabel) }
            }
        }
    }
}

@Composable
fun InfoBanner(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun SpecialListTypeLabel(listType: String) {
    if (listType == LIST_TYPE_NORMAL) return
    Text(
        text = listTypeLabel(listType),
        color = MutedTextColor,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun FinishedAtMetadata(finishedAt: String?) {
    val finishedLabel = finishedAt?.let(::formatFinishedAt) ?: return
    Text(
        text = "Completed $finishedLabel",
        color = MutedTextColor,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun formatFinishedAt(finishedAt: String): String? =
    try {
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
            .format(Instant.parse(finishedAt).atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        null
    }

@Composable
fun OpenDoneStatTiles(
    openCount: Int,
    doneCount: Int,
    modifier: Modifier = Modifier,
    hideWhenEmpty: Boolean = false,
) {
    OpenDoneStatTiles(
        counts = OpenDoneCounts(open = openCount, done = doneCount),
        modifier = modifier,
        hideWhenEmpty = hideWhenEmpty,
    )
}

@Composable
fun OpenDoneStatTiles(
    counts: OpenDoneCounts,
    modifier: Modifier = Modifier,
    hideWhenEmpty: Boolean = false,
) {
    if (hideWhenEmpty && counts.total == 0) {
        return
    }
    Row(
        modifier = modifier
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountStatTile(
            label = "open",
            count = counts.open,
            backgroundColor = Color(0xFF294C46),
            contentColor = Color(0xFFB8E2C3),
        )
        CountStatTile(
            label = "done",
            count = counts.done,
            backgroundColor = Color(0xFF563E45),
            contentColor = Color(0xFFF3BCC0),
        )
    }
}

@Composable
fun CountStatTile(
    label: String,
    count: Int,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(52.dp)
            .heightIn(min = 48.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
fun FinishedSectionHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 8.dp),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            thickness = 0.5.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Finished ($count)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
fun ProjectSimpleRow(project: Project, onClick: () -> Unit) {
    SimpleNameRow(
        name = project.name,
        isDone = false,
        indicator = if (project.isCore) "Core" else null,
        onClick = onClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectRow(project: Project, onClick: () -> Unit, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (project.isCore) {
                            Text("Core", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (project.description.isNotBlank()) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (project.tags.isNotEmpty()) {
                        Text(
                            text = "tags: ${project.tags.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OpenDoneStatTiles(
                    counts = OpenDoneCounts(open = project.taskCounts.open, done = project.taskCounts.done),
                )
            }
            CompactRowActions(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskSimpleRow(
    task: Task,
    subTaskCounts: OpenDoneCounts? = null,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val clickableModifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionCheckbox(name = task.name, isDone = task.isDone, onToggle = onToggle)
            HttpLinkText(
                text = task.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
        if (subTaskCounts != null && subTaskCounts.total > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OpenDoneStatTiles(
                    counts = subTaskCounts,
                    hideWhenEmpty = true,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            thickness = 0.5.dp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: Task,
    subTaskCounts: OpenDoneCounts? = null,
    showFinishedAt: Boolean = false,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CompletionCheckbox(name = task.name, isDone = task.isDone, onToggle = onToggle)
                Column(modifier = Modifier.weight(1f)) {
                    HttpLinkText(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (task.description.isNotBlank()) {
                        HttpLinkText(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    FinishedAtMetadata(task.finishedAt.takeIf { showFinishedAt })
                }
                if (subTaskCounts != null && subTaskCounts.total > 0) {
                    OpenDoneStatTiles(
                        counts = subTaskCounts,
                        hideWhenEmpty = true,
                    )
                }
            }
            if (onEdit != null || onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit_24),
                                contentDescription = "Edit ${task.name}",
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_24),
                                contentDescription = "Delete ${task.name}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubTaskSimpleRow(subTask: SubTask, onClick: () -> Unit, onToggle: () -> Unit, onLongPress: () -> Unit) {
    SimpleNameRow(
        name = subTask.name,
        isDone = subTask.isDone,
        indicator = null,
        onClick = onClick,
        onToggle = onToggle,
        onLongPress = onLongPress,
        linkify = true,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubTaskRow(
    subTask: SubTask,
    showFinishedAt: Boolean = false,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CompletionCheckbox(name = subTask.name, isDone = subTask.isDone, onToggle = onToggle)
            Column(modifier = Modifier.weight(1f)) {
                HttpLinkText(
                    text = subTask.name,
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    textDecoration = if (subTask.isDone) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (subTask.description.isNotBlank()) {
                    HttpLinkText(
                        text = subTask.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                FinishedAtMetadata(subTask.finishedAt.takeIf { showFinishedAt })
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24),
                        contentDescription = "Edit ${subTask.name}",
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24),
                        contentDescription = "Delete ${subTask.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleNameRow(
    name: String,
    isDone: Boolean,
    indicator: String?,
    onClick: () -> Unit,
    onToggle: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    linkify: Boolean = false,
) {
    val clickableModifier = if (onLongPress != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggle != null) {
                CompletionCheckbox(name = name, isDone = isDone, onToggle = onToggle)
            }
            val nameDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
            if (linkify) {
                HttpLinkText(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    textDecoration = nameDecoration,
                )
            } else {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = nameDecoration,
                )
            }
            if (indicator != null) {
                Text(
                    text = indicator,
                    color = MutedTextColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun CompletionCheckbox(name: String, isDone: Boolean, onToggle: () -> Unit) {
    Checkbox(
        checked = isDone,
        onCheckedChange = { onToggle() },
        modifier = Modifier.semantics {
            contentDescription = if (isDone) "Mark $name incomplete" else "Mark $name complete"
        },
    )
}

@Composable
private fun CompactRowActions(onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    if (onEdit == null && onDelete == null) {
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (onEdit != null) {
            CompactTextAction("Edit", onEdit)
        }
        if (onDelete != null) {
            CompactTextAction("Delete", onDelete)
        }
    }
}

@Composable
private fun CompactTextAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text = label,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MutedTextColor,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.padding(4.dp)) {
        Text(label)
    }
}

val MutedTextColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

fun listTypeLabel(type: String): String = when (type) {
    LIST_TYPE_DAILY -> "Daily"
    LIST_TYPE_MARKET -> "Market"
    LIST_TYPE_CHECKLIST -> "Checklist"
    LIST_TYPE_CUSTOM -> "Custom ordered"
    else -> "Normal"
}

@Composable
fun ListTypeSettingsButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = "\u2699",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListTypeDialog(
    title: String,
    currentType: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var selectedType by remember(title, currentType) {
        mutableStateOf(currentType.takeIf { it in LIST_TYPES } ?: LIST_TYPE_NORMAL)
    }
    var expanded by remember(title) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = listTypeLabel(selectedType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("List type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        LIST_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(listTypeLabel(type)) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedType) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun EntityFormDialog(
    title: String,
    nameLabel: String,
    descriptionLabel: String = "Description",
    includeTags: Boolean = false,
    initialName: String = "",
    initialDescription: String = "",
    initialTags: List<String> = emptyList(),
    autoFocusName: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String, tags: List<String>) -> Unit,
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var tags by remember(title, initialTags) { mutableStateOf(initialTags.joinToString(", ")) }
    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var initialFocusRequested by rememberSaveable(title) { mutableStateOf(false) }

    LaunchedEffect(autoFocusName, initialFocusRequested) {
        if (autoFocusName && !initialFocusRequested) {
            withFrameNanos { }
            nameFocusRequester.requestFocus()
            keyboardController?.show()
            initialFocusRequested = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(nameFocusRequester),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(descriptionLabel) },
                )
                if (includeTags) {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags, comma separated") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onSubmit(
                        name.trim(),
                        description.trim(),
                        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

data class MoveOption(val id: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveEntityFormDialog(
    title: String,
    nameLabel: String,
    initialName: String,
    initialDescription: String,
    destinationLabel: String,
    options: List<MoveOption>,
    initialDestinationId: String,
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String, destinationId: String) -> Unit,
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var selectedDestinationId by remember(title, initialDestinationId, options) {
        mutableStateOf(options.firstOrNull { it.id == initialDestinationId }?.id ?: options.firstOrNull()?.id.orEmpty())
    }
    var destinationExpanded by remember(title, options) { mutableStateOf(false) }
    val selectedDestinationLabel = options.firstOrNull { it.id == selectedDestinationId }?.label.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                )
                if (options.isEmpty()) {
                    Text("No destinations available.", color = MutedTextColor)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = destinationExpanded,
                        onExpandedChange = { destinationExpanded = !destinationExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedDestinationLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(destinationLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = destinationExpanded,
                            onDismissRequest = { destinationExpanded = false },
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedDestinationId = option.id
                                        destinationExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty() && selectedDestinationId.isNotBlank(),
                onClick = { onSubmit(name.trim(), description.trim(), selectedDestinationId) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

data class TemplatePickerAction(val label: String, val onClick: () -> Unit)

@Composable
fun BulkTextCreateDialog(
    title: String,
    bulkLabel: String,
    bulkHelp: String,
    onDismiss: () -> Unit,
    onSubmitBulk: (text: String) -> Unit,
) {
    var bulkText by remember(title) { mutableStateOf("") }
    val bulkFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var initialFocusRequested by rememberSaveable(title) { mutableStateOf(false) }

    LaunchedEffect(initialFocusRequested) {
        if (!initialFocusRequested) {
            withFrameNanos { }
            bulkFocusRequester.requestFocus()
            keyboardController?.show()
            initialFocusRequested = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(bulkHelp, style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
                OutlinedTextField(
                    value = bulkText,
                    onValueChange = { bulkText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(bulkFocusRequester),
                    label = { Text(bulkLabel) },
                    minLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = bulkText.trim().isNotEmpty(),
                onClick = { onSubmitBulk(bulkText) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerBottomSheet(
    actions: List<TemplatePickerAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Choose template",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ConfirmChoiceDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { (onCancel ?: onDismiss)() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

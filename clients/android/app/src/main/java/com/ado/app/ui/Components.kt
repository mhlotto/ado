package com.ado.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdoScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    bottomActions: List<BottomBarAction> = emptyList(),
    content: @Composable (Modifier) -> Unit,
) {
    val swipeBackThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = {
                        AppBarNavigationSlot(onBack = onBack)
                    },
                    actions = {
                        if (onSettings != null) {
                            TextButton(onClick = onSettings) { Text("Settings") }
                        }
                    },
                )
                if (actions != null) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        actions()
                    }
                }
            }
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
        val contentModifier = Modifier
            .padding(padding)
            .then(
                if (onBack != null) {
                    Modifier.swipeLeftToBack(
                        thresholdPx = swipeBackThresholdPx,
                        onBack = onBack,
                    )
                } else {
                    Modifier
                },
            )
        content(contentModifier)
    }
}

private fun Modifier.swipeLeftToBack(
    thresholdPx: Float,
    onBack: () -> Unit,
): Modifier = pointerInput(thresholdPx, onBack) {
    var totalDragX = 0f
    detectHorizontalDragGestures(
        onDragStart = {
            totalDragX = 0f
        },
        onDragCancel = {
            totalDragX = 0f
        },
        onDragEnd = {
            if (totalDragX <= -thresholdPx) {
                onBack()
            }
            totalDragX = 0f
        },
        onHorizontalDrag = { _, dragAmount ->
            totalDragX += dragAmount
        },
    )
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.take(4).forEach { action ->
                    if (action.prominent) {
                        Button(
                            onClick = action.onClick,
                            enabled = action.enabled,
                        ) {
                            Text(action.label)
                        }
                    } else {
                        TextButton(
                            onClick = action.onClick,
                            enabled = action.enabled,
                        ) {
                            Text(
                                text = action.label,
                                fontWeight = if (action.emphasized) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBarNavigationSlot(onBack: (() -> Unit)?) {
    Box(
        modifier = Modifier.size(width = 56.dp, height = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Text(
                text = "<",
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
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
fun StatusText(status: String, finishedAt: String? = null) {
    val finishedLabel = finishedAt?.let(::formatFinishedAt)
    Text(
        text = if (finishedLabel == null) status else "$status - $finishedLabel",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
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
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        if (project.isCore) {
                            Text("Core", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (project.description.isNotBlank()) {
                        Text(project.description, style = MaterialTheme.typography.bodyMedium)
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
fun TaskSimpleRow(task: Task, subTaskCounts: OpenDoneCounts? = null, onClick: () -> Unit, onLongPress: () -> Unit) {
    val clickableModifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HttpLinkText(
                text = task.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
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
                Column(modifier = Modifier.weight(1f)) {
                    HttpLinkText(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (task.description.isNotBlank()) {
                        HttpLinkText(task.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusText(
                            status = task.status,
                            finishedAt = task.finishedAt.takeIf { showFinishedAt },
                        )
                    }
                }
                if (subTaskCounts != null && subTaskCounts.total > 0) {
                    OpenDoneStatTiles(
                        counts = subTaskCounts,
                        hideWhenEmpty = true,
                    )
                }
            }
            CompactRowActions(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubTaskSimpleRow(subTask: SubTask, onClick: () -> Unit, onLongPress: () -> Unit) {
    SimpleNameRow(
        name = subTask.name,
        isDone = subTask.isDone,
        indicator = null,
        onClick = onClick,
        onLongPress = onLongPress,
        linkify = true,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubTaskRow(
    subTask: SubTask,
    showFinishedAt: Boolean = false,
    onLongPress: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            HttpLinkText(
                text = subTask.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (subTask.isDone) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (subTask.description.isNotBlank()) {
                HttpLinkText(subTask.description, style = MaterialTheme.typography.bodyMedium)
            }
            StatusText(
                status = subTask.status,
                finishedAt = subTask.finishedAt.takeIf { showFinishedAt },
            )
            if (onMoveUp != null || onMoveDown != null || onEdit != null || onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onMoveUp != null) {
                        CompactTextAction("Up", onMoveUp, enabled = canMoveUp)
                    }
                    if (onMoveDown != null) {
                        CompactTextAction("Down", onMoveDown, enabled = canMoveDown)
                    }
                    if (onEdit != null) {
                        CompactTextAction("Edit", onEdit)
                    }
                    if (onDelete != null) {
                        CompactTextAction("Delete", onDelete)
                    }
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
        ) {
            val nameDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
            if (linkify) {
                HttpLinkText(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = nameDecoration,
                )
            } else {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
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
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String, tags: List<String>) -> Unit,
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var tags by remember(title, initialTags) { mutableStateOf(initialTags.joinToString(", ")) }

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

private enum class CreateMode { Single, Bulk }

data class QuickAddAction(val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BulkCreateDialog(
    title: String,
    nameLabel: String,
    bulkLabel: String,
    bulkHelp: String,
    quickActionsLabel: String? = null,
    quickActions: List<QuickAddAction> = emptyList(),
    onDismiss: () -> Unit,
    onSubmitSingle: (name: String, description: String) -> Unit,
    onSubmitBulk: (text: String) -> Unit,
) {
    var mode by remember(title) { mutableStateOf(CreateMode.Single) }
    var name by remember(title) { mutableStateOf("") }
    var description by remember(title) { mutableStateOf("") }
    var bulkText by remember(title) { mutableStateOf("") }
    var quickActionsExpanded by remember(title, quickActions) { mutableStateOf(false) }
    val canSubmit = when (mode) {
        CreateMode.Single -> name.trim().isNotEmpty()
        CreateMode.Bulk -> bulkText.trim().isNotEmpty()
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
                if (quickActions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = quickActionsExpanded,
                        onExpandedChange = { quickActionsExpanded = !quickActionsExpanded },
                    ) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(quickActionsLabel ?: "Quick add") },
                            placeholder = { Text("Choose generated list") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quickActionsExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = quickActionsExpanded,
                            onDismissRequest = { quickActionsExpanded = false },
                        ) {
                            quickActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        quickActionsExpanded = false
                                        action.onClick()
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(
                        label = "Single",
                        selected = mode == CreateMode.Single,
                        onClick = { mode = CreateMode.Single },
                    )
                    ModeButton(
                        label = "Bulk",
                        selected = mode == CreateMode.Bulk,
                        onClick = { mode = CreateMode.Bulk },
                    )
                }
                if (mode == CreateMode.Single) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(nameLabel) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                    )
                } else {
                    Text(bulkHelp, style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(bulkLabel) },
                        minLines = 8,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    if (mode == CreateMode.Single) {
                        onSubmitSingle(name.trim(), description.trim())
                    } else {
                        onSubmitBulk(bulkText)
                    }
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

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
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

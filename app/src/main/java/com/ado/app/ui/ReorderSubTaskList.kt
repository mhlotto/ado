package com.ado.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ado.app.data.SubTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderSubTaskList(
    subTasks: List<SubTask>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onOrderSettled: (List<String>) -> Unit,
) {
    var orderedItems by remember { mutableStateOf(subTasks) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(subTasks) {
        if (draggingId == null) orderedItems = subTasks
    }

    fun finishDrag(save: Boolean) {
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggingId = null
        dragOffset = 0f
        if (save) {
            onOrderSettled(orderedItems.map { it.id })
        } else {
            orderedItems = subTasks
        }
    }

    LazyColumn(modifier = modifier, state = listState) {
        items(orderedItems, key = { it.id }) { subTask ->
            val isDragging = draggingId == subTask.id
            ReorderSubTaskRow(
                subTask = subTask,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                rowDragModifier = Modifier.pointerInput(subTask.id, enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingId = subTask.id
                            dragOffset = 0f
                        },
                        onDragCancel = { finishDrag(save = false) },
                        onDragEnd = { finishDrag(save = true) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y

                            val draggedId = draggingId ?: return@detectDragGesturesAfterLongPress
                            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId }
                            val itemSize = itemInfo?.size?.toFloat()?.coerceAtLeast(1f) ?: return@detectDragGesturesAfterLongPress
                            while (true) {
                                val from = orderedItems.indexOfFirst { it.id == draggedId }
                                val direction = when {
                                    dragOffset > itemSize / 2f && from < orderedItems.lastIndex -> 1
                                    dragOffset < -itemSize / 2f && from > 0 -> -1
                                    else -> break
                                }
                                val mutable = orderedItems.toMutableList()
                                val moved = mutable.removeAt(from)
                                mutable.add(from + direction, moved)
                                orderedItems = mutable
                                dragOffset -= direction * itemSize
                            }

                            val visibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId }
                            if (visibleInfo != null && autoScrollJob?.isActive != true) {
                                val edge = 56.dp.toPx()
                                val projectedTop = visibleInfo.offset + dragOffset
                                val projectedBottom = projectedTop + visibleInfo.size
                                val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
                                val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
                                val scrollAmount = when {
                                    projectedTop < viewportStart + edge -> -20.dp.toPx()
                                    projectedBottom > viewportEnd - edge -> 20.dp.toPx()
                                    else -> 0f
                                }
                                if (abs(scrollAmount) > 0f) {
                                    autoScrollJob = scope.launch {
                                        dragOffset += listState.scrollBy(scrollAmount)
                                    }
                                }
                            }
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ReorderSubTaskRow(
    subTask: SubTask,
    isDragging: Boolean,
    dragOffset: Float,
    rowDragModifier: Modifier,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .then(rowDragModifier),
        colors = if (isDragging) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = "Drag ${subTask.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("≡", style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subTask.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (subTask.isDone) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (subTask.description.isNotBlank()) {
                    Text(subTask.description, style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
                }
                if (subTask.isDone) {
                    Text("Completed", style = MaterialTheme.typography.labelSmall, color = MutedTextColor)
                }
            }
        }
    }
}

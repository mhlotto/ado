package com.ado.app.ui

import android.content.Context
import android.content.Intent
import com.ado.app.data.SubTask
import com.ado.app.data.Task

internal fun buildTaskShareText(task: Task, subtasks: List<SubTask>): String {
    val title = task.name.trim().ifBlank { "Task" }
    val remainingItems = subtasks
        .asSequence()
        .filterNot { it.isDone }
        .map { it.name.trim() }
        .filter { it.isNotBlank() }
        .toList()

    if (remainingItems.isEmpty()) return title

    return buildString {
        append(title)
        append("\n\n")
        remainingItems.forEachIndexed { index, item ->
            append("- ")
            append(item)
            if (index < remainingItems.lastIndex) append('\n')
        }
    }
}

internal fun shareTask(context: Context, task: Task, subtasks: List<SubTask>) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, task.name.trim())
        putExtra(Intent.EXTRA_TEXT, buildTaskShareText(task, subtasks))
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share task"))
}

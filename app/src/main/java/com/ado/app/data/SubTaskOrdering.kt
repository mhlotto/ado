package com.ado.app.data

internal fun reorderSubTasksById(subTasks: List<SubTask>, orderedIds: List<String>): List<SubTask> {
    require(orderedIds.size == orderedIds.distinct().size) { "Subtask order contains duplicate items." }
    require(orderedIds.toSet() == subTasks.mapTo(mutableSetOf()) { it.id }) {
        "Subtask order does not match the task's items."
    }
    val byId = subTasks.associateBy { it.id }
    return orderedIds.mapIndexed { position, id -> byId.getValue(id).copy(position = position) }
}

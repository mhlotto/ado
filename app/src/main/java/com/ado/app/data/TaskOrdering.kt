package com.ado.app.data

internal fun reorderTasksById(tasks: List<Task>, orderedIds: List<String>): List<Task> {
    require(orderedIds.size == orderedIds.distinct().size) { "Task order contains duplicate items." }
    require(orderedIds.toSet() == tasks.mapTo(mutableSetOf()) { it.id }) {
        "Task order does not match the project's items."
    }
    val byId = tasks.associateBy { it.id }
    return orderedIds.mapIndexed { position, id -> byId.getValue(id).copy(position = position) }
}

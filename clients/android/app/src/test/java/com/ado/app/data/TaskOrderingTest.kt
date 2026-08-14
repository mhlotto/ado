package com.ado.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOrderingTest {
    private val tasks = listOf(
        task("one", 0),
        task("two", 1),
        task("three", 2, status = STATUS_DONE),
        task("four", 3),
        task("five", 4),
    )

    @Test
    fun movesFirstTaskToLast() {
        assertOrder("two", "three", "four", "five", "one")
    }

    @Test
    fun movesLastTaskToFirst() {
        assertOrder("five", "one", "two", "three", "four")
    }

    @Test
    fun movesTaskSeveralPositions() {
        assertOrder("one", "three", "four", "two", "five")
    }

    @Test
    fun preservesEveryTaskExactlyOnceAndAssignsSequentialPositions() {
        val reordered = reorderTasksById(tasks, listOf("three", "five", "one", "four", "two"))

        assertEquals(tasks.map { it.id }.toSet(), reordered.map { it.id }.toSet())
        assertEquals(tasks.size, reordered.size)
        assertEquals(reordered.indices.toList(), reordered.map { it.position })
    }

    @Test
    fun completedTaskRetainsCompletionState() {
        val reordered = reorderTasksById(tasks, listOf("three", "one", "two", "four", "five"))

        assertTrue(reordered.first().isDone)
    }

    private fun assertOrder(vararg ids: String) {
        assertEquals(ids.toList(), reorderTasksById(tasks, ids.toList()).map { it.id })
    }

    private fun task(id: String, position: Int, status: String = STATUS_TODO) = Task(
        id = id,
        projectId = "project",
        name = id,
        description = "",
        status = status,
        createdAt = "2026-01-01T00:00:00Z",
        finishedAt = if (status == STATUS_DONE) "2026-01-02T00:00:00Z" else null,
        updatedAt = "2026-01-01T00:00:00Z",
        deletedAt = null,
        position = position,
    )
}

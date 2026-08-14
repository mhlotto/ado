package com.ado.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubTaskOrderingTest {
    private val items = listOf(
        subTask("milk", 0),
        subTask("apples", 1),
        subTask("soap", 2, status = STATUS_DONE),
        subTask("bread", 3),
        subTask("chicken", 4),
        subTask("coffee", 5),
    )

    @Test
    fun movesFirstItemToLast() {
        assertOrder("apples", "soap", "bread", "chicken", "coffee", "milk")
    }

    @Test
    fun movesLastItemToFirst() {
        assertOrder("coffee", "milk", "apples", "soap", "bread", "chicken")
    }

    @Test
    fun movesItemSeveralPositions() {
        assertOrder("apples", "milk", "chicken", "bread", "coffee", "soap")
    }

    @Test
    fun preservesEveryItemExactlyOnceAndAssignsSequentialPositions() {
        val reordered = reorderSubTasksById(items, listOf("soap", "coffee", "milk", "bread", "apples", "chicken"))

        assertEquals(items.map { it.id }.toSet(), reordered.map { it.id }.toSet())
        assertEquals(items.size, reordered.size)
        assertEquals(reordered.indices.toList(), reordered.map { it.position })
    }

    @Test
    fun completedItemRetainsCompletionState() {
        val reordered = reorderSubTasksById(items, listOf("soap", "milk", "apples", "bread", "chicken", "coffee"))

        assertTrue(reordered.first().isDone)
    }

    private fun assertOrder(vararg ids: String) {
        assertEquals(ids.toList(), reorderSubTasksById(items, ids.toList()).map { it.id })
    }

    private fun subTask(id: String, position: Int, status: String = STATUS_TODO) = SubTask(
        id = id,
        taskId = "task",
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

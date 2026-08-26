package com.ado.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FinishedOrderingTest {
    @Test
    fun sortsValidCompletionTimesNewestFirst() {
        val items = listOf(
            FinishedItem("oldest", "2026-08-20T12:00:00Z"),
            FinishedItem("newest", "2026-08-23T12:00:00Z"),
            FinishedItem("middle", "2026-08-21T12:00:00Z"),
        )

        assertEquals(
            listOf("newest", "middle", "oldest"),
            newestFinishedFirst(items) { it.finishedAt }.map { it.name },
        )
    }

    @Test
    fun missingAndInvalidTimesFollowTimestampedItemsInExistingOrder() {
        val items = listOf(
            FinishedItem("missing", null),
            FinishedItem("valid", "2026-08-23T12:00:00Z"),
            FinishedItem("invalid", "not-a-timestamp"),
        )

        assertEquals(
            listOf("valid", "missing", "invalid"),
            newestFinishedFirst(items) { it.finishedAt }.map { it.name },
        )
    }

    private data class FinishedItem(val name: String, val finishedAt: String?)
}

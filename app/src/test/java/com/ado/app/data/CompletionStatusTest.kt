package com.ado.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionStatusTest {
    @Test
    fun incompleteItemCanBeCompleted() {
        assertEquals(STATUS_DONE, toggledStatus(STATUS_TODO))
    }

    @Test
    fun completedItemCanBeRestored() {
        assertEquals(STATUS_TODO, toggledStatus(STATUS_DONE))
    }
}

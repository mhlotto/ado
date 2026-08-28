package com.ado.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun addsAndRemovesIndependentViewPreferenceIds() {
        val initial = setOf("project-one")

        val added = initial.withMembership("project-two", included = true)
        val removed = added.withMembership("project-one", included = false)

        assertEquals(setOf("project-one", "project-two"), added)
        assertEquals(setOf("project-two"), removed)
    }
}

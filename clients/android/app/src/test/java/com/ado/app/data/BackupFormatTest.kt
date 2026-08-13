package com.ado.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    @Test
    fun acceptsCurrentVersion() {
        val root = parseCurrentBackup(validBackup())

        assertEquals(BACKUP_FORMAT, root.getString("format"))
        assertEquals(CURRENT_BACKUP_VERSION, root.getInt("version"))
    }

    @Test
    fun rejectsMissingVersion() {
        assertFailure("version is missing or invalid") {
            parseCurrentBackup(validBackup(versionEntry = ""))
        }
    }

    @Test
    fun rejectsNonIntegerVersion() {
        assertFailure("version is missing or invalid") {
            parseCurrentBackup(validBackup(versionEntry = "\"version\": \"2\","))
        }
    }

    @Test
    fun rejectsOlderUnsupportedVersion() {
        assertFailure("version 1 is no longer supported") {
            parseCurrentBackup(validBackup(versionEntry = "\"version\": 1,"))
        }
    }

    @Test
    fun rejectsNewerVersion() {
        assertFailure("created by a newer version") {
            parseCurrentBackup(validBackup(versionEntry = "\"version\": 3,"))
        }
    }

    @Test
    fun rejectsWrongFormat() {
        assertFailure("Not an ado backup file") {
            parseCurrentBackup(validBackup(format = "something-else"))
        }
    }

    @Test
    fun rejectsMissingDatasetArray() {
        assertFailure("missing or invalid 'templates' list") {
            parseCurrentBackup(validBackup(includeTemplates = false))
        }
    }

    @Test
    fun rejectsDatasetThatIsNotAnArray() {
        assertFailure("missing or invalid 'tasks' list") {
            parseCurrentBackup(validBackup(tasks = "{}"))
        }
    }

    private fun validBackup(
        format: String = BACKUP_FORMAT,
        versionEntry: String = "\"version\": $CURRENT_BACKUP_VERSION,",
        tasks: String = "[]",
        includeTemplates: Boolean = true,
    ): String = """
        {
          "format": "$format",
          $versionEntry
          "projects": [],
          "tasks": $tasks,
          "subtasks": []${if (includeTemplates) ",\n  \"templates\": []" else ""}
        }
    """.trimIndent()

    private fun assertFailure(expectedMessage: String, block: () -> Unit) {
        val error = try {
            block()
            throw AssertionError("Expected import validation to fail")
        } catch (error: IllegalArgumentException) {
            error
        }
        assertTrue("Expected '${error.message}' to contain '$expectedMessage'", error.message.orEmpty().contains(expectedMessage))
    }
}

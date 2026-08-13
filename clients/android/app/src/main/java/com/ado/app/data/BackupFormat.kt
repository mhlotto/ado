package com.ado.app.data

import org.json.JSONArray
import org.json.JSONObject

internal const val BACKUP_FORMAT = "ado-local-export"
internal const val CURRENT_BACKUP_VERSION = 2

private val requiredDatasetArrays = listOf("projects", "tasks", "subtasks", "templates")

/**
 * Validates the backup envelope and returns data normalized to the current format.
 * Add explicit older-version migrations here when a future format version is introduced.
 */
internal fun parseCurrentBackup(raw: String): JSONObject {
    val root = try {
        JSONObject(raw)
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid backup file.")
    }

    if (root.optString("format") != BACKUP_FORMAT) {
        throw IllegalArgumentException("Not an ado backup file.")
    }

    val rawVersion = root.opt("version")
    val version = if (rawVersion is Number && rawVersion.toDouble() % 1.0 == 0.0) {
        rawVersion.toInt()
    } else {
        throw IllegalArgumentException("Backup version is missing or invalid.")
    }

    val current = when {
        version == CURRENT_BACKUP_VERSION -> root
        version > CURRENT_BACKUP_VERSION -> throw IllegalArgumentException(
            "This backup was created by a newer version of ado (backup version $version). Update ado before importing it.",
        )
        else -> throw IllegalArgumentException(
            "Backup version $version is no longer supported by this version of ado.",
        )
    }

    requiredDatasetArrays.forEach { name ->
        if (current.opt(name) !is JSONArray) {
            throw IllegalArgumentException("Invalid backup file: missing or invalid '$name' list.")
        }
    }
    return current
}

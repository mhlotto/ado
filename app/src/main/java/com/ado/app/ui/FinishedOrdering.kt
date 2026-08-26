package com.ado.app.ui

import java.time.Instant

internal fun <T> newestFinishedFirst(
    items: List<T>,
    finishedAt: (T) -> String?,
): List<T> = items.sortedByDescending { item ->
    finishedAt(item)?.let { value ->
        try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    } ?: Instant.MIN
}

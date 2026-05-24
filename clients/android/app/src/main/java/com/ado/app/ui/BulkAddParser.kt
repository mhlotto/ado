package com.ado.app.ui

data class BulkTaskDraft(
    val name: String,
    val description: String,
    val subtasks: List<BulkSubTaskDraft>,
)

data class BulkSubTaskDraft(
    val name: String,
    val description: String,
)

fun parseBulkTasks(text: String): List<BulkTaskDraft> {
    val entries = parseIndentedLines(text)
    val tasks = mutableListOf<BulkTaskDraftBuilder>()
    var currentTask: BulkTaskDraftBuilder? = null
    var currentSubTask: BulkSubTaskDraftBuilder? = null

    entries.forEach { entry ->
        if (entry.depth == 0 || currentTask == null) {
            currentTask = BulkTaskDraftBuilder(entry.text).also { tasks += it }
            currentSubTask = null
            return@forEach
        }

        if (entry.depth == 1) {
            val subTask = BulkSubTaskDraftBuilder(entry.text)
            currentTask?.subtasks?.add(subTask)
            currentSubTask = subTask
            return@forEach
        }

        if (currentSubTask != null) {
            currentSubTask?.appendDescription(entry.text)
        } else {
            currentTask?.appendDescription(entry.text)
        }
    }

    return tasks.map { it.toDraft() }
}

fun parseBulkSubTasks(text: String): List<BulkSubTaskDraft> {
    val entries = parseIndentedLines(text)
    val subtasks = mutableListOf<BulkSubTaskDraftBuilder>()
    var currentSubTask: BulkSubTaskDraftBuilder? = null

    entries.forEach { entry ->
        if (entry.depth == 0 || currentSubTask == null) {
            currentSubTask = BulkSubTaskDraftBuilder(entry.text).also { subtasks += it }
            return@forEach
        }

        currentSubTask?.appendDescription(entry.text)
    }

    return subtasks.map { it.toDraft() }
}

private data class IndentedLine(val depth: Int, val text: String)

private class BulkTaskDraftBuilder(val name: String) {
    var description: String = ""
    val subtasks = mutableListOf<BulkSubTaskDraftBuilder>()

    fun appendDescription(line: String) {
        description = appendDescriptionLine(description, line)
    }

    fun toDraft(): BulkTaskDraft = BulkTaskDraft(
        name = name,
        description = description,
        subtasks = subtasks.map { it.toDraft() },
    )
}

private class BulkSubTaskDraftBuilder(val name: String) {
    var description: String = ""

    fun appendDescription(line: String) {
        description = appendDescriptionLine(description, line)
    }

    fun toDraft(): BulkSubTaskDraft = BulkSubTaskDraft(name = name, description = description)
}

private fun parseIndentedLines(text: String): List<IndentedLine> {
    val rows = text
        .replace("\t", "  ")
        .lines()
        .mapNotNull { raw ->
            val indent = raw.takeWhile { it == ' ' }.length
            val value = stripListPrefix(raw.trim())
            if (value.isBlank()) null else indent to value
        }
    val indents = rows.map { it.first }.distinct().sorted()
    return rows.map { (indent, value) ->
        IndentedLine(depth = indents.indexOf(indent).coerceAtLeast(0), text = value)
    }
}

private fun stripListPrefix(value: String): String {
    return value
        .replace(Regex("""^[-*+\u2022]\s+\[[ xX]\]\s+"""), "")
        .replace(Regex("""^\[[ xX]\]\s+"""), "")
        .replace(Regex("""^[-*+\u2022]\s+"""), "")
        .replace(Regex("""^\d+[.)]\s+"""), "")
        .replace(Regex("""^[a-zA-Z][.)]\s+"""), "")
        .trim()
}

private fun appendDescriptionLine(current: String, line: String): String =
    if (current.isBlank()) line else "$current\n$line"

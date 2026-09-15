package dev.pacmon.jetbrains.core

enum class AgentProblemKind {
    UNKNOWN_KEY,
    EMPTY_VALUE,
    BAD_VALUE,
}

data class AgentProblem(
    val kind: AgentProblemKind,
    val line: Int,
    val key: String,
    val value: String = "",
    val expected: String? = null,
    val suggestion: String? = null,
) {
    val fixable: Boolean
        get() = kind == AgentProblemKind.UNKNOWN_KEY

    fun message(): String = when (kind) {
        AgentProblemKind.UNKNOWN_KEY -> suggestion?.let { "Line ${line + 1}: unknown field '$key'; use '$it'." }
            ?: "Line ${line + 1}: unknown field '$key'; it will be kept as 'note'."
        AgentProblemKind.EMPTY_VALUE -> "Line ${line + 1}: '$key' needs a value."
        AgentProblemKind.BAD_VALUE -> "Line ${line + 1}: '$key' must be ${expected.orEmpty()}."
    }
}

object AgentNotes {
    val fields = listOf(
        "purpose", "usage", "constraint", "verify", "log", "verified", "risk", "runtime",
        "exposure", "bump-with", "remove-when", "alternatives", "owner", "status", "links", "note",
    )

    internal val fieldSet = fields.toSet()
    internal val field = Regex("^(\\s*)[-*]\\s+([a-z][a-z0-9_-]*)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    internal val emptyValue = Regex("^[-\u2014\u2013]*$")
    private val status = Regex("^(dead|removal-planned|removed\\s+\\d{4}-\\d{2}(\\b.*)?)$", RegexOption.IGNORE_CASE)
    private val version = Regex("^v?\\d+(\\.\\d+)*", RegexOption.IGNORE_CASE)
    private val runtimeValues = setOf("server", "client", "build", "dev", "deploy")
    private val exposureValues = setOf("untrusted-input", "internal")

    fun lint(text: String): List<AgentProblem> = text.split(Regex("\\r?\\n")).mapIndexedNotNull { line, raw ->
        val match = field.matchEntire(raw) ?: return@mapIndexedNotNull null
        val key = match.groupValues[2].lowercase()
        val value = match.groupValues[3].trim()
        if (value.startsWith("//")) return@mapIndexedNotNull null
        if (key !in fieldSet) {
            return@mapIndexedNotNull AgentProblem(
                AgentProblemKind.UNKNOWN_KEY,
                line,
                key,
                value,
                suggestion = closest(key),
            )
        }
        if (emptyValue.matches(value)) {
            return@mapIndexedNotNull AgentProblem(AgentProblemKind.EMPTY_VALUE, line, key)
        }
        val expected = valueProblem(key, value) ?: return@mapIndexedNotNull null
        AgentProblem(AgentProblemKind.BAD_VALUE, line, key, value, expected)
    }

    fun fix(text: String): String {
        val lines = text.split(Regex("\\r?\\n")).toMutableList()
        lint(text).filter { it.kind == AgentProblemKind.UNKNOWN_KEY }.forEach { problem ->
            val raw = lines.getOrNull(problem.line) ?: return@forEach
            val match = field.matchEntire(raw) ?: return@forEach
            val indent = match.groupValues[1]
            val originalKey = match.groupValues[2]
            val value = match.groupValues[3]
            lines[problem.line] = if (problem.suggestion != null) {
                "$indent- ${problem.suggestion}: $value".trimEnd()
            } else {
                "$indent- note: $originalKey: $value".trimEnd()
            }
        }
        return lines.joinToString("\n")
    }

    internal fun valueProblem(key: String, value: String): String? = when (key) {
        "runtime" -> oneOf(value, runtimeValues, "one of server | client | build | dev | deploy")
        "exposure" -> oneOf(value, exposureValues, "one of untrusted-input | internal")
        "status" -> if (status.matches(value)) null else "dead | removal-planned | removed YYYY-MM - reason"
        "verified" -> if (version.containsMatchIn(value)) null else "a version, e.g. 4.18.2"
        else -> null
    }

    private fun oneOf(value: String, allowed: Set<String>, expected: String): String? {
        val values = value.lowercase().split(Regex("\\s*[,|/]\\s*"))
        return if (values.all { it in allowed }) null else expected
    }

    internal fun closest(value: String): String? {
        val normalized = value.lowercase().replace('_', '-')
        val best = fields.minByOrNull { levenshtein(normalized, it) } ?: return null
        val distance = levenshtein(normalized, best)
        val limit = minOf(2, maxOf(normalized.length, best.length) / 3)
        return best.takeIf { distance <= limit }
    }

    internal fun levenshtein(left: String, right: String): Int {
        val previous = IntArray(right.length + 1) { it }
        for (i in 1..left.length) {
            var diagonal = previous[0]
            previous[0] = i
            for (j in 1..right.length) {
                val old = previous[j]
                previous[j] = minOf(
                    old + 1,
                    previous[j - 1] + 1,
                    diagonal + if (left[i - 1] == right[j - 1]) 0 else 1,
                )
                diagonal = old
            }
        }
        return previous[right.length]
    }
}

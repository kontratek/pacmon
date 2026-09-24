package dev.pacmon.jetbrains.core

enum class ManifestKind(val id: String) {
    NPM("npm"),
    CARGO("cargo"),
    MAVEN("maven"),
    GRADLE("gradle"),
    MIX("mix"),
    ZIG("zig");

    companion object {
        fun fromId(value: String?): ManifestKind? = entries.firstOrNull { it.id == value }
    }
}

data class SourceRange(val offset: Int, val length: Int) {
    fun contains(position: Int): Boolean = position >= offset && position <= offset + length
}

data class DependencyEntry(
    val noteKey: String,
    val displayName: String,
    val scope: String,
    val primaryRange: SourceRange,
    val sourceRanges: List<SourceRange> = listOf(primaryRange),
    val iconRange: SourceRange = primaryRange,
)

interface ManifestAdapter {
    val kind: ManifestKind
    val fileNames: List<String>
    val notesRelativePath: String
    fun extractDependencies(text: String): List<DependencyEntry>
    fun normalizeNoteKey(raw: String): String
}

object ManifestRegistry {
    val adapters: List<ManifestAdapter> = listOf(
        NpmManifestAdapter,
        CargoManifestAdapter,
        MavenManifestAdapter,
        GradleManifestAdapter,
        MixManifestAdapter,
        ZigManifestAdapter,
    )

    fun forFileName(fileName: String): ManifestAdapter? = adapters.firstOrNull { fileName in it.fileNames }

    fun forKind(kind: ManifestKind): ManifestAdapter = adapters.first { it.kind == kind }

    fun dependencyAtOffset(dependencies: List<DependencyEntry>, offset: Int): DependencyEntry? =
        dependencies.firstOrNull { dependency -> dependency.sourceRanges.any { it.contains(offset) } }

    fun stripName(raw: String): String {
        var value = raw.trim()
        if (value.length >= 2 && value.first() == value.last() && value.first() in setOf('`', '\'', '"')) {
            value = value.substring(1, value.length - 1).trim()
        }
        return value
    }

    fun normalizeName(raw: String, ecosystem: ManifestKind?): String {
        val value = stripName(raw)
        return if (ecosystem != null && ecosystem != ManifestKind.NPM) value else value.lowercase()
    }
}

internal fun dependencyEntry(
    noteKey: String,
    scope: String,
    primaryRange: SourceRange,
    sourceRanges: List<SourceRange> = listOf(primaryRange),
    displayName: String = noteKey,
    iconRange: SourceRange = primaryRange,
) = DependencyEntry(noteKey, displayName, scope, primaryRange, sourceRanges, iconRange)

object NpmManifestAdapter : ManifestAdapter {
    override val kind = ManifestKind.NPM
    override val fileNames = listOf("package.json")
    override val notesRelativePath = ".pacmon/DEPENDENCY-NOTES.md"
    override fun normalizeNoteKey(raw: String): String = ManifestRegistry.stripName(raw).lowercase()

    private val dependencySections = setOf(
        "dependencies",
        "devDependencies",
        "peerDependencies",
        "optionalDependencies",
    )

    override fun extractDependencies(text: String): List<DependencyEntry> {
        val root = JsonReader(text).readValue() as? JsonObject ?: return emptyList()
        return root.properties.flatMap { section ->
            if (section.key !in dependencySections) return@flatMap emptyList()
            val dependencies = section.value as? JsonObject ?: return@flatMap emptyList()
            dependencies.properties.map { dependency ->
                dependencyEntry(dependency.key, section.key, dependency.keyRange)
            }
        }
    }
}

private sealed interface JsonValue
private data class JsonObject(val properties: List<JsonProperty>) : JsonValue
private data object JsonScalar : JsonValue
private data class JsonProperty(
    val key: String,
    val keyRange: SourceRange,
    val value: JsonValue,
)

/** Small JSONC reader: enough structure for package.json, with no runtime dependency. */
private class JsonReader(private val text: String) {
    private var cursor = 0

    fun readValue(): JsonValue? {
        skipTrivia()
        if (cursor >= text.length) return null
        return when (text[cursor]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()?.let { JsonScalar }
            else -> readPrimitive()
        }
    }

    private fun readObject(): JsonObject {
        cursor++
        val properties = mutableListOf<JsonProperty>()
        while (cursor < text.length) {
            skipTrivia()
            if (take('}')) break
            val key = readString()
            if (key == null) {
                recover(',', '}')
                if (take(',')) continue
                if (take('}')) break
                continue
            }
            skipTrivia()
            if (!take(':')) {
                recover(',', '}')
                if (take(',')) continue
                if (take('}')) break
                continue
            }
            val value = readValue() ?: JsonScalar
            properties.add(JsonProperty(key.value, key.range, value))
            skipTrivia()
            if (take(',')) continue
            if (take('}')) break
            recover(',', '}')
            if (take(',')) continue
            if (take('}')) break
        }
        return JsonObject(properties)
    }

    private fun readArray(): JsonValue {
        cursor++
        while (cursor < text.length) {
            skipTrivia()
            if (take(']')) break
            readValue() ?: run { cursor++ }
            skipTrivia()
            if (take(',')) continue
            if (take(']')) break
        }
        return JsonScalar
    }

    private data class JsonString(val value: String, val range: SourceRange)

    private fun readString(): JsonString? {
        skipTrivia()
        if (cursor >= text.length || text[cursor] != '"') return null
        val start = cursor++
        val value = StringBuilder()
        while (cursor < text.length) {
            val ch = text[cursor++]
            if (ch == '"') return JsonString(value.toString(), SourceRange(start, cursor - start))
            if (ch != '\\') {
                value.append(ch)
                continue
            }
            if (cursor >= text.length) break
            when (val escaped = text[cursor++]) {
                '"', '\\', '/' -> value.append(escaped)
                'b' -> value.append('\b')
                'f' -> value.append('\u000C')
                'n' -> value.append('\n')
                'r' -> value.append('\r')
                't' -> value.append('\t')
                'u' -> {
                    val end = (cursor + 4).coerceAtMost(text.length)
                    val digits = text.substring(cursor, end)
                    val code = digits.toIntOrNull(16)
                    if (digits.length == 4 && code != null) {
                        value.append(code.toChar())
                        cursor = end
                    } else {
                        value.append('u').append(digits)
                        cursor = end
                    }
                }
                else -> value.append(escaped)
            }
        }
        return null
    }

    private fun readPrimitive(): JsonValue {
        while (cursor < text.length && text[cursor] !in charArrayOf(',', '}', ']') && !text[cursor].isWhitespace()) cursor++
        return JsonScalar
    }

    private fun skipTrivia() {
        while (cursor < text.length) {
            if (text[cursor].isWhitespace()) {
                cursor++
                continue
            }
            if (text.startsWith("//", cursor)) {
                cursor += 2
                while (cursor < text.length && text[cursor] != '\n') cursor++
                continue
            }
            if (text.startsWith("/*", cursor)) {
                val end = text.indexOf("*/", cursor + 2)
                cursor = if (end < 0) text.length else end + 2
                continue
            }
            break
        }
    }

    private fun recover(vararg wanted: Char) {
        while (cursor < text.length && text[cursor] !in wanted) cursor++
    }

    private fun take(ch: Char): Boolean {
        if (cursor >= text.length || text[cursor] != ch) return false
        cursor++
        return true
    }
}

object CargoManifestAdapter : ManifestAdapter {
    override val kind = ManifestKind.CARGO
    override val fileNames = listOf("Cargo.toml")
    override val notesRelativePath = ".pacmon/cargo/DEPENDENCY-NOTES.md"
    override fun normalizeNoteKey(raw: String): String = ManifestRegistry.stripName(raw)

    private data class KeyPart(val value: String, val range: SourceRange)
    private data class TableContext(val scope: String, val dependencyIndex: Int?)
    private val dependencyTables = setOf("dependencies", "dev-dependencies", "build-dependencies")

    override fun extractDependencies(text: String): List<DependencyEntry> {
        val out = mutableListOf<DependencyEntry>()
        val seen = mutableSetOf<String>()
        var activeScope: String? = null
        var continuationDepth = 0

        fun add(part: KeyPart, scope: String) {
            if (!seen.add("$scope\u0000${part.value}")) return
            out.add(dependencyEntry(part.value, scope, part.range))
        }

        for ((line, lineOffset) in splitLines(text)) {
            val content = contentBeforeComment(line)
            if (continuationDepth > 0) {
                continuationDepth = (continuationDepth + nestingDelta(content)).coerceAtLeast(0)
                continue
            }
            val header = tableHeader(content)
            if (header == null && content.trimStart().startsWith('[')) {
                activeScope = null
                continue
            }
            if (header != null) {
                val parts = keyParts(header.first, lineOffset + header.second)
                val context = parts?.let(::tableScope)
                activeScope = if (context?.dependencyIndex == null) context?.scope else null
                if (parts != null && context?.dependencyIndex != null) {
                    parts.getOrNull(context.dependencyIndex)?.let { add(it, context.scope) }
                }
                continue
            }
            val scope = activeScope ?: continue
            val equals = unquotedIndex(content, '=')
            if (equals < 0) continue
            val rawKey = content.substring(0, equals)
            val leading = rawKey.length - rawKey.trimStart().length
            keyParts(rawKey.trim(), lineOffset + leading)?.firstOrNull()?.let { add(it, scope) }
            continuationDepth = nestingDelta(content.substring(equals + 1)).coerceAtLeast(0)
        }
        return out
    }

    private fun splitLines(text: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var start = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val rawEnd = if (newline < 0) text.length else newline
            val end = if (rawEnd > start && text[rawEnd - 1] == '\r') rawEnd - 1 else rawEnd
            result.add(text.substring(start, end) to start)
            if (newline < 0) break
            start = newline + 1
        }
        return result
    }

    private fun contentBeforeComment(line: String): String {
        var quote: Char? = null
        var escaped = false
        line.forEachIndexed { index, ch ->
            if (quote == '"' && escaped) {
                escaped = false
            } else if (quote == '"' && ch == '\\') {
                escaped = true
            } else if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '#') {
                return line.substring(0, index)
            }
        }
        return line
    }

    private fun decodeKey(raw: String): String {
        if (raw.length < 2) return raw
        if (raw.first() == '\'' && raw.last() == '\'') return raw.substring(1, raw.length - 1)
        if (raw.first() != '"' || raw.last() != '"') return raw
        return decodeBasicString(raw.substring(1, raw.length - 1))
    }

    private fun decodeBasicString(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val ch = value[i++]
            if (ch != '\\' || i >= value.length) append(ch)
            else when (val escaped = value[i++]) {
                '"', '\\', '/' -> append(escaped)
                'b' -> append('\b')
                'f' -> append('\u000C')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'u' -> {
                    val end = (i + 4).coerceAtMost(value.length)
                    val digits = value.substring(i, end)
                    val code = digits.toIntOrNull(16)
                    if (digits.length == 4 && code != null) {
                        append(code.toChar())
                        i = end
                    } else {
                        append('u').append(digits)
                        i = end
                    }
                }
                else -> append(escaped)
            }
        }
    }

    private fun keyParts(raw: String, absoluteOffset: Int): List<KeyPart>? {
        val out = mutableListOf<KeyPart>()
        var start = 0
        var quote: Char? = null
        var escaped = false

        fun push(end: Int): Boolean {
            val segment = raw.substring(start, end)
            val leading = segment.length - segment.trimStart().length
            val token = segment.trim()
            if (token.isEmpty()) return false
            if ((token.startsWith('"') && !token.endsWith('"')) ||
                (token.startsWith('\'') && !token.endsWith('\''))
            ) return false
            out.add(KeyPart(decodeKey(token), SourceRange(absoluteOffset + start + leading, token.length)))
            return true
        }

        raw.forEachIndexed { index, ch ->
            if (quote == '"' && escaped) {
                escaped = false
            } else if (quote == '"' && ch == '\\') {
                escaped = true
            } else if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '.') {
                if (!push(index)) return null
                start = index + 1
            }
        }
        if (quote != null || !push(raw.length)) return null
        return out
    }

    private fun unquotedIndex(raw: String, wanted: Char): Int {
        var quote: Char? = null
        var escaped = false
        raw.forEachIndexed { index, ch ->
            if (quote == '"' && escaped) escaped = false
            else if (quote == '"' && ch == '\\') escaped = true
            else if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') quote = ch
            else if (ch == wanted) return index
        }
        return -1
    }

    private fun nestingDelta(raw: String): Int {
        var delta = 0
        var quote: Char? = null
        var escaped = false
        for (ch in raw) {
            if (quote == '"' && escaped) escaped = false
            else if (quote == '"' && ch == '\\') escaped = true
            else if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') quote = ch
            else if (ch == '{' || ch == '[') delta++
            else if (ch == '}' || ch == ']') delta--
        }
        return delta
    }

    private fun tableHeader(line: String): Pair<String, Int>? {
        val leading = line.length - line.trimStart().length
        if (line.getOrNull(leading) != '[' || line.getOrNull(leading + 1) == '[') return null
        var quote: Char? = null
        var escaped = false
        for (index in leading + 1 until line.length) {
            val ch = line[index]
            if (quote == '"' && escaped) escaped = false
            else if (quote == '"' && ch == '\\') escaped = true
            else if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') quote = ch
            else if (ch == ']') return line.substring(leading + 1, index) to leading + 1
        }
        return null
    }

    private fun tableScope(parts: List<KeyPart>): TableContext? {
        if (parts.isEmpty() || parts.first().value == "workspace") return null
        val sectionIndex = when {
            parts.first().value in dependencyTables -> 0
            parts.first().value == "target" -> parts.indexOfFirstIndexed { index, part ->
                index >= 2 && part.value in dependencyTables
            }
            else -> -1
        }
        if (sectionIndex < 0) return null
        val section = parts[sectionIndex].value
        val scope = if (sectionIndex == 0) section else {
            "target:${parts.subList(1, sectionIndex).joinToString(".") { it.value }}/$section"
        }
        return TableContext(scope, if (parts.size > sectionIndex + 1) sectionIndex + 1 else null)
    }

    private inline fun <T> List<T>.indexOfFirstIndexed(predicate: (Int, T) -> Boolean): Int {
        forEachIndexed { index, value -> if (predicate(index, value)) return index }
        return -1
    }
}

object MavenManifestAdapter : ManifestAdapter {
    override val kind = ManifestKind.MAVEN
    override val fileNames = listOf("pom.xml")
    override val notesRelativePath = ".pacmon/maven/DEPENDENCY-NOTES.md"
    override fun normalizeNoteKey(raw: String): String = ManifestRegistry.stripName(raw)

    private data class XmlNode(
        val name: String,
        val openStart: Int,
        val openEnd: Int,
        var closeStart: Int,
        val children: MutableList<XmlNode> = mutableListOf(),
    )

    override fun extractDependencies(text: String): List<DependencyEntry> {
        val project = parseXml(text).firstOrNull { it.name == "project" } ?: return emptyList()
        val out = dependenciesFrom(text, child(project, "dependencies")).toMutableList()
        child(project, "profiles")?.children?.filter { it.name == "profile" }?.forEach { profile ->
            val id = valueAndRange(text, child(profile, "id"))?.first ?: "unnamed"
            out.addAll(dependenciesFrom(text, child(profile, "dependencies"), "profile:$id"))
        }
        return out
    }

    private fun localName(name: String): String = name.substringAfterLast(':')

    private fun parseXml(text: String): List<XmlNode> {
        val roots = mutableListOf<XmlNode>()
        val stack = mutableListOf<XmlNode>()
        var cursor = 0
        while (cursor < text.length) {
            val open = text.indexOf('<', cursor)
            if (open < 0) break
            val marker = when {
                text.startsWith("<!--", open) -> "-->" to 4
                text.startsWith("<![CDATA[", open) -> "]]>" to 9
                text.startsWith("<?", open) -> "?>" to 2
                text.startsWith("<!", open) -> ">" to 2
                else -> null
            }
            if (marker != null) {
                val end = text.indexOf(marker.first, open + marker.second)
                cursor = if (end < 0) text.length else end + marker.first.length
                continue
            }

            var quote: Char? = null
            var end = open + 1
            while (end < text.length) {
                val ch = text[end]
                if (quote != null) {
                    if (ch == quote) quote = null
                } else if (ch == '"' || ch == '\'') quote = ch
                else if (ch == '>') break
                end++
            }
            if (end >= text.length) break
            val inside = text.substring(open + 1, end).trim()
            if (inside.startsWith('/')) {
                val name = localName(firstToken(inside.drop(1).trim()))
                for (index in stack.indices.reversed()) {
                    if (stack[index].name != name) continue
                    stack[index].closeStart = open
                    while (stack.size > index) stack.removeAt(stack.lastIndex)
                    break
                }
            } else {
                val selfClosing = Regex("/\\s*$").containsMatchIn(inside)
                val rawName = firstToken(inside.replace(Regex("/\\s*$"), ""))
                if (rawName.isNotEmpty()) {
                    val node = XmlNode(localName(rawName), open, end + 1, end + 1)
                    stack.lastOrNull()?.children?.add(node) ?: roots.add(node)
                    if (!selfClosing) stack.add(node)
                }
            }
            cursor = end + 1
        }
        stack.forEach { it.closeStart = text.length }
        return roots
    }

    private fun child(node: XmlNode, name: String): XmlNode? = node.children.firstOrNull { it.name == name }

    private fun firstToken(value: String): String = value.takeWhile { !it.isWhitespace() }

    private fun valueAndRange(text: String, node: XmlNode?): Pair<String, SourceRange>? {
        node ?: return null
        val raw = text.substring(node.openEnd.coerceAtMost(text.length), node.closeStart.coerceAtMost(text.length))
        val plain = raw.replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")) { it.groupValues[1] }
        val trimmed = plain.trim()
        val value = decodeXml(trimmed)
        if (value.isEmpty()) return null
        val relative = raw.indexOf(trimmed).coerceAtLeast(0)
        return value to SourceRange(node.openEnd + relative, trimmed.length)
    }

    private fun decodeXml(value: String): String = Regex("&(#x[\\da-f]+|#\\d+|amp|lt|gt|quot|apos);", RegexOption.IGNORE_CASE)
        .replace(value) { match ->
            val body = match.groupValues[1].lowercase()
            when (body) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val radix = if (body.startsWith("#x")) 16 else 10
                    val digits = body.drop(if (radix == 16) 2 else 1)
                    digits.toIntOrNull(radix)?.let { Character.toChars(it).concatToString() } ?: match.value
                }
            }
        }

    private fun dependenciesFrom(text: String, dependencies: XmlNode?, scopePrefix: String? = null): List<DependencyEntry> {
        dependencies ?: return emptyList()
        return dependencies.children.filter { it.name == "dependency" }.mapNotNull { dependency ->
            val group = valueAndRange(text, child(dependency, "groupId")) ?: return@mapNotNull null
            val artifact = valueAndRange(text, child(dependency, "artifactId")) ?: return@mapNotNull null
            val declaredScope = valueAndRange(text, child(dependency, "scope"))?.first ?: "compile"
            val scope = if (scopePrefix == null) declaredScope else "$scopePrefix/$declaredScope"
            dependencyEntry(
                "${group.first}:${artifact.first}",
                scope,
                artifact.second,
                listOf(group.second, artifact.second),
                iconRange = SourceRange(dependency.openStart, 1),
            )
        }
    }
}

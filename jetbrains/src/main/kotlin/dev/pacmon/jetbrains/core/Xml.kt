package dev.pacmon.jetbrains.core

internal data class XmlAttribute(
    val name: String,
    val value: String,
    val range: SourceRange,
)

internal data class XmlNode(
    val name: String,
    val openStart: Int,
    val openEnd: Int,
    var closeStart: Int,
    val attributes: List<XmlAttribute>,
    val children: MutableList<XmlNode> = mutableListOf(),
)

private fun localXmlName(name: String): String = name.substringAfterLast(':')

internal fun decodeXml(value: String): String =
    Regex("&(#x[\\da-f]+|#\\d+|amp|lt|gt|quot|apos);", RegexOption.IGNORE_CASE).replace(value) { match ->
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
                val codePoint = digits.toIntOrNull(radix)
                if (codePoint == null || !Character.isValidCodePoint(codePoint)) {
                    match.value
                } else {
                    runCatching { Character.toChars(codePoint).concatToString() }.getOrDefault(match.value)
                }
            }
        }
    }

private fun attributesIn(text: String, start: Int, end: Int): List<XmlAttribute> {
    val attributes = mutableListOf<XmlAttribute>()
    var cursor = start
    while (cursor < end) {
        while (cursor < end && text[cursor].isWhitespace()) cursor++
        if (cursor >= end || text[cursor] == '/') break
        val nameStart = cursor
        while (cursor < end && !text[cursor].isWhitespace() && text[cursor] !in setOf('=', '/')) cursor++
        val rawName = text.substring(nameStart, cursor)
        while (cursor < end && text[cursor].isWhitespace()) cursor++
        if (rawName.isEmpty() || cursor >= end || text[cursor] != '=') {
            while (cursor < end && !text[cursor].isWhitespace() && text[cursor] != '/') cursor++
            continue
        }
        cursor++
        while (cursor < end && text[cursor].isWhitespace()) cursor++
        if (cursor >= end) break
        val quote = text[cursor]
        if (quote != '\'' && quote != '"') continue
        cursor++
        val valueStart = cursor
        while (cursor < end && text[cursor] != quote) cursor++
        if (cursor >= end) break
        val rawValue = text.substring(valueStart, cursor)
        val leading = rawValue.length - rawValue.trimStart().length
        val trimmed = rawValue.trim()
        attributes.add(
            XmlAttribute(
                localXmlName(rawName),
                decodeXml(trimmed),
                SourceRange(valueStart + leading, trimmed.length),
            ),
        )
        cursor++
    }
    return attributes
}

/** Small, tolerant XML reader used by static manifest adapters. */
internal fun parseXml(text: String): List<XmlNode> {
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
            val found = text.indexOf(marker.first, open + marker.second)
            cursor = if (found < 0) text.length else found + marker.first.length
            continue
        }

        var quote: Char? = null
        var end = open + 1
        while (end < text.length) {
            val ch = text[end]
            if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '\'' || ch == '"') {
                quote = ch
            } else if (ch == '>') {
                break
            }
            end++
        }
        if (end >= text.length) break

        var contentStart = open + 1
        while (contentStart < end && text[contentStart].isWhitespace()) contentStart++
        if (contentStart < end && text[contentStart] == '/') {
            contentStart++
            while (contentStart < end && text[contentStart].isWhitespace()) contentStart++
            var nameEnd = contentStart
            while (nameEnd < end && !text[nameEnd].isWhitespace()) nameEnd++
            val name = localXmlName(text.substring(contentStart, nameEnd))
            for (index in stack.indices.reversed()) {
                if (stack[index].name != name) continue
                stack[index].closeStart = open
                while (stack.size > index) stack.removeAt(stack.lastIndex)
                break
            }
        } else {
            var nameEnd = contentStart
            while (nameEnd < end && !text[nameEnd].isWhitespace() && text[nameEnd] != '/') nameEnd++
            val rawName = text.substring(contentStart, nameEnd)
            if (rawName.isNotEmpty()) {
                val selfClosing = Regex("/\\s*$").containsMatchIn(text.substring(nameEnd, end))
                val node = XmlNode(
                    localXmlName(rawName),
                    open,
                    end + 1,
                    end + 1,
                    attributesIn(text, nameEnd, end),
                )
                stack.lastOrNull()?.children?.add(node) ?: roots.add(node)
                if (!selfClosing) stack.add(node)
            }
        }
        cursor = end + 1
    }
    stack.forEach { it.closeStart = text.length }
    return roots
}

internal fun xmlChild(node: XmlNode, name: String): XmlNode? = node.children.firstOrNull { it.name == name }

internal fun xmlAttribute(node: XmlNode, name: String): XmlAttribute? =
    node.attributes.firstOrNull { it.name == name }

internal fun xmlTextValue(text: String, node: XmlNode?): Pair<String, SourceRange>? {
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

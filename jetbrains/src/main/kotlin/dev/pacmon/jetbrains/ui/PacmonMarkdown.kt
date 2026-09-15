package dev.pacmon.jetbrains.ui

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object PacmonMarkdown {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder()
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()
    private val image = Regex("<img\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val codeBackground = JBColor(0xF0F0F0, 0x333537)
    private val codeBorder = JBColor(0xD9D9D9, 0x4A4D4E)

    fun render(markdown: String, emptyText: String): String {
        val body = if (markdown.isBlank()) {
            "<p><i>${escape(emptyText)}</i></p>"
        } else {
            image.replace(renderer.render(parser.parse(markdown)), "")
        }
        val codeBg = ColorUtil.toHtmlColor(codeBackground)
        val codeBorderColor = ColorUtil.toHtmlColor(codeBorder)
        return """
            <html><head><style>
            body { font-family: sans-serif; margin: 6px; }
            p { margin: 0 0 7px 0; }
            pre { background-color: $codeBg; border: 1px solid $codeBorderColor; padding: 8px; margin: 4px 0; }
            code { font-family: monospace; background-color: $codeBg; padding: 1px 4px; }
            </style></head><body>$body</body></html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

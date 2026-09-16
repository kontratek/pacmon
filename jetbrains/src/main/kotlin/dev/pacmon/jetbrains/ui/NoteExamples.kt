package dev.pacmon.jetbrains.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import dev.pacmon.jetbrains.editor.PacmonIcons
import dev.pacmon.jetbrains.settings.Decorations
import dev.pacmon.jetbrains.settings.NoteButtons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A sample `package.json` line showing what each choice actually does to it.
 *
 * This is the one thing the VS Code view is a webview for rather than a tree:
 * a label and a help line can describe a mark before the package name, but
 * they cannot show you one. The samples here are the Swing answer to that —
 * drawn with the editor's own font and colors, so the mark, the underline and
 * the line above each look like the thing they stand for.
 *
 * VS Code draws them for the click targets only. The note markers get one here
 * too, because "badge" is the choice nobody can picture from its name: it is
 * not the bare marker but the marker and the word `note`, which is exactly the
 * kind of thing a sample settles and a sentence does not.
 *
 * The text is fixed and invented — `express` and a version range — so nothing
 * from the open project reaches the panel.
 */
internal object NoteExamples {
    private const val NAME = "\"express\""
    private const val REST = ": \"^4.18.0\","
    private const val EDIT = "Edit note"

    fun forClickTarget(id: String): JComponent = when (id) {
        NoteButtons.ICON_LEFT -> frame(
            codeLine().apply {
                icon = PacmonIcons.Documented
                append(NAME)
                append(REST, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            },
        )

        NoteButtons.LINK -> frame(
            codeLine().apply {
                append(NAME, linkAttributes())
                append(REST, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            },
        )

        NoteButtons.CODE_LENS -> frame(
            stacked(
                codeLine().apply { append(EDIT, smallLinkAttributes()) },
                codeLine().apply {
                    append(NAME)
                    append(REST, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                },
            ),
        )

        NoteButtons.INLAY_HINT -> frame(
            codeLine().apply {
                append(NAME)
                append(REST, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append("  $EDIT  ", chipAttributes())
            },
        )

        else -> frame(
            codeLine().apply {
                icon = AllIcons.Actions.IntentionBulb
                append(NAME)
                append(REST, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            },
        )
    }

    fun forMarker(value: String): JComponent = frame(
        codeLine().apply {
            append(NAME)
            append(REST, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            when (value) {
                Decorations.PREVIEW ->
                    append("   ▪ Fast, minimal HTTP server", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                Decorations.BADGE ->
                    append("   ▪ note", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        },
    )

    /** One line of the sample, in the editor's font so it reads as code. */
    private fun codeLine(): SimpleColoredComponent = SimpleColoredComponent().apply {
        val scheme = EditorColorsManager.getInstance().globalScheme
        font = Font(scheme.editorFontName, Font.PLAIN, scheme.editorFontSize)
        isOpaque = false
        border = JBUI.Borders.empty()
        ipad = JBUI.emptyInsets()
        myBorder = JBUI.Borders.empty()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun stacked(vararg lines: JComponent): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        for (line in lines) add(line)
    }

    /** The editor-colored box the sample sits in, so it reads as a quotation
     *  from `package.json` and not as more of the settings page. */
    private fun frame(content: JComponent): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border()),
            JBUI.Borders.empty(4, 6),
        )
        add(content, BorderLayout.CENTER)
    }

    private fun linkAttributes() = SimpleTextAttributes(
        SimpleTextAttributes.STYLE_UNDERLINE,
        JBUI.CurrentTheme.Link.Foreground.ENABLED,
    )

    private fun smallLinkAttributes() = SimpleTextAttributes(
        SimpleTextAttributes.STYLE_SMALLER,
        JBUI.CurrentTheme.Link.Foreground.ENABLED,
    )

    /** The end-of-line chip, in the colors the editor gives a real inlay hint. */
    private fun chipAttributes(): SimpleTextAttributes {
        val hint = EditorColorsManager.getInstance().globalScheme
            .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
        return SimpleTextAttributes(
            hint?.backgroundColor ?: JBColor(0xE6E6E6, 0x3B3B3B),
            hint?.foregroundColor ?: JBColor(0x6F737A, 0xA0A0A0),
            null,
            SimpleTextAttributes.STYLE_SMALLER,
        )
    }
}

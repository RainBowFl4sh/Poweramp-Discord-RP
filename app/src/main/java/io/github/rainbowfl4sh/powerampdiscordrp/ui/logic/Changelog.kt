package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

/** A piece of text, bold or not (Markdown **...**). */
data class Span(val text: String, val bold: Boolean = false)

/** One block of the changelog as the About tab draws it. */
sealed interface ChangelogBlock {
    /** "## 1.0.0" */
    data class Version(val version: String) : ChangelogBlock

    /** "### New" */
    data class Heading(val text: String) : ChangelogBlock

    /** "- text" (nested: indented by at least two spaces) */
    data class Item(val spans: List<Span>, val nested: Boolean = false) : ChangelogBlock

    data class Paragraph(val spans: List<Span>) : ChangelogBlock
}

/**
 * The small Markdown subset of CHANGELOG.md (like the AIMP plugin's About tab): version headings, section headings,
 * list items (also continued on the next lines), paragraphs, **bold**; `code` and *italics* lose their markers.
 */
object Changelog {
    fun parse(markdown: String): List<ChangelogBlock> {
        val blocks = mutableListOf<ChangelogBlock>()
        var text: StringBuilder? = null   // item / paragraph being collected
        var nested = false
        var isItem = false

        fun flush() {
            val t = text ?: return
            text = null
            val spans = spans(t.toString())
            if (spans.isEmpty()) return
            blocks += if (isItem) ChangelogBlock.Item(spans, nested) else ChangelogBlock.Paragraph(spans)
        }

        for (raw in markdown.lineSequence()) {
            val line = raw.trimEnd('\r', ' ', '\t')
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            when {
                trimmed.isEmpty() -> flush()
                trimmed.startsWith("### ") -> {
                    flush()
                    blocks += ChangelogBlock.Heading(plain(trimmed.substring(4)))
                }
                trimmed.startsWith("## ") -> {
                    flush()
                    blocks += ChangelogBlock.Version(plain(trimmed.substring(3)))
                }
                trimmed.startsWith("# ") -> flush()   // the document title
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flush()
                    text = StringBuilder(trimmed.substring(2).trim())
                    isItem = true
                    nested = indent >= 2
                }
                else -> {
                    val current = text
                    if (current != null) {
                        current.append(' ').append(trimmed)   // continuation line
                    } else {
                        text = StringBuilder(trimmed)
                        isItem = false
                        nested = false
                    }
                }
            }
        }
        flush()
        return blocks
    }

    /** True if a version heading ("1.0.0", "v1.0.0 - 2026-10-06") is about [version] (the installed one). */
    fun headingIsVersion(heading: String, version: String): Boolean {
        val found = VERSION.find(heading)?.value ?: return false
        return found == version.trim().removePrefix("v").substringBefore('-').substringBefore(' ')
    }

    private val VERSION = Regex("""\d+(\.\d+)+""")

    /** Splits **bold** parts off; other markers are dropped. Unbalanced ** stay plain text. */
    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        var rest = text
        while (rest.isNotEmpty()) {
            val start = rest.indexOf("**")
            val end = if (start >= 0) rest.indexOf("**", start + 2) else -1
            if (start < 0 || end < 0) {
                add(out, plain(rest), false)
                break
            }
            add(out, plain(rest.substring(0, start)), false)
            add(out, plain(rest.substring(start + 2, end)), true)
            rest = rest.substring(end + 2)
        }
        return out
    }

    private fun add(out: MutableList<Span>, text: String, bold: Boolean) {
        if (text.isEmpty()) return
        val last = out.lastOrNull()
        if (last != null && last.bold == bold) out[out.size - 1] = Span(last.text + text, bold) else out += Span(text, bold)
    }

    /** Removes inline Markdown that is not drawn: `code` backticks and single * / _ emphasis around words. */
    private fun plain(text: String): String =
        text.replace("`", "")
            .replace(Regex("""(?<![\w*])\*(?!\s)([^*]+?)(?<!\s)\*(?![\w*])"""), "$1")
            .replace(Regex("""(?<!\w)_(?!\s)([^_]+?)(?<!\s)_(?!\w)"""), "$1")
}

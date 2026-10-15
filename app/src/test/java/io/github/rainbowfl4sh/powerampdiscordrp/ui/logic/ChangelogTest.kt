package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {
    @Test
    fun parsesVersionsSectionsAndItems() {
        val md = """
            # Changelog

            ## 1.1.0

            ### New
            - **Bold** start and `code`
              continued on the next line
              - nested item
            - *emphasis* stays text

            Some paragraph
            over two lines.

            ## 1.0.0
            - first
        """.trimIndent()
        val blocks = Changelog.parse(md)
        assertEquals(
            listOf(
                ChangelogBlock.Version("1.1.0"),
                ChangelogBlock.Heading("New"),
                ChangelogBlock.Item(listOf(Span("Bold", bold = true), Span(" start and code continued on the next line"))),
                ChangelogBlock.Item(listOf(Span("nested item")), nested = true),
                ChangelogBlock.Item(listOf(Span("emphasis stays text"))),
                ChangelogBlock.Paragraph(listOf(Span("Some paragraph over two lines."))),
                ChangelogBlock.Version("1.0.0"),
                ChangelogBlock.Item(listOf(Span("first"))),
            ),
            blocks,
        )
    }

    @Test
    fun boldSpans() {
        assertEquals(listOf(Span("a "), Span("b", true), Span(" c "), Span("d", true)), Changelog.spans("a **b** c **d**"))
        assertEquals(listOf(Span("a ** b")), Changelog.spans("a ** b"))
        assertEquals(listOf(Span("%title% and 5 * 3")), Changelog.spans("`%title%` and 5 * 3"))
        assertEquals(emptyList<Span>(), Changelog.spans(""))
    }

    @Test
    fun windowsLineEndings() {
        val blocks = Changelog.parse("## 2.0.0\r\n- a\r\n")
        assertEquals(listOf(ChangelogBlock.Version("2.0.0"), ChangelogBlock.Item(listOf(Span("a")))), blocks)
    }

    @Test
    fun installedVersion() {
        assertTrue(Changelog.headingIsVersion("1.0.0", "1.0.0"))
        assertTrue(Changelog.headingIsVersion("v1.0.0 - 2026-10-06", "1.0.0"))
        assertTrue(Changelog.headingIsVersion("1.0.0", "1.0.0-beta"))
        assertFalse(Changelog.headingIsVersion("1.0.1", "1.0.0"))
        assertFalse(Changelog.headingIsVersion("1.0.0", "1.0"))
        assertFalse(Changelog.headingIsVersion("Unreleased", "1.0.0"))
    }

    /** The changelog shown on the About tab, in every language: starts with the version of this build. */
    @Test
    fun bundledChangelogsDescribeThisVersion() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        for (folder in listOf("raw", "raw-de", "raw-ru", "raw-uk")) {
            val blocks = Changelog.parse(File(res, "$folder/changelog.md").readText())
            val first = blocks.first()
            assertTrue("$folder: $first", first is ChangelogBlock.Version)
            assertTrue(folder, Changelog.headingIsVersion((first as ChangelogBlock.Version).version, BuildConfig.VERSION_NAME))
            assertTrue(folder, blocks.count { it is ChangelogBlock.Item } >= 5)
        }
    }
}

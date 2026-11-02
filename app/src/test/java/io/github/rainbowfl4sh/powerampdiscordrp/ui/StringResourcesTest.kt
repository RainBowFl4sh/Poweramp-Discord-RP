package io.github.rainbowfl4sh.powerampdiscordrp.ui

import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The texts (strings.xml and strings_runtime.xml) are complete in every language of [AppLanguages] and use the same
 * format arguments everywhere.
 */
class StringResourcesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** key -> text of one strings file (only translatable texts). */
    private fun load(folder: String, file: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/$file"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val files = listOf("strings.xml", "strings_runtime.xml")
    private val english = files.associateWith { load("values", it) }
    private val translations = AppLanguages.ALL.filter { it.qualifier.isNotEmpty() }.map { it.valuesFolder }

    /** Shown only for the AI-translated languages, empty elsewhere. */
    private val optional = setOf("about_translation_note")

    /** Every translation of every file: (folder/file, its texts, the English texts). */
    private fun eachTranslation(check: (where: String, texts: Map<String, String>, english: Map<String, String>) -> Unit) {
        for (folder in translations) for (file in files) check("$folder/$file", load(folder, file), english.getValue(file))
    }

    @Test
    fun everyLanguageHasEveryText() {
        assertTrue(english.getValue("strings.xml").size > 200)
        assertTrue(english.getValue("strings_runtime.xml").size > 10)
        eachTranslation { where, texts, english ->
            assertEquals("$where: missing", emptySet<String>(), english.keys - texts.keys)
            assertEquals("$where: unknown", emptySet<String>(), texts.keys - english.keys)
            texts.forEach { (key, text) -> assertTrue("$where/$key is empty", text.isNotBlank() || key in optional) }
        }
        english.values.forEach { texts ->
            texts.forEach { (key, text) -> assertTrue("$key is empty", text.isNotBlank() || key in optional) }
        }
    }

    @Test
    fun formatArgumentsMatch() {
        val arg = Regex("""%(\d+\$)?[sd]""")
        eachTranslation { where, texts, english ->
            english.forEach { (key, text) ->
                if (key in optional) return@forEach
                val expected = arg.findAll(text).map { it.value }.toSortedSet()
                val actual = arg.findAll(texts[key] ?: return@forEach).map { it.value }.toSortedSet()
                assertEquals("$where/$key", expected, actual)
            }
        }
    }

    @Test
    fun appNameIsKept() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        val appName = (0 until nodes.length).map { nodes.item(it) as Element }.single { it.getAttribute("name") == "app_name" }
        assertEquals("Poweramp Discord RP", appName.textContent)
    }

    /** The background part keeps its texts in strings_runtime.xml (svc_*, status_*): no name may be used twice. */
    @Test
    fun noNamesOfTheRuntimeTexts() {
        val clashes = english.getValue("strings.xml").keys.filter { it.startsWith("svc_") || it.startsWith("status_") }
        assertEquals(emptyList<String>(), clashes)
    }
}

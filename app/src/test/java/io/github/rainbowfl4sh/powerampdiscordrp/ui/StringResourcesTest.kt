package io.github.rainbowfl4sh.powerampdiscordrp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The UI texts are complete in all four languages and use the same format arguments everywhere. */
class StringResourcesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** key -> text of one strings.xml (only translatable texts). */
    private fun load(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val english = load("values")
    private val translations = listOf("values-de", "values-ru", "values-uk").associateWith { load(it) }

    /** Shown only for the AI-translated languages, empty elsewhere. */
    private val optional = setOf("about_translation_note")

    @Test
    fun everyLanguageHasEveryText() {
        assertTrue(english.size > 200)
        translations.forEach { (folder, texts) ->
            assertEquals("$folder: missing", emptySet<String>(), english.keys - texts.keys)
            assertEquals("$folder: unknown", emptySet<String>(), texts.keys - english.keys)
            texts.forEach { (key, text) -> assertTrue("$folder/$key is empty", text.isNotBlank() || key in optional) }
        }
        english.forEach { (key, text) -> assertTrue("$key is empty", text.isNotBlank() || key in optional) }
    }

    @Test
    fun formatArgumentsMatch() {
        val arg = Regex("""%(\d+\$)?[sd]""")
        english.forEach { (key, text) ->
            val expected = arg.findAll(text).map { it.value }.toSortedSet()
            translations.forEach { (folder, texts) ->
                if (key in optional) return@forEach
                val actual = arg.findAll(texts.getValue(key)).map { it.value }.toSortedSet()
                assertEquals("$folder/$key", expected, actual)
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
        val clashes = english.keys.filter { it.startsWith("svc_") || it.startsWith("status_") }
        assertEquals(emptyList<String>(), clashes)
    }
}

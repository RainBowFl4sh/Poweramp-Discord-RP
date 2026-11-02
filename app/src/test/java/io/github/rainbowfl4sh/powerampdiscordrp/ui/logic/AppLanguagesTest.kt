package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** [AppLanguages] is the one list of languages: resources, locales_config.xml and the AI note follow it. */
class AppLanguagesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    @Test
    fun theList() {
        val all = AppLanguages.ALL
        assertEquals(30, all.size)
        assertEquals(listOf("en", "de", "ru", "uk"), all.take(4).map { it.tag })
        assertEquals("ko", all.last().tag)
        assertEquals(all.size, all.map { it.tag }.toSet().size)
        assertEquals(all.size, all.map { it.qualifier }.toSet().size)
        assertEquals(listOf("en", "de"), all.filterNot { it.aiTranslation }.map { it.tag })
        all.forEach { assertTrue(it.tag, it.autonym.isNotBlank() && it.englishName.isNotBlank()) }
        assertEquals("values", all.first().valuesFolder)
        assertEquals("raw-b+sr+Latn", AppLanguages.byTag("sr-Latn")!!.rawFolder)
        assertEquals("values-in", AppLanguages.byTag("id")!!.valuesFolder)
        assertEquals("values-zh-rTW", AppLanguages.byTag("zh-TW")!!.valuesFolder)
    }

    @Test
    fun everyLanguageHasItsResources() {
        val missing = AppLanguages.ALL.flatMap { lang ->
            listOf("${lang.valuesFolder}/strings.xml", "${lang.valuesFolder}/strings_runtime.xml", "${lang.rawFolder}/changelog.md")
        }.filterNot { File(res, it).isFile }
        assertEquals(emptyList<String>(), missing)
    }

    /** No translation folder the list does not know (it would be missing from the picker and locales_config.xml). */
    @Test
    fun noOtherTranslations() {
        val known = AppLanguages.ALL.flatMap { listOf(it.valuesFolder, it.rawFolder) }.toSet()
        val translated = res.listFiles().orEmpty()
            .filter { File(it, "strings.xml").isFile || File(it, "changelog.md").isFile }
            .map { it.name }
        assertEquals(emptyList<String>(), translated.filterNot { it in known })
    }

    @Test
    fun localeConfigListsExactlyTheAppLanguages() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File(res, "xml/locales_config.xml"))
        val nodes = doc.getElementsByTagName("locale")
        val tags = (0 until nodes.length).map {
            (nodes.item(it) as Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        }
        assertEquals(AppLanguages.TAGS, tags)
    }

    @Test
    fun translationNoteOnlyForAiTranslations() {
        for (lang in AppLanguages.ALL) {
            val file = File(res, "${lang.valuesFolder}/strings.xml")
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val note = (0 until nodes.length).map { nodes.item(it) as Element }
                .single { it.getAttribute("name") == "about_translation_note" }.textContent
            assertEquals(lang.tag, lang.aiTranslation, note.isNotBlank())
        }
    }

    @Test
    fun systemTagsFindTheirLanguage() {
        mapOf(
            "de" to "de", "DE" to "de", "en-US" to "en", "de-AT" to "de", "pt-BR" to "pt-BR", "pt-PT" to "pt-BR",
            "sr-Latn" to "sr-Latn", "sr-Latn-RS" to "sr-Latn", "sr-Cyrl-RS" to "sr", "sr" to "sr",
            "zh-CN" to "zh-CN", "zh-Hans" to "zh-CN", "zh-Hans-CN" to "zh-CN", "zh-SG" to "zh-CN",
            "zh-TW" to "zh-TW", "zh-Hant" to "zh-TW", "zh-Hant-TW" to "zh-TW", "zh-HK" to "zh-TW",
            "id" to "id", "in" to "id", "in-ID" to "id", "nb-NO" to "nb", "ar-EG" to "ar",
        ).forEach { (tag, expected) -> assertEquals(tag, expected, AppLanguages.match(tag)?.tag) }
        assertNull(AppLanguages.match(null))
        assertNull(AppLanguages.match(""))
        assertNull(AppLanguages.match("xx"))
        assertNull(AppLanguages.match("he"))
    }
}

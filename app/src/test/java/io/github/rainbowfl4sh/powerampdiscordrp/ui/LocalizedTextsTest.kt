package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.core.engine.EngineStatus
import io.github.rainbowfl4sh.powerampdiscordrp.core.presence.Hidden
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.core.update.UpdateResult
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Changelog
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.ChangelogBlock
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.Placeholders
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.StatusTexts
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UiText
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The mapped texts resolve with the real resources in every language (Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)   // the app's own Application wires the whole runtime
class LocalizedTextsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun inEveryLanguage(block: (lang: String) -> Unit) {
        for (lang in listOf("en", "de", "ru", "uk")) {
            RuntimeEnvironment.setQualifiers(lang)
            block(lang)
        }
    }

    @Test
    fun statusTextsResolve() = inEveryLanguage { lang ->
        val res = context.resources
        Hidden.entries.forEach { assertTrue("$lang $it", res.getString(StatusTexts.hidden(it)).isNotBlank()) }
        TransportError.entries.forEach { assertTrue("$lang $it", res.getString(StatusTexts.transportError(it)).isNotBlank()) }
        EngineStatus.Kind.entries.forEach {
            assertTrue("$lang $it", StatusTexts.connection(EngineStatus(kind = it)).text.resolve(res).isNotBlank())
        }
        Placeholders.ALL.forEach { assertTrue("$lang ${it.name}", res.getString(it.description).isNotBlank()) }
    }

    @Test
    fun nestedTextsResolve() {
        RuntimeEnvironment.setQualifiers("en")
        val uploaded = StatusTexts.coverSource("poweramp|x0.at")
        assertEquals("Poweramp’s cover, uploaded to x0.at", uploaded.resolve(context.resources))
        assertEquals(
            "Source: Poweramp’s cover, uploaded to x0.at",
            UiText(R.string.cov_source, uploaded).resolve(context.resources),
        )
        RuntimeEnvironment.setQualifiers("de")
        assertEquals("Cover von Poweramp, hochgeladen zu x0.at", uploaded.resolve(context.resources))
        assertEquals(
            "Test-Presence wird gesendet – sie wird 15 Sekunden angezeigt.",
            UiText(R.string.msg_test_sent, 15).resolve(context.resources),
        )
    }

    @Test
    fun newStatusTextsResolve() = inEveryLanguage { lang ->
        val res = context.resources
        val connected = EngineStatus(kind = EngineStatus.Kind.CONNECTED, testing = true)
        val lines = listOf(
            StatusTexts.nowPlaying(Hidden.NO, connected, stoppedByUser = true).text,
            StatusTexts.nowPlaying(Hidden.NO, connected.copy(activityError = "x"), false).text,
            StatusTexts.nowPlaying(Hidden.NO, connected.copy(visible = true), false).text,
            StatusTexts.nowPlaying(Hidden.STOPPED, connected.copy(visible = true), false).text,
            StatusTexts.nowPlaying(Hidden.NO, connected, false).text,
            StatusTexts.nowPlaying(Hidden.NO, EngineStatus(), false).text,
            StatusTexts.testNote(connected)!!,
            UiText(R.string.conn_activity_error, "Invalid Form Body"),
            UiText(R.string.cov_not_yet),
            UiText(R.string.adv_export_keys_text, "Imgur, Spotify"),
            UiText(R.string.check_appid_builtin_invalid, "12345"),
            UiText(R.string.check_appid_both_invalid, "12345"),
        ) + UpdateResult.Reason.entries.map { UpdateView.failureLine(it).text }
        lines.forEach { assertTrue("$lang $it", it.resolve(res).isNotBlank()) }
        assertTrue(lang, UiText(R.string.check_appid_builtin_invalid, "12345").resolve(res).contains("12345"))
        assertTrue(lang, UiText(R.string.adv_export_keys_text, "Imgur, Spotify").resolve(res).contains("Imgur, Spotify"))
    }

    @Test
    fun placeholderTextsAreNotFormatted() {
        RuntimeEnvironment.setQualifiers("en")
        assertEquals("Length of %bar%", context.getString(R.string.disp_bar_length))
        assertTrue(context.getString(R.string.disp_refresh_note).contains("%pos%, %bar% or %percent%"))
    }

    @Test
    fun rotationTextsKeepTheSeparatorAndTheExample() = inEveryLanguage { lang ->
        val example = "%artist% || %title%"
        assertTrue(lang, context.getString(R.string.disp_rotation_help).contains(example))
        assertTrue(lang, context.getString(R.string.ph_rotation).contains(example))
        assertTrue(lang, context.getString(R.string.disp_rotation_note).contains("||"))
        assertTrue(lang, context.getString(R.string.disp_rotation).isNotBlank())
        assertTrue(lang, context.getString(R.string.disp_rotation_value, 15).contains("15"))
    }

    @Test
    fun changelogIsLocalized() {
        val firstItems = mutableMapOf<String, String>()
        inEveryLanguage { lang ->
            val text = context.resources.openRawResource(R.raw.changelog).bufferedReader().use { it.readText() }
            val blocks = Changelog.parse(text)
            assertTrue(lang, blocks.first() is ChangelogBlock.Version)
            val item = blocks.filterIsInstance<ChangelogBlock.Item>().first()
            firstItems[lang] = item.spans.joinToString("") { it.text }
            // newest version first, the older ones stay
            val versions = blocks.filterIsInstance<ChangelogBlock.Version>().map { it.version }
            assertEquals(lang, listOf("1.1.0", "1.0.0"), versions.take(2))
            // 1.1.0 tells about the rotating texts, with their example
            val newest = blocks.drop(1).takeWhile { it !is ChangelogBlock.Version }.filterIsInstance<ChangelogBlock.Item>()
            assertTrue(lang, newest.any { item -> item.spans.joinToString("") { it.text }.contains("%artist% || %title%") })
        }
        assertEquals(4, firstItems.values.toSet().size)
        assertTrue(firstItems.getValue("de").startsWith("App-Einstellungen"))
    }

    @Test
    fun translationNoteOnlyForAiTranslations() {
        val notes = mutableMapOf<String, String>()
        inEveryLanguage { lang -> notes[lang] = context.getString(R.string.about_translation_note) }
        assertEquals("", notes["en"])
        assertEquals("", notes["de"])
        assertTrue(notes.getValue("ru").isNotBlank())
        assertTrue(notes.getValue("uk").isNotBlank())
    }

    @Test
    fun launcherIconLayersLoad() {
        for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            assertNotNull(ContextCompat.getDrawable(context, id))
        }
    }
}

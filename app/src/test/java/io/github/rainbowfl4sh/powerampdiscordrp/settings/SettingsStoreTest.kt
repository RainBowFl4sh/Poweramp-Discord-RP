package io.github.rainbowfl4sh.powerampdiscordrp.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class SettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var changes = 0

    private fun store() = SettingsStore(context).also { it.onChanged = { _, _ -> changes++ } }

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsWithoutStoredSettings() {
        assertEquals(Config(), store().config.value)
    }

    @Test
    fun updateIsVisibleAtOnceAndPersisted() {
        val s = store()
        s.update { it.copy(details = "%artist% – %title%", hideStreams = true) }
        assertEquals("%artist% – %title%", s.config.value.details)
        assertEquals(1, changes)

        val stored = context.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SettingsStore.KEY_CONFIG, null)!!
        assertEquals(s.config.value, Config.fromJson(stored))
        // A new store (next process start) reads the same settings
        assertEquals(s.config.value, SettingsStore(context).config.value)
    }

    @Test
    fun changeHookGetsOldAndNewSettings() {
        val s = SettingsStore(context)
        val seen = mutableListOf<Pair<Boolean, Boolean>>()
        s.onChanged = { old, new -> seen += old.enabled to new.enabled }
        s.update { it.copy(enabled = false) }
        s.update { it.copy(enabled = true) }
        assertEquals(listOf(true to false, false to true), seen)
    }

    @Test
    fun unchangedUpdateDoesNotNotify() {
        val s = store()
        s.update { it }
        s.update { it.copy() }
        assertEquals(0, changes)
    }

    @Test
    fun updateBookkeepingDoesNotRebuildThePresence() {
        val s = store()
        s.update { it.copy(updateLastCheckMs = 123, updateLatest = "1.2.0") }
        assertEquals(0, changes)
        assertEquals("1.2.0", SettingsStore(context).config.value.updateLatest)
    }

    @Test
    fun brokenStoredJsonFallsBackToDefaults() {
        context.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(SettingsStore.KEY_CONFIG, "{ not json").commit()
        assertEquals(Config(), store().config.value)
    }

    @Test
    fun exportFormat() {
        val s = store()
        s.update { it.copy(state = "by %artist%", updateLastCheckMs = 999, updateLatest = "9.9.9") }
        val root = Config.JSON.parseToJsonElement(s.exportJson()).jsonObject
        assertEquals("poweramp-discord-rp", root["app"]!!.jsonPrimitive.content)
        assertEquals(1, root["version"]!!.jsonPrimitive.int)
        val settings = root["settings"] as JsonObject
        assertEquals("by %artist%", settings["state"]!!.jsonPrimitive.content)
        // The update bookkeeping is not part of an export
        assertEquals("", settings["updateLatest"]!!.jsonPrimitive.content)
        assertEquals("0", settings["updateLastCheck"]!!.jsonPrimitive.content)
    }

    @Test
    fun importRoundTripKeepsOwnUpdateBookkeeping() {
        val source = store()
        source.update { it.copy(activityType = Config.ACTIVITY_PLAYING, barLength = 20, srcSpotify = true) }
        val exported = source.exportJson()

        clearPrefs()
        val target = store()
        target.update { it.copy(updateLastCheckMs = 42, updateLatest = "2.0.0") }
        changes = 0
        assertTrue(target.importJson(exported))
        assertEquals(1, changes)
        val c = target.config.value
        assertEquals(Config.ACTIVITY_PLAYING, c.activityType)
        assertEquals(20, c.barLength)
        assertTrue(c.srcSpotify)
        assertEquals(42L, c.updateLastCheckMs)
        assertEquals("2.0.0", c.updateLatest)
    }

    @Test
    fun importToleratesUnknownAndMissingKeys() {
        val s = store()
        val json = """{"app":"poweramp-discord-rp","version":7,"settings":{"details":"%title%!","someFutureOption":true}}"""
        assertTrue(s.importJson(json))
        assertEquals("%title%!", s.config.value.details)
        assertEquals(Config().state, s.config.value.state)
    }

    @Test
    fun rotationIntervalIsStoredExportedAndImported() {
        val source = store()
        source.update { it.copy(details = "%artist% || %title%", rotationSeconds = 7) }
        assertEquals(1, changes)   // the engine rebuilds the presence with the new interval
        assertEquals(7, SettingsStore(context).config.value.rotationSeconds)
        val exported = source.exportJson()
        val settings = Config.JSON.parseToJsonElement(exported).jsonObject["settings"] as JsonObject
        assertEquals(7, settings["rotationSeconds"]!!.jsonPrimitive.int)
        assertEquals("%artist% || %title%", settings["details"]!!.jsonPrimitive.content)

        clearPrefs()
        val target = store()
        assertTrue(target.importJson(exported))
        assertEquals(7, target.config.value.rotationSeconds)
        assertEquals("%artist% || %title%", target.config.value.details)
    }

    @Test
    fun anExportWithoutRotationIntervalGetsTheDefault() {
        // a file of 1.0.0: no rotationSeconds
        val s = store()
        s.update { it.copy(rotationSeconds = 30) }
        val json = """{"app":"poweramp-discord-rp","version":1,"settings":{"details":"%title%","refreshSeconds":20}}"""
        assertTrue(s.importJson(json))
        assertEquals(Config().rotationSeconds, s.config.value.rotationSeconds)
        assertEquals(10, s.config.value.rotationSeconds)
        assertEquals(20, s.config.value.refreshSeconds)
    }

    @Test
    fun anImportedIntervalOutsideTheSliderIsClamped() {
        val s = store()
        val json = """{"app":"poweramp-discord-rp","version":1,"settings":{"rotationSeconds":1}}"""
        assertTrue(s.importJson(json))
        assertEquals(Config.ROTATION_MIN_SECONDS, s.config.value.rotationSeconds)
    }

    @Test
    fun importedValuesOutsideTheChoicesFallBackToWhatTheUiShows() {
        // a hand-edited file, or one of a newer version with more choices: the UI, the bridge and the engine must not
        // each read such a value their own way
        val s = store()
        val json = """{"app":"poweramp-discord-rp","version":1,"settings":{"pausedBehavior":5,"activityType":4,
            |"statusDisplay":7,"coverMaxPx":0,"uploadHost":9,"updateFrequency":-1,"clearAfterPausedMin":-3,
            |"barLength":500,"refreshSeconds":0,"details":"kept"}}""".trimMargin()
        assertTrue(s.importJson(json))
        val c = s.config.value
        assertEquals(Config.PAUSED_HIDE, c.pausedBehavior)
        assertEquals(Config.ACTIVITY_LISTENING, c.activityType)
        assertEquals(Config.STATUS_DISPLAY_DETAILS, c.statusDisplay)
        assertEquals(Config.DEFAULT_COVER_PX, c.coverMaxPx)
        assertEquals(Config.UPLOAD_X0, c.uploadHost)
        assertEquals(Config.UPDATE_EVERY_START, c.updateFrequency)
        assertEquals(0, c.clearAfterPausedMin)
        assertEquals(Config.BAR_LENGTH_MAX, c.barLength)
        assertEquals(Config.REFRESH_MIN_SECONDS, c.refreshSeconds)
        assertEquals("kept", c.details)
        // what is stored is what is used
        assertEquals(c, SettingsStore(context).config.value)
    }

    @Test
    fun storedValuesOutsideTheChoicesAreNormalizedOnLoad() {
        context.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(SettingsStore.KEY_CONFIG, Config(pausedBehavior = 5, activityType = 1, coverMaxPx = -1).toJson())
            .commit()
        val c = store().config.value
        assertEquals(Config.PAUSED_HIDE, c.pausedBehavior)
        assertEquals(Config.ACTIVITY_LISTENING, c.activityType)
        assertEquals(Config.DEFAULT_COVER_PX, c.coverMaxPx)
    }

    @Test
    fun validChoicesAreKept() {
        val s = store()
        s.update {
            it.copy(
                pausedBehavior = Config.PAUSED_SHOW, activityType = Config.ACTIVITY_PLAYING, uploadHost = Config.UPLOAD_OFF,
                coverMaxPx = 1024, statusDisplay = Config.STATUS_DISPLAY_NAME, updateFrequency = Config.UPDATE_MONTHLY,
                clearAfterPausedMin = 30, barLength = 4, refreshSeconds = 120,
            )
        }
        val c = s.config.value
        assertEquals(c, c.sanitized())
        assertEquals(Config.PAUSED_SHOW, c.pausedBehavior)
        assertEquals(Config.ACTIVITY_PLAYING, c.activityType)
        assertEquals(1024, c.coverMaxPx)
        assertEquals(Config(), Config().sanitized())
    }

    @Test
    fun importRejectsForeignFiles() {
        val s = store()
        s.update { it.copy(details = "mine") }
        changes = 0
        val rejected = listOf(
            "",
            "not json at all",
            "[1, 2, 3]",
            """{"settings":{"details":"x"}}""",                                  // no marker
            """{"app":"aimp-discord-rp","version":1,"settings":{"details":"x"}}""", // other app
            """{"app":"poweramp-discord-rp","version":1}""",                    // no settings
            """{"app":"poweramp-discord-rp","version":1,"settings":"x"}""",
            """{"app":"poweramp-discord-rp","version":1,"settings":{"barLength":"long"}}""",
            Config(details = "plain config without wrapper").toJson(),
        )
        for (json in rejected) assertFalse(json, s.importJson(json))
        assertEquals("mine", s.config.value.details)
        assertEquals(0, changes)
    }

    @Test
    fun resetKeepsUpdateBookkeeping() {
        val s = store()
        s.update { it.copy(enabled = false, coverMaxPx = 256, updateLastCheckMs = 77, updateLatest = "1.0.1") }
        s.resetToDefaults()
        assertEquals(Config(updateLastCheckMs = 77, updateLatest = "1.0.1"), s.config.value)
    }

    @Test
    fun concurrentUpdatesAreNotLost() {
        val s = store()
        val threads = List(8) {
            Thread { repeat(50) { s.update { c -> c.copy(clearAfterPausedMin = c.clearAfterPausedMin + 1) } } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(Config().clearAfterPausedMin + 400, s.config.value.clearAfterPausedMin)
    }
}

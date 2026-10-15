package io.github.rainbowfl4sh.powerampdiscordrp.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** The settings (with the Spotify secret, Discogs token and Imgur client ID) stay out of Android's backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRulesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun backupIsSwitchedOff() {
        // Cloud backup on every version, device-to-device transfer up to Android 11
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun android12RulesExcludeEverything() {
        // allowBackup="false" no longer stops device-to-device transfers on Android 12+: the rules must
        val everything = setOf("root:.", "file:.", "database:.", "sharedpref:.", "external:.")
        val rules = sections(R.xml.data_extraction_rules)
        assertEquals(setOf("cloud-backup", "device-transfer"), rules.keys)
        for ((section, entries) in rules) {
            assertEquals(section, everything.map { "exclude:$it" }.toSet(), entries.toSet())
        }
    }

    /** section name -> "exclude:domain:path" / "include:domain:path" entries. */
    private fun sections(xml: Int): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        val parser = app.resources.getXml(xml)
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name.also { result[it] = mutableListOf() }
                "include", "exclude" -> result.getValue(section!!) +=
                    "${parser.name}:${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
            }
        }
        return result
    }
}

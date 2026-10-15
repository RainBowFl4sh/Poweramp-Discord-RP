package io.github.rainbowfl4sh.powerampdiscordrp.cover

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.Poweramp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLocalCoverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val resolver = shadowOf(app.contentResolver)

    private fun register(realId: Long, stream: InputStream) =
        resolver.registerInputStream(Poweramp.albumArtUri(realId), stream)

    @Test
    fun readsTheCoverPowerampShows() {
        val image = ByteArray(300_000) { (it % 251).toByte() }
        register(42, ByteArrayInputStream(image))
        assertArrayEquals(image, AndroidLocalCover(app).read(TrackInfo(realId = 42, title = "x")))
    }

    @Test
    fun noRealIdNoCover() {
        assertNull(AndroidLocalCover(app).read(TrackInfo(realId = 0, title = "x")))
        assertNull(AndroidLocalCover(app).read(TrackInfo(realId = -3, title = "x")))
    }

    @Test
    fun tooLargeIsSkipped() {
        val lines = mutableListOf<String>()
        register(7, ByteArrayInputStream(ByteArray(2_000)))
        assertNull(AndroidLocalCover(app, log = { lines += it }, maxBytes = 1_000).read(TrackInfo(realId = 7)))
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().contains("too large"))
    }

    /** Like Poweramp's album art provider when it fails inside (e.g. still starting): no file, no exception. */
    class NoDataProvider : ContentProvider() {
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = null
        override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<String>?) = 0
    }

    @Test
    fun providerWithoutDataIsNotReportedAsTooLarge() {
        Robolectric.buildContentProvider(NoDataProvider::class.java)
            .create(ProviderInfo().apply { authority = Poweramp.AA_AUTHORITY })
        val lines = mutableListOf<String>()
        assertNull(AndroidLocalCover(app, log = { lines += it }).read(TrackInfo(realId = 11)))
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().contains("no data"))
        assertFalse(lines.single(), lines.single().contains("too large"))
    }

    @Test
    fun emptyStreamIsNoCover() {
        register(8, ByteArrayInputStream(ByteArray(0)))
        assertNull(AndroidLocalCover(app).read(TrackInfo(realId = 8)))
    }

    @Test
    fun readErrorsGiveNull() {
        register(9, object : InputStream() {
            override fun read(): Int = throw IOException("pipe broken")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("pipe broken")
        })
        assertNull(AndroidLocalCover(app).read(TrackInfo(realId = 9)))
        // Nothing registered: Poweramp not installed / no provider
        assertNull(AndroidLocalCover(app).read(TrackInfo(realId = 10)))
    }

    @Test
    fun readCappedReadsPipesInChunks() {
        // A pipe hands out a few bytes per read() and its size is unknown in advance
        val data = ByteArray(200_000) { it.toByte() }
        val trickle = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= data.size) return -1
                val n = minOf(len, 4_096, data.size - pos)
                System.arraycopy(data, pos, b, off, n)
                pos += n
                return n
            }
        }
        assertArrayEquals(data, AndroidLocalCover.readCapped(trickle, 200_000))
        assertNull(AndroidLocalCover.readCapped(ByteArrayInputStream(data), 199_999))
    }
}

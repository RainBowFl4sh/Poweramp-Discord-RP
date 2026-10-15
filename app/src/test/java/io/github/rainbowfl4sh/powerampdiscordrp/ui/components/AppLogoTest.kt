package io.github.rainbowfl4sh.powerampdiscordrp.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rainbowfl4sh.powerampdiscordrp.R
import io.github.rainbowfl4sh.powerampdiscordrp.ui.MainThreadCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import android.graphics.Color as AndroidColor

/** The in-app logo looks like Poweramp's raster icon: a round dark disc whose rim and glow end in the surface behind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLogoTest {
    private val lightSurface = Color(0xFFFDF7FF)

    /** [AppLogo] at [size] on [surface], drawn into a bitmap, and where in it the logo is. */
    private fun draw(size: Dp, surface: Color): Pair<Bitmap, Rect> {
        MainThreadCompose.reset()
        var bounds = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Box(Modifier.background(surface).padding(8.dp)) {
                AppLogo(size, Modifier.onGloballyPositioned { bounds = it.boundsInWindow() })
            }
        }
        ShadowLooper.idleMainLooper()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(size.value * 3, bounds.width, 1f)   // xxhdpi
        return bitmap to bounds
    }

    /** The pixels from just left of the logo to its centre, through the middle. */
    private fun leftHalf(bitmap: Bitmap, bounds: Rect): List<Int> =
        (bounds.left.toInt() - 3..bounds.center.x.toInt()).map { bitmap.getPixel(it, bounds.center.y.toInt()) }

    private fun luminance(pixel: Int) =
        (AndroidColor.red(pixel) * 299 + AndroidColor.green(pixel) * 587 + AndroidColor.blue(pixel) * 114) / 1000

    private fun periwinkle(pixel: Int) = AndroidColor.blue(pixel) > 200 && AndroidColor.blue(pixel) - AndroidColor.red(pixel) > 40

    private fun hex(pixels: List<Int>) = pixels.joinToString(" ") { Integer.toHexString(it) }

    @Test
    fun theLauncherBackgroundLosesOnlyItsBlackBackdrop() {
        val res = RuntimeEnvironment.getApplication().resources
        val background = ImageVector.vectorResource(res = res, resId = R.drawable.ic_launcher_background)
        val paths = background.root.filterIsInstance<VectorPath>()
        val backdrop = paths.single { it.name == LAUNCHER_BACKDROP }
        assertEquals(SolidColor(Color.Black), backdrop.fill)

        val disc = background.withoutPath(LAUNCHER_BACKDROP)
        assertEquals((paths - backdrop).map { it.pathData }, disc.root.filterIsInstance<VectorPath>().map { it.pathData })
        assertEquals(background.viewportWidth, disc.viewportWidth)
        assertEquals(background.defaultWidth, disc.defaultWidth)
    }

    @Test
    fun onALightSurfaceTheBlueRimIsTheOutline() {
        for (size in listOf(32.dp, 72.dp, 112.dp)) {
            val (bitmap, bounds) = draw(size, lightSurface)
            assertEquals("round: the corner shows the surface", lightSurface.toArgb(), bitmap.getPixel(bounds.left.toInt() + 1, bounds.top.toInt() + 1))
            val pixels = leftHalf(bitmap, bounds)
            // from the outside in: the surface, the glow (a light tint), the blue rim, then the dark disc - no dark ring
            val disc = pixels.indexOfFirst { luminance(it) < 60 }
            val rim = pixels.indexOfFirst(::periwinkle)
            assertTrue("$size: no blue rim around the dark disc in ${hex(pixels)}", rim in 1 until disc)
            assertTrue("$size: dark pixels outside the rim in ${hex(pixels)}", pixels.subList(0, rim).all { luminance(it) >= 100 })
        }
    }

    @Test
    fun onBlackTheGlowFadesOutInsideTheRoundLogo() {
        val (bitmap, bounds) = draw(112.dp, Color.Black)
        val pixels = leftHalf(bitmap, bounds)
        // the logo's edge: the glow has faded out, no cut-off ring
        val edge = pixels[3]
        assertTrue("edge #${Integer.toHexString(edge)}", luminance(edge) < 12)
        // towards the rim, a periwinkle glow lightens the black
        val rim = pixels.indexOfFirst(::periwinkle)
        assertTrue("no rim in ${hex(pixels)}", rim > 3)
        val glow = pixels.subList(4, rim)
        assertTrue("no glow in ${hex(pixels)}", glow.any { luminance(it) in 30..120 && AndroidColor.blue(it) > AndroidColor.red(it) })
    }
}

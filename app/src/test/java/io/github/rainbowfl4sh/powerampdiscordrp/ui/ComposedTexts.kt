package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * What a composed screen says and offers, read from Compose's semantics tree (the app's tests run without
 * compose-ui-test). [root] is a window's decor view, e.g. an activity's or a dialog's; [everyWindow] also sees popups
 * (menus).
 */
class ComposedTexts private constructor(private val rootViews: () -> List<View>) {
    constructor(root: View) : this({ listOf(root) })

    private fun roots(merged: Boolean): List<SemanticsNode> = buildList {
        fun walk(v: View) {
            if (v is ViewRootForTest) {
                add(if (merged) v.semanticsOwner.rootSemanticsNode else v.semanticsOwner.unmergedRootSemanticsNode)
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        rootViews().forEach(::walk)
    }

    private fun SemanticsNode.withDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.withDescendants() }

    private fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    private fun SemanticsNode.descriptions(): List<String> = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    /** Every text shown, one entry per Text. */
    val texts: List<String> get() = roots(merged = false).flatMap { it.withDescendants() }.flatMap { it.texts() }

    /** Every content description (icons, pictures). */
    val descriptions: List<String> get() = roots(merged = false).flatMap { it.withDescendants() }.flatMap { it.descriptions() }

    operator fun contains(text: String): Boolean = text in texts

    /** The control (button, switch row) that shows [text] or is described as [text] (icon buttons). */
    private fun control(text: String): SemanticsNode =
        roots(merged = true).flatMap { it.withDescendants() }
            .firstOrNull { (text in it.texts() || text in it.descriptions()) && SemanticsActions.OnClick in it.config }
            ?: error("No control shows \"$text\" - texts: $texts, descriptions: $descriptions")

    fun isEnabled(text: String): Boolean = SemanticsProperties.Disabled !in control(text).config

    fun click(text: String) {
        val action = control(text).config[SemanticsActions.OnClick].action ?: error("\"$text\" cannot be clicked")
        action()
    }

    /** Gives the focus to the text field that holds [text] (as a tap into it would). */
    fun focusField(text: String) {
        val field = roots(merged = false).flatMap { it.withDescendants() }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text == text && SemanticsActions.RequestFocus in it.config }
            ?: error("No text field holds \"$text\"")
        field.config[SemanticsActions.RequestFocus].action?.invoke()
    }

    /** Moves the slider whose values span [range] to [value] (as a drag or an accessibility service would). */
    fun slide(range: ClosedFloatingPointRange<Float>, value: Float) {
        val slider = roots(merged = true).flatMap { it.withDescendants() }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == range }
            ?: error("No slider for $range")
        val action = slider.config[SemanticsActions.SetProgress].action ?: error("The slider for $range cannot be moved")
        action(value)
    }

    /** Where the control showing [text] is, in pixels of its Compose root. */
    fun bounds(text: String): Rect = control(text).boundsInRoot

    /** Width of the (first) Compose root in pixels. */
    val rootWidth: Float get() = roots(merged = true).first().boundsInRoot.width

    companion object {
        /** Every window shown: activities, dialogs and popups such as menus. */
        fun everyWindow(): ComposedTexts = ComposedTexts { windowRoots() }

        /** The root views of all windows (WindowManagerGlobal is hidden API; Robolectric runs the real framework). */
        @Suppress("UNCHECKED_CAST")
        private fun windowRoots(): List<View> {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            return views.toList()
        }
    }
}

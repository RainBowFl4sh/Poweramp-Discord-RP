package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources

/**
 * A text from the string resources with its arguments, resolved where a Context is at hand. Lets the pure logic
 * (and its tests) decide WHICH text is shown without needing Android resources. Arguments may be UiTexts themselves.
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    fun resolve(resources: Resources): String {
        if (args.isEmpty()) return resources.getString(id)
        val resolved = args.map { if (it is UiText) it.resolve(resources) else it }
        return resources.getString(id, *resolved.toTypedArray())
    }
}

/** Resolves the text in a composable (recomposed when the configuration / language changes). */
@Composable
@ReadOnlyComposable
fun UiText.text(): String = resolve(LocalResources.current)

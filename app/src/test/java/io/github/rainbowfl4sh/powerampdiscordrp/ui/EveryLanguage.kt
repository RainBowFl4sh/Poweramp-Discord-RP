package io.github.rainbowfl4sh.powerampdiscordrp.ui

import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguage
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.AppLanguages
import org.robolectric.RuntimeEnvironment

/** Robolectric's qualifiers for [this] language: its resource qualifier ("en" for the default texts). */
val AppLanguage.testQualifier: String get() = qualifier.ifEmpty { "en" }

/** Runs [block] once in every language of [AppLanguages] (Robolectric's qualifiers set to it). */
fun inEveryAppLanguage(block: (lang: AppLanguage) -> Unit) {
    for (lang in AppLanguages.ALL) {
        RuntimeEnvironment.setQualifiers(lang.testQualifier)
        block(lang)
    }
}

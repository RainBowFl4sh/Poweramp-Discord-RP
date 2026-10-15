package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config

/** Which cover options take effect (like the core's CoverResolver decides), for enabling them on the tabs. */
object CoverOptions {
    /** Poweramp's cover is used: it needs an upload host for a public link ("Off" = online lookups only). */
    fun usesPowerampCover(c: Config): Boolean = c.coverEnabled && c.localCovers && c.uploadHost != Config.UPLOAD_OFF

    /** "Prefer Poweramp's cover" limits the online lookups to tracks without one - only while that cover is used. */
    fun prefersPowerampCover(c: Config): Boolean = usesPowerampCover(c) && c.preferLocal
}

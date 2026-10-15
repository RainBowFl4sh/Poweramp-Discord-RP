package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config

/** Keys an export of the settings carries in plain text (that is how they get to a new phone). */
object ExportKeys {
    /** Services whose keys are set (Imgur Client-ID, Spotify Client ID / Secret, Discogs token), in the order of the tabs. */
    fun services(c: Config): List<String> = buildList {
        if (c.imgurClientId.isNotBlank()) add("Imgur")
        if (c.spotifyId.isNotBlank() || c.spotifySecret.isNotBlank()) add("Spotify")
        if (c.discogsToken.isNotBlank()) add("Discogs")
    }
}

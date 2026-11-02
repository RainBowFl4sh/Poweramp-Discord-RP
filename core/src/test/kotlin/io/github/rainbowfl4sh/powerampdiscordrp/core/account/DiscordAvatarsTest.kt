package io.github.rainbowfl4sh.powerampdiscordrp.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordAvatarsTest {
    private val id = "80351110224678912"
    private val hash = "8342729096ea3675442027381ff50dfe"

    @Test
    fun anAvatarHashGivesItsPicture() {
        assertEquals("https://cdn.discordapp.com/avatars/$id/$hash.png?size=128", DiscordAvatars.url(id, hash))
        assertEquals("https://cdn.discordapp.com/avatars/$id/$hash.png?size=64", DiscordAvatars.url(id, hash, 64))
    }

    @Test
    fun anAnimatedAvatarIsShownAsPng() {
        assertEquals("https://cdn.discordapp.com/avatars/$id/a_$hash.png?size=128", DiscordAvatars.url(id, "a_$hash"))
    }

    @Test
    fun withoutAHashDiscordsDefaultAvatarForTheId() {
        // (id >>> 22) % 6
        assertEquals(5, DiscordAvatars.defaultIndex(id))
        assertEquals(2, DiscordAvatars.defaultIndex("175928847299117063"))
        assertEquals(5, DiscordAvatars.defaultIndex("1559554394301800528"))
        // the biggest snowflake: unsigned 64 bits, no overflow
        assertEquals(3, DiscordAvatars.defaultIndex("18446744073709551615"))
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", DiscordAvatars.url(id, ""))
        assertEquals("https://cdn.discordapp.com/embed/avatars/2.png", DiscordAvatars.url("175928847299117063", "  "))
        // small IDs (all in the first 4 ms of Discord's epoch) get avatar 0
        assertEquals("https://cdn.discordapp.com/embed/avatars/0.png", DiscordAvatars.url("1", ""))
    }

    @Test
    fun nothingFromOutsideEndsUpInThePath() {
        assertEquals("", DiscordAvatars.url("x/../y", hash))
        assertEquals("", DiscordAvatars.url("", hash))
        assertEquals("", DiscordAvatars.url("18446744073709551616", hash))   // more than 64 bits
        // not a hash: the default avatar instead
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", DiscordAvatars.url(id, "../../evil"))
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", DiscordAvatars.url(id, "a_"))
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", DiscordAvatars.url(id, "ABC"))
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", DiscordAvatars.url(id, "a".repeat(65)))
    }

    @Test
    fun snowflakes() {
        assertTrue(DiscordAvatars.isSnowflake("80351110224678912"))
        assertFalse(DiscordAvatars.isSnowflake("-1"))
        assertFalse(DiscordAvatars.isSnowflake("1e5"))
        assertFalse(DiscordAvatars.isSnowflake(""))
    }

    @Test
    fun theAccountsNameAndPicture() {
        val account = LinkedAccount(id = id, username = "flash", globalName = " Fl4sh ", avatar = hash)
        assertEquals("Fl4sh", account.displayName)
        assertEquals("https://cdn.discordapp.com/avatars/$id/$hash.png?size=128", account.avatarUrl)
        assertEquals("flash", account.copy(globalName = "").displayName)
        assertEquals("https://cdn.discordapp.com/embed/avatars/5.png", account.copy(avatar = "").avatarUrl)
    }
}

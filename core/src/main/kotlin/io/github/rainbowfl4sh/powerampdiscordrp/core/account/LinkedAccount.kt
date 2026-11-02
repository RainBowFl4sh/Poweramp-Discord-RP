package io.github.rainbowfl4sh.powerampdiscordrp.core.account

/**
 * The public profile of the Discord account the user linked (OAuth2, scope "identify"): what the live preview and the
 * status page show. The Discord app's RPC service only reports a placeholder user, so this is the one way to know the
 * real name and picture. No token is kept with it.
 */
data class LinkedAccount(
    val id: String,
    val username: String,
    val globalName: String = "",
    val avatar: String = "",       // avatar hash, "" = Discord's default avatar
    val linkedAtMs: Long = 0,      // unix ms of the link / the last refresh
) {
    /** The name Discord shows: the display name, else the username. */
    val displayName: String get() = globalName.trim().ifEmpty { username.trim() }

    /** Discord CDN link of the profile picture ([DiscordAvatars.url]). */
    val avatarUrl: String get() = DiscordAvatars.url(id, avatar)
}

/** Links of Discord profile pictures on its CDN. */
object DiscordAvatars {
    const val CDN = "https://cdn.discordapp.com"

    /** Edge in pixels asked for (Discord's CDN takes powers of two); the screens show avatars at 40-48 dp. */
    const val SIZE = 128

    /** Number of default avatars for accounts with the new unique usernames (embed/avatars/0..5.png). */
    private const val DEFAULT_AVATARS = 6uL

    private const val MAX_HASH_LENGTH = 64

    /**
     * The picture of user [id]: its avatar [hash] as PNG - an animated one ("a_" prefix) too, as its still first frame -
     * or Discord's default avatar for the ID when there is no hash (or something that is not one: nothing from outside
     * ends up in the path). "" if [id] is not a Discord ID.
     */
    fun url(id: String, hash: String, size: Int = SIZE): String {
        val snowflake = id.trim()
        if (!isSnowflake(snowflake)) return ""
        val h = hash.trim()
        if (!isHash(h)) return defaultUrl(snowflake)
        return "$CDN/avatars/$snowflake/$h.png?size=$size"
    }

    /** Discord's default avatar of [id]: embed/avatars/{(id >>> 22) % 6}.png (the rule for the new usernames). */
    fun defaultUrl(id: String): String = "$CDN/embed/avatars/${defaultIndex(id)}.png"

    /** (id >>> 22) % 6 - the snowflake's timestamp part picks one of the six default avatars; 0 for a malformed ID. */
    fun defaultIndex(id: String): Int {
        val snowflake = id.trim().takeIf(::isSnowflake)?.toULongOrNull() ?: return 0
        return ((snowflake shr 22) % DEFAULT_AVATARS).toInt()
    }

    /** A Discord ID: 1-20 digits that fit the unsigned 64 bits of a snowflake. */
    fun isSnowflake(id: String): Boolean = id.length in 1..20 && id.all { it in '0'..'9' } && id.toULongOrNull() != null

    /** An avatar hash: hex digits, "a_" in front for an animated one. */
    private fun isHash(hash: String): Boolean {
        val hex = hash.removePrefix("a_")
        return hex.isNotEmpty() && hash.length <= MAX_HASH_LENGTH && hex.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

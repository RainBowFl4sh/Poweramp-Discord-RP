package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

/** The author's GitHub account, derived from the repository the app is built from ("owner/name"). */
object GitHubProfile {
    /** "RainBowFl4sh/Poweramp-Discord-RP" -> "RainBowFl4sh". */
    fun user(repo: String): String = repo.substringBefore('/').trim()

    fun profileUrl(user: String): String = "https://github.com/$user"

    /** The profile picture, [sizePx] wide (GitHub scales it; the About tab shows it at 64 dp). */
    fun avatarUrl(user: String, sizePx: Int = 256): String = "https://avatars.githubusercontent.com/$user?s=$sizePx"
}

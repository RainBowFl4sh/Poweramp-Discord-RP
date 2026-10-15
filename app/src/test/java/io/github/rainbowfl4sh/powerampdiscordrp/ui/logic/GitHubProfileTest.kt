package io.github.rainbowfl4sh.powerampdiscordrp.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubProfileTest {
    @Test
    fun userIsTheRepositoryOwner() {
        assertEquals("RainBowFl4sh", GitHubProfile.user("RainBowFl4sh/Poweramp-Discord-RP"))
        assertEquals("someone", GitHubProfile.user("someone"))
    }

    @Test
    fun urls() {
        assertEquals("https://github.com/RainBowFl4sh", GitHubProfile.profileUrl("RainBowFl4sh"))
        assertEquals("https://avatars.githubusercontent.com/RainBowFl4sh?s=256", GitHubProfile.avatarUrl("RainBowFl4sh"))
        assertEquals("https://avatars.githubusercontent.com/RainBowFl4sh?s=128", GitHubProfile.avatarUrl("RainBowFl4sh", 128))
    }
}

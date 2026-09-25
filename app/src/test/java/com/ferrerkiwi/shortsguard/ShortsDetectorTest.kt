package com.ferrerkiwi.shortsguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class ShortsDetectorTest {
    @Test
    fun `does not block normal video that merely shows a Shorts shelf`() {
        val snapshot = fixture("normal_video_with_shorts_shelf")

        assertFalse(ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent = false))
    }

    @Test
    fun `blocks a Shorts player with explicit player route evidence`() {
        val snapshot = fixture("shorts_player")

        assertTrue(ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent = false))
    }

    @Test
    fun `blocks a reel watch player when YouTube exposes no action labels`() {
        val snapshot = fixture("reel_watch_player_without_action_labels")

        assertTrue(ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent = false))
    }

    @Test
    fun `blocks a player entered from a labelled Shorts control`() {
        val snapshot = fixture("shorts_entry_player")

        assertTrue(ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent = true))
    }

    @Test
    fun `does not block a Home shelf after clicking no Shorts control`() {
        val snapshot = fixture("home_shorts_shelf")

        assertFalse(ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent = true))
    }

    @Test
    fun `blocks a direct Shorts URL once player controls appear`() {
        val snapshot = fixture("direct_shorts_link")

        assertTrue(ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent = false))
    }

    private fun fixture(name: String): ShortsDetector.UiSnapshot {
        val properties = Properties()
        val resource = requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name.properties"))
        resource.use(properties::load)
        return ShortsDetector.UiSnapshot(
            labels = properties.getProperty("labels").split('|'),
            identifiers = properties.getProperty("identifiers").split('|'),
        )
    }
}

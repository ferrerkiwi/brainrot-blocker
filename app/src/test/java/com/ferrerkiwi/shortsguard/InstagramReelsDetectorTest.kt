package com.ferrerkiwi.shortsguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class InstagramReelsDetectorTest {
    @Test
    fun `blocks the calibrated full-screen Reels player`() {
        assertTrue(InstagramReelsDetector.isReelsPlayer(fixture("instagram_reels_player")))
    }

    @Test
    fun `does not block a normal Instagram feed video`() {
        assertFalse(InstagramReelsDetector.isReelsPlayer(fixture("instagram_feed_video")))
    }

    @Test
    fun `does not block when only one player marker is present`() {
        val partialPlayer = ShortsDetector.UiSnapshot(
            labels = emptyList(),
            identifiers = listOf("com.instagram.android:id/clips_viewer_view_pager"),
        )

        assertFalse(InstagramReelsDetector.isReelsPlayer(partialPlayer))
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

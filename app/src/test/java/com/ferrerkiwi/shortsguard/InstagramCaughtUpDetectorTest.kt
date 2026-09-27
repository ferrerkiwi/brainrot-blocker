package com.ferrerkiwi.shortsguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramCaughtUpDetectorTest {
    @Test
    fun `recognizes the caught up banner and suggested posts on the calibrated feed`() {
        assertTrue(InstagramCaughtUpDetector.isBoundary(null, "You're all caught up", null))
        assertTrue(InstagramCaughtUpDetector.isBoundary(null, "You’re all caught up", null))
        assertTrue(InstagramCaughtUpDetector.isBoundary(
            "com.instagram.android:id/end_of_feed_demarcator_container", null, null,
        ))
        assertTrue(InstagramCaughtUpDetector.isBoundary(null, "Suggested Posts", null))
        assertTrue(InstagramCaughtUpDetector.isBoundary(null, null, "Suggested for you"))
    }

    @Test
    fun `ignores ordinary feed labels`() {
        assertFalse(InstagramCaughtUpDetector.isBoundary(null, "You're all set", null))
        assertFalse(InstagramCaughtUpDetector.isBoundary(null, "Someone said you're all caught up", null))
        assertFalse(InstagramCaughtUpDetector.isBoundary(null, null, null))
    }

    @Test
    fun `prefers the caught up marker over suggested post labels`() {
        val marker = InstagramCaughtUpDetector.priority(
            "com.instagram.android:id/end_of_feed_demarcator_container", null, null,
        )
        val caughtUpTitle = InstagramCaughtUpDetector.priority(null, "You're all caught up", null)
        val suggestedTitle = InstagramCaughtUpDetector.priority(null, "Suggested Posts", null)
        val suggestion = InstagramCaughtUpDetector.priority(null, "Suggested for you", null)

        assertTrue(marker > caughtUpTitle)
        assertTrue(caughtUpTitle > suggestedTitle)
        assertTrue(suggestedTitle > suggestion)
    }
}

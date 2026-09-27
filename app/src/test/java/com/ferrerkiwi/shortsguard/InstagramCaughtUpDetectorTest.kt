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
}

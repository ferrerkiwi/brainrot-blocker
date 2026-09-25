package com.ferrerkiwi.shortsguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsBlockPolicyTest {
    private val policy = ShortsBlockPolicy(cooldownMs = 1_750)

    @Test
    fun `does not repeat Back for duplicate player events during one transition`() {
        policy.recordBlock(now = 1_000)

        assertFalse(policy.shouldBlock(now = 1_200))
    }

    @Test
    fun `allows a new Shorts tab attempt during the previous transition cooldown`() {
        policy.recordBlock(now = 1_000)
        policy.recordShortsIntent()

        assertTrue(policy.shouldBlock(now = 1_200))
    }

    @Test
    fun `allows another card after YouTube has returned to a non Shorts screen`() {
        policy.recordBlock(now = 1_000)
        policy.recordNonShortsSurface()

        assertTrue(policy.shouldBlock(now = 1_200))
    }

    @Test
    fun `allows a retry after the cooldown expires even without an exposed tap label`() {
        policy.recordBlock(now = 1_000)

        assertTrue(policy.shouldBlock(now = 2_750))
    }
}

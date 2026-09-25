package com.ferrerkiwi.shortsguard

/**
 * Keeps a Back action from repeating during one player-to-feed transition without delaying a
 * genuinely new Shorts attempt. All state is memory-only and disappears when the service stops.
 */
internal class ShortsBlockPolicy(private val cooldownMs: Long) {
    private var cooldownExpiresAt = 0L
    private var hasObservedNonShortsSurface = true
    private var shortsIntentGeneration = 0
    private var blockedIntentGeneration = 0

    fun recordShortsIntent() {
        shortsIntentGeneration += 1
    }

    fun recordNonShortsSurface() {
        hasObservedNonShortsSurface = true
    }

    fun shouldBlock(now: Long): Boolean =
        now >= cooldownExpiresAt ||
            hasObservedNonShortsSurface ||
            shortsIntentGeneration > blockedIntentGeneration

    fun recordBlock(now: Long) {
        cooldownExpiresAt = now + cooldownMs
        hasObservedNonShortsSurface = false
        blockedIntentGeneration = shortsIntentGeneration
    }
}

package com.ferrerkiwi.shortsguard

/** Matches the end of followed posts and the suggested posts that come after it. */
internal object InstagramCaughtUpDetector {
    private const val END_OF_FEED_ID = "com.instagram.android:id/end_of_feed_demarcator_container"
    private val boundaryLabels = setOf("you're all caught up", "suggested posts", "suggested for you")

    fun isBoundary(viewId: String?, text: CharSequence?, description: CharSequence?): Boolean {
        if (viewId == END_OF_FEED_ID) return true
        return isBoundaryLabel(text) || isBoundaryLabel(description)
    }

    private fun isBoundaryLabel(value: CharSequence?): Boolean {
        val normalized = value?.toString()
            ?.trim()
            ?.lowercase()
            ?.replace('’', '\'')
            ?: return false
        return normalized in boundaryLabels
    }
}

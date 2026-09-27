package com.ferrerkiwi.shortsguard

/** Matches the end of followed posts and the suggested posts that come after it. */
internal object InstagramCaughtUpDetector {
    private const val END_OF_FEED_ID = "com.instagram.android:id/end_of_feed_demarcator_container"
    private const val CAUGHT_UP_LABEL = "you're all caught up"
    fun priority(viewId: String?, text: CharSequence?, description: CharSequence?): Int {
        if (viewId == END_OF_FEED_ID) return 4
        val labels = listOfNotNull(normalize(text), normalize(description))
        if (CAUGHT_UP_LABEL in labels) return 3
        if ("suggested posts" in labels) return 2
        return if ("suggested for you" in labels) 1 else 0
    }

    fun isBoundary(viewId: String?, text: CharSequence?, description: CharSequence?): Boolean =
        priority(viewId, text, description) > 0

    private fun normalize(value: CharSequence?): String? = value?.toString()
        ?.trim()
        ?.lowercase()
        ?.replace('’', '\'')
}

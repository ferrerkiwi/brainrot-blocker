package com.ferrerkiwi.shortsguard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * A deliberately conservative detector. It never stores a node's contents: a compact
 * [UiSnapshot] exists only for the duration of an accessibility callback.
 *
 * The player route identifiers are intentionally narrow. If a YouTube update removes
 * these signals, the guard fails open instead of backing out of an ordinary video.
 */
internal object ShortsDetector {
    private val shortsPlayerRouteMarkers = setOf(
        "shorts_player",
        "shorts_watch",
        "reel_player",
        "reel_watch",
        "/shorts/",
    )

    private val playerControlLabels = setOf(
        "like",
        "dislike",
        "comments",
        "share",
        "subscribe",
    )

    fun isShortsEntry(node: AccessibilityNodeInfo?): Boolean {
        return node?.toUiSnapshot()?.hasShortsLabel == true
    }

    fun isShortsPlayer(snapshot: UiSnapshot, hasRecentShortsIntent: Boolean): Boolean {
        // `reel_watch` and `reel_player` are player-only YouTube identifiers. The controls in
        // this surface are not consistently exposed through accessibility (including on the
        // current YouTube release), so requiring their labels would miss real Shorts players.
        if (snapshot.hasShortsPlayerRoute) return true

        // Tapping an explicitly labelled Shorts entry is secondary evidence. Require the exact
        // Shorts label and standard player controls to still be visible before blocking.
        return hasRecentShortsIntent && snapshot.hasShortsLabel && snapshot.hasPlayerControls
    }

    fun AccessibilityNodeInfo.toUiSnapshot(): UiSnapshot {
        val labels = mutableListOf<String>()
        val identifiers = mutableListOf<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(this)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.text?.toString()?.let(labels::add)
            node.contentDescription?.toString()?.let(labels::add)
            node.viewIdResourceName?.let(identifiers::add)
            node.className?.toString()?.let(identifiers::add)

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(stack::add)
            }
        }

        return UiSnapshot(labels = labels, identifiers = identifiers)
    }

    internal data class UiSnapshot(
        val labels: List<String>,
        val identifiers: List<String>,
    ) {
        private val normalizedLabels = labels.map(::normalize)
        private val normalizedIdentifiers = identifiers.map(::normalize)

        val hasShortsLabel: Boolean
            get() = normalizedLabels.any { label ->
                label == "shorts" || label == "shorts player" || label.startsWith("shorts ")
            }

        val hasShortsPlayerRoute: Boolean
            get() = normalizedIdentifiers.any { identifier ->
                shortsPlayerRouteMarkers.any(identifier::contains)
            }

        val hasPlayerControls: Boolean
            get() = normalizedLabels.count { label ->
                playerControlLabels.any { control -> label == control || label.startsWith("$control ") }
            } >= 3
    }

}

private fun normalize(value: String): String = value.trim().lowercase()

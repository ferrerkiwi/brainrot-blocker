package com.ferrerkiwi.shortsguard

/**
 * Detects only Instagram's full-screen Reels player. Both markers were observed together in the
 * official Instagram app's accessibility hierarchy on the calibrated device. Requiring both
 * keeps normal feed posts and videos out of scope.
 */
internal object InstagramReelsDetector {
    private val reelsPlayerMarkers = setOf(
        "clips_viewer_view_pager",
        "clips_video_container",
    )

    fun isReelsPlayer(snapshot: ShortsDetector.UiSnapshot): Boolean {
        val identifiers = snapshot.identifiers.map { it.trim().lowercase() }
        return reelsPlayerMarkers.all { marker ->
            identifiers.any { identifier -> identifier.contains(marker) }
        }
    }
}

package com.ferrerkiwi.shortsguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class ShortsGuardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val boundaryHandler = Handler(Looper.getMainLooper())
    private val boundaryCheck = Runnable {
        if (ProtectionPreferences.isEnabled(this)) {
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == INSTAGRAM_PACKAGE) {
                handleInstagramFeedBoundary(root, System.currentTimeMillis())
            }
        }
    }
    private val shortsBlockPolicy = ShortsBlockPolicy(BLOCK_COOLDOWN_MS)
    private val reelsBlockPolicy = ShortsBlockPolicy(BLOCK_COOLDOWN_MS)
    private var lastYouTubeInspectionAt = 0L
    private var lastInstagramInspectionAt = 0L
    private var shortsIntentExpiresAt = 0L
    private var lastCaughtUpActionAt = 0L
    private var notice: TextView? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == INSTAGRAM_PACKAGE) {
            handleInstagramEvent(event)
            return
        }
        if (packageName != YOUTUBE_PACKAGE) return
        if (!ProtectionPreferences.isEnabled(this)) return

        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (ShortsDetector.isShortsEntry(event.source)) {
                shortsIntentExpiresAt = now + SHORTS_INTENT_WINDOW_MS
                shortsBlockPolicy.recordShortsIntent()
            }
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        if (now - lastYouTubeInspectionAt < INSPECTION_DEBOUNCE_MS) return

        lastYouTubeInspectionAt = now
        val root = rootInActiveWindow ?: return
        val snapshot = ShortsDetector.run { root.toUiSnapshot() }
        val hasRecentShortsIntent = now < shortsIntentExpiresAt

        if (ShortsDetector.isShortsPlayer(snapshot, hasRecentShortsIntent)) {
            if (!shortsBlockPolicy.shouldBlock(now)) return
            shortsIntentExpiresAt = 0L
            shortsBlockPolicy.recordBlock(now)
            performGlobalAction(GLOBAL_ACTION_BACK)
            showBlockedNotice("Shorts blocked")
        } else {
            shortsBlockPolicy.recordNonShortsSurface()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        boundaryHandler.removeCallbacks(boundaryCheck)
        removeBlockedNotice()
        super.onDestroy()
    }

    private fun handleInstagramEvent(event: AccessibilityEvent) {
        if (!ProtectionPreferences.isEnabled(this)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastInstagramInspectionAt < INSPECTION_DEBOUNCE_MS) return
        lastInstagramInspectionAt = now

        val root = rootInActiveWindow ?: return
        val snapshot = ShortsDetector.run { root.toUiSnapshot() }
        if (InstagramReelsDetector.isReelsPlayer(snapshot)) {
            boundaryHandler.removeCallbacks(boundaryCheck)
            if (!reelsBlockPolicy.shouldBlock(now)) return
            reelsBlockPolicy.recordBlock(now)
            performGlobalAction(GLOBAL_ACTION_BACK)
            showBlockedNotice("Reel blocked")
            return
        } else {
            reelsBlockPolicy.recordNonShortsSurface()
        }

        handleInstagramFeedBoundary(root, now)
    }

    private fun handleInstagramFeedBoundary(root: AccessibilityNodeInfo, now: Long) {
        val feed = findInstagramFeed(root)
        if (feed == null || !hasVisibleBoundary(feed)) {
            boundaryHandler.removeCallbacks(boundaryCheck)
            return
        }

        val remainingCooldown = CAUGHT_UP_ACTION_COOLDOWN_MS - (now - lastCaughtUpActionAt)
        if (remainingCooldown > 0) {
            scheduleBoundaryCheck(remainingCooldown)
            return
        }

        if (scrollInstagramFeedBackward(feed)) {
            lastCaughtUpActionAt = now
            showBlockedNotice("End of followed posts")
            scheduleBoundaryCheck(CAUGHT_UP_ACTION_COOLDOWN_MS)
        }
    }

    private fun scheduleBoundaryCheck(delayMs: Long) {
        boundaryHandler.removeCallbacks(boundaryCheck)
        boundaryHandler.postDelayed(boundaryCheck, delayMs)
    }

    private fun findInstagramFeed(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.viewIdResourceName == "android:id/list" && node.isVisibleToUser) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(stack::add)
            }
        }
        return null
    }

    private fun hasVisibleBoundary(feed: AccessibilityNodeInfo): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(feed)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.isVisibleToUser && InstagramCaughtUpDetector.isBoundary(
                    node.viewIdResourceName, node.text, node.contentDescription,
                )
            ) return true
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(stack::add)
            }
        }
        return false
    }

    private fun scrollInstagramFeedBackward(feed: AccessibilityNodeInfo): Boolean {
        if (feed.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id
            } && feed.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        ) return true

        // Some Instagram versions omit the feed's scroll action from Accessibility.
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.5f, height * 0.35f)
            lineTo(width * 0.5f, height * 0.75f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 350L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun showBlockedNotice(message: String) {
        removeBlockedNotice()
        val textView = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            setTextSize(16f)
            setPadding(36, 24, 36, 24)
            setBackgroundColor(Color.argb(230, 30, 30, 30))
        }
        notice = textView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 96
        }

        val windowManager = getSystemService(WindowManager::class.java)
        windowManager.addView(textView, params)
        handler.postDelayed(::removeBlockedNotice, NOTICE_DURATION_MS)
    }

    private fun removeBlockedNotice() {
        handler.removeCallbacksAndMessages(null)
        val currentNotice = notice ?: return
        notice = null
        runCatching {
            getSystemService(WindowManager::class.java).removeViewImmediate(currentNotice)
        }
    }

    private companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val INSPECTION_DEBOUNCE_MS = 150L
        const val SHORTS_INTENT_WINDOW_MS = 3_000L
        const val BLOCK_COOLDOWN_MS = 1_750L
        const val NOTICE_DURATION_MS = 1_000L
        const val CAUGHT_UP_ACTION_COOLDOWN_MS = 600L
    }
}

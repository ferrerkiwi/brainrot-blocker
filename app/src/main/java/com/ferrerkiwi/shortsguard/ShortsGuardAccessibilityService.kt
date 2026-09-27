package com.ferrerkiwi.shortsguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

class ShortsGuardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val boundaryHandler = Handler(Looper.getMainLooper())
    private val boundaryCheck = Runnable { returnToInstagramBoundary() }
    private val shortsBlockPolicy = ShortsBlockPolicy(BLOCK_COOLDOWN_MS)
    private val reelsBlockPolicy = ShortsBlockPolicy(BLOCK_COOLDOWN_MS)
    private var lastYouTubeInspectionAt = 0L
    private var lastInstagramInspectionAt = 0L
    private var shortsIntentExpiresAt = 0L
    private var boundaryReturnPending = false
    private var boundaryReturnAttempts = 0
    private var boundaryReturnInProgress = false
    private var boundarySamplePriority = 0
    private var boundarySampleTop: Int? = null
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
            stopBoundaryReturn()
            if (!reelsBlockPolicy.shouldBlock(now)) return
            reelsBlockPolicy.recordBlock(now)
            performGlobalAction(GLOBAL_ACTION_BACK)
            showBlockedNotice("Reel blocked")
            return
        } else {
            reelsBlockPolicy.recordNonShortsSurface()
        }

        handleInstagramFeedBoundary(root)
    }

    private fun handleInstagramFeedBoundary(root: AccessibilityNodeInfo) {
        val feed = findInstagramFeed(root)
        if (feed == null) {
            stopBoundaryReturn()
            return
        }
        val boundary = findVisibleBoundary(feed)
        if (boundary != null && !boundaryReturnPending) {
            boundaryReturnPending = true
            scheduleBoundaryCheck(BOUNDARY_SETTLE_MS)
        }
    }

    private fun returnToInstagramBoundary() {
        if (!boundaryReturnPending || boundaryReturnInProgress ||
            !ProtectionPreferences.isEnabled(this)
        ) return
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != INSTAGRAM_PACKAGE) {
            stopBoundaryReturn()
            return
        }
        val feed = findInstagramFeed(root)
        val boundary = feed?.let(::findVisibleBoundary)
        if (feed == null || boundary == null || boundaryReturnAttempts >= MAX_BOUNDARY_RETURN_ATTEMPTS) {
            stopBoundaryReturn()
            return
        }

        val feedBounds = Rect()
        val boundaryBounds = Rect()
        feed.getBoundsInScreen(feedBounds)
        boundary.getBoundsInScreen(boundaryBounds)
        val priority = InstagramCaughtUpDetector.priority(
            boundary.viewIdResourceName, boundary.text, boundary.contentDescription,
        )
        val previousTop = boundarySampleTop
        if (priority != boundarySamplePriority || previousTop == null ||
            kotlin.math.abs(boundaryBounds.top - previousTop) > BOUNDARY_STABLE_TOLERANCE_PX
        ) {
            boundarySamplePriority = priority
            boundarySampleTop = boundaryBounds.top
            scheduleBoundaryCheck(BOUNDARY_STABILITY_MS)
            return
        }
        val targetY = feedBounds.top + feedBounds.height() * 0.88f
        if (priority >= 3 && boundaryBounds.top >= targetY - BOUNDARY_TARGET_TOLERANCE_PX) {
            stopBoundaryReturn()
            return
        }

        if (scrollInstagramFeedBackward(feed, boundary, priority)) {
            boundaryReturnAttempts++
            if (boundaryReturnAttempts == 1) showBlockedNotice("End of followed posts")
        } else {
            stopBoundaryReturn()
        }
    }

    private fun scheduleBoundaryCheck(delayMs: Long) {
        boundaryHandler.removeCallbacks(boundaryCheck)
        boundaryHandler.postDelayed(boundaryCheck, delayMs)
    }

    private fun stopBoundaryReturn() {
        boundaryHandler.removeCallbacks(boundaryCheck)
        boundaryReturnPending = false
        boundaryReturnAttempts = 0
        boundarySamplePriority = 0
        boundarySampleTop = null
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

    private fun findVisibleBoundary(feed: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(feed)
        var best: AccessibilityNodeInfo? = null
        var bestPriority = 0
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val priority = if (node.isVisibleToUser) InstagramCaughtUpDetector.priority(
                    node.viewIdResourceName, node.text, node.contentDescription,
                ) else 0
            if (priority > bestPriority) {
                best = node
                bestPriority = priority
                if (priority == 4) return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(stack::add)
            }
        }
        return best
    }

    private fun scrollInstagramFeedBackward(
        feed: AccessibilityNodeInfo,
        boundary: AccessibilityNodeInfo,
        priority: Int,
    ): Boolean {
        val feedBounds = Rect()
        val boundaryBounds = Rect()
        feed.getBoundsInScreen(feedBounds)
        boundary.getBoundsInScreen(boundaryBounds)
        if (feedBounds.height() < 200 || feedBounds.width() < 100 || boundaryBounds.isEmpty) return false

        val x = feedBounds.exactCenterX()
        val startY = feedBounds.top + feedBounds.height() * 0.08f
        // Place the caught-up marker near the bottom, leaving the preceding post in view.
        val targetBoundaryY = feedBounds.top + feedBounds.height() * 0.88f
        val estimatedCaughtUpTop = when (priority) {
            2 -> boundaryBounds.top - feedBounds.height() * 0.28f
            1 -> boundaryBounds.top - feedBounds.height() * 0.8f
            else -> boundaryBounds.top.toFloat()
        }
        val desiredDistance = (targetBoundaryY - estimatedCaughtUpTop).coerceAtLeast(0f)
        if (desiredDistance < 40f) return false

        val canScrollBackward = feed.actionList.any {
            it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (canScrollBackward) {
            val amount = (desiredDistance / feedBounds.height()).coerceAtMost(3f)
            val args = Bundle().apply {
                putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, amount)
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, amount)
            }
            if (feed.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, args)) {
                boundarySampleTop = null
                scheduleBoundaryCheck(BOUNDARY_SCROLL_FOLLOW_UP_MS)
                return true
            }
        }

        // Fall back to a held touch gesture if this Instagram feed omits its scroll action.
        val distance = desiredDistance.coerceAtMost(feedBounds.height() * 0.82f)
        val endY = startY + distance
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        // Keep the finger down briefly at the end so the drag stops without fling momentum.
        val drag = GestureDescription.StrokeDescription(path, 0L, BOUNDARY_DRAG_DURATION_MS, true)
        val gesture = GestureDescription.Builder()
            .addStroke(drag)
            .build()
        boundaryReturnInProgress = true
        val started = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                val holdPath = Path().apply { moveTo(x, endY) }
                val hold = drag.continueStroke(holdPath, 0L, BOUNDARY_HOLD_DURATION_MS, false)
                val holdGesture = GestureDescription.Builder().addStroke(hold).build()
                val holding = dispatchGesture(holdGesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        boundaryReturnInProgress = false
                        if (boundaryReturnPending) {
                            boundarySampleTop = null
                            scheduleBoundaryCheck(BOUNDARY_FOLLOW_UP_MS)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        boundaryReturnInProgress = false
                        stopBoundaryReturn()
                    }
                }, handler)
                if (!holding) {
                    boundaryReturnInProgress = false
                    stopBoundaryReturn()
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                boundaryReturnInProgress = false
                stopBoundaryReturn()
            }
        }, handler)
        if (!started) boundaryReturnInProgress = false
        return started
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
        const val BOUNDARY_SETTLE_MS = 300L
        const val BOUNDARY_STABILITY_MS = 150L
        const val BOUNDARY_STABLE_TOLERANCE_PX = 20
        const val BOUNDARY_FOLLOW_UP_MS = 100L
        const val BOUNDARY_SCROLL_FOLLOW_UP_MS = 450L
        const val BOUNDARY_TARGET_TOLERANCE_PX = 100f
        const val MAX_BOUNDARY_RETURN_ATTEMPTS = 3
        const val BOUNDARY_DRAG_DURATION_MS = 450L
        const val BOUNDARY_HOLD_DURATION_MS = 200L
    }
}

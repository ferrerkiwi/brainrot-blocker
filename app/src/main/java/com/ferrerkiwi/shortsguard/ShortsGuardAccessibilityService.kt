package com.ferrerkiwi.shortsguard

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class ShortsGuardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val shortsBlockPolicy = ShortsBlockPolicy(BLOCK_COOLDOWN_MS)
    private val reelsBlockPolicy = ShortsBlockPolicy(BLOCK_COOLDOWN_MS)
    private var lastYouTubeInspectionAt = 0L
    private var lastInstagramInspectionAt = 0L
    private var shortsIntentExpiresAt = 0L
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
        removeBlockedNotice()
        super.onDestroy()
    }

    private fun handleInstagramEvent(event: AccessibilityEvent) {
        if (!ProtectionPreferences.isEnabled(this)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastInstagramInspectionAt < INSPECTION_DEBOUNCE_MS) return
        lastInstagramInspectionAt = now

        val root = rootInActiveWindow ?: return
        val snapshot = ShortsDetector.run { root.toUiSnapshot() }
        if (InstagramReelsDetector.isReelsPlayer(snapshot)) {
            if (!reelsBlockPolicy.shouldBlock(now)) return
            reelsBlockPolicy.recordBlock(now)
            performGlobalAction(GLOBAL_ACTION_BACK)
            showBlockedNotice("Reel blocked")
        } else {
            reelsBlockPolicy.recordNonShortsSurface()
        }
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
    }
}

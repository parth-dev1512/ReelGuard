package com.kaamkarle.reelguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelGuardService : AccessibilityService() {

    companion object {
        const val REEL_LIMIT = 4
        private const val IG = "com.instagram.android"

        /** After a kick-out, opening Reels again within this window kicks you out instantly. */
        private const val COOLDOWN_MS = 5 * 60 * 1000L

        /** If you don't touch Reels for this long, the counter starts over. */
        private const val RESET_AFTER_MS = 30 * 60 * 1000L

        /** Several scroll events fire per swipe; ignore repeats closer than this. */
        private const val SWIPE_DEBOUNCE_MS = 700L

        private val REELS_VIEW_IDS = listOf(
            "$IG:id/clips_viewer_view_pager",
            "$IG:id/clips_viewer_container",
        )
    }

    private var reelCount = 0
    private var lastIndex = -1
    private var lastCountedAt = 0L
    private var lastReelActivityAt = 0L
    private var blockedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != IG) return
        val now = SystemClock.elapsedRealtime()

        if (!isInReels(event)) return

        // Still cooling down from the last kick-out → straight back out.
        if (blockedAt != 0L && now - blockedAt < COOLDOWN_MS) {
            kickOut(now)
            return
        }

        if (now - lastReelActivityAt > RESET_AFTER_MS) resetCounter()
        lastReelActivityAt = now

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        // Prefer the pager's item index when Instagram reports it; fall back to timing.
        val index = event.fromIndex
        val isNewReel = if (index >= 0) {
            val changed = index != lastIndex
            lastIndex = index
            changed && now - lastCountedAt > 250
        } else {
            now - lastCountedAt > SWIPE_DEBOUNCE_MS
        }
        if (!isNewReel) return

        lastCountedAt = now
        reelCount++

        if (reelCount >= REEL_LIMIT) kickOut(now)
    }

    private fun isInReels(event: AccessibilityEvent): Boolean {
        val srcId = event.source?.viewIdResourceName
        if (srcId != null && srcId.contains("clips_viewer")) return true

        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return false
        return REELS_VIEW_IDS.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
    }

    private fun kickOut(now: Long) {
        blockedAt = now
        resetCounter()
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun resetCounter() {
        reelCount = 0
        lastIndex = -1
    }

    override fun onInterrupt() {}
}

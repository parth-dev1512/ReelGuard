package com.kaamkarle.reelguard

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class ReelGuardService : AccessibilityService() {

    companion object {
        const val REEL_LIMIT = 4
        const val PREFS = "reelguard"
        private const val IG = "com.instagram.android"
        private const val TASKS = "com.google.android.apps.tasks"

        /** After a kick-out, opening Reels again within this window kicks you out instantly. */
        private const val COOLDOWN_MS = 5 * 60 * 1000L

        /** If you don't touch Reels for this long, the counter starts over. */
        private const val RESET_AFTER_MS = 30 * 60 * 1000L

        /** Several scroll events fire per swipe; ignore repeats closer than this. */
        private const val SWIPE_DEBOUNCE_MS = 700L

        /** Any view id containing one of these means we're on a Reels screen. */
        private val REELS_ID_HINTS = listOf("clips_viewer", "clips_video", "clips_ufi", "reel_viewer")

        /** Set while the service is running, so the main screen can trigger a test. */
        var instance: ReelGuardService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null

    private var reelCount = 0
    private var lastIndex = -1
    private var lastCountedAt = 0L
    private var lastReelActivityAt = 0L
    private var blockedAt = 0L

    override fun onServiceConnected() {
        instance = this
        saveDebug("Service running. Open Instagram Reels and swipe.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != IG) return
        if (overlay != null) return
        val now = SystemClock.elapsedRealtime()

        val reelsId = findReelsId(event) ?: return

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
        saveDebug("Reels counted: $reelCount / $REEL_LIMIT\n(detected via $reelsId)")

        if (reelCount >= REEL_LIMIT) kickOut(now)
    }

    /** Returns the matching view id if we're on a Reels screen, else null. */
    private fun findReelsId(event: AccessibilityEvent): String? {
        event.source?.viewIdResourceName?.let { id ->
            if (REELS_ID_HINTS.any { id.contains(it) }) return id
        }
        val root = rootInActiveWindow ?: return null
        return searchTree(root, 0, intArrayOf(0))
    }

    private fun searchTree(node: AccessibilityNodeInfo, depth: Int, visited: IntArray): String? {
        if (depth > 25 || visited[0]++ > 400) return null
        node.viewIdResourceName?.let { id ->
            if (REELS_ID_HINTS.any { id.contains(it) }) return id
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            searchTree(child, depth + 1, visited)?.let { return it }
        }
        return null
    }

    private fun kickOut(now: Long) {
        blockedAt = now
        resetCounter()
        saveDebug("Kicked out! Cooldown active for 5 min.")
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Small delay so the home screen is up before we draw on top of it.
        main.postDelayed({ showOverlay() }, 300)
    }

    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val image = ImageView(this).apply {
            setImageResource(R.drawable.lock_in)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val big = TextView(this).apply {
            text = "Kaam karle"
            textSize = 34f
            setPadding(0, 32, 0, 0)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val small = TextView(this).apply {
            text = "$REEL_LIMIT reels ho gaye. Phone rakh."
            textSize = 18f
            setTextColor(Color.parseColor("#FFD27A"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 48)
        }
        val button = Button(this).apply {
            text = "Close → Google Tasks"
            setOnClickListener { closeOverlay() }
        }

        val root = object : LinearLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    closeOverlay()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(32, 96, 32, 96)
            isFocusableInTouchMode = true
            addView(image)
            addView(big)
            addView(small)
            addView(button)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.OPAQUE
        )

        try {
            wm.addView(root, params)
            overlay = root
            root.requestFocus()
        } catch (e: Exception) {
            saveDebug("Couldn't draw overlay: ${e.message}")
        }
    }

    private fun closeOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (ignored: Exception) {}
        openGoogleTasks()
    }

    private fun openGoogleTasks() {
        val launch = packageManager.getLaunchIntentForPackage(TASKS)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TASKS"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (ignored: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TASKS"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun resetCounter() {
        reelCount = 0
        lastIndex = -1
    }

    private fun saveDebug(msg: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("debug", msg).apply()
    }

    override fun onInterrupt() {}

    /** Shows the block screen right now, without touching the cooldown. */
    fun showTest() {
        main.post { showOverlay() }
    }

    override fun onDestroy() {
        instance = null
        overlay?.let {
            try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (ignored: Exception) {}
        }
        overlay = null
        super.onDestroy()
    }
}

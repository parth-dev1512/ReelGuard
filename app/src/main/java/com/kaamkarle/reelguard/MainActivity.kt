package com.kaamkarle.reelguard

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var debug: TextView
    private lateinit var paymentStatus: TextView
    private lateinit var prefs: SharedPreferences

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(ReelGuardService.PREFS, MODE_PRIVATE)

        status = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val help = TextView(this).apply {
            text = "After ${ReelGuardService.REEL_LIMIT} reels, Instagram gets closed and the block screen shows. " +
                "Closing it opens Google Tasks. Opening Reels again within 5 minutes kicks you out instantly."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 32, 0, 32)
        }
        val settingsBtn = Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val testBtn = Button(this).apply {
            text = "Test block screen"
            setOnClickListener {
                val svc = ReelGuardService.instance
                if (svc == null) {
                    Toast.makeText(this@MainActivity, "Turn ReelGuard ON in Accessibility first", Toast.LENGTH_LONG).show()
                } else {
                    svc.showTest()
                }
            }
        }
        val pauseBtn = Button(this).apply {
            text = "Pause for ${PaymentPause.MANUAL_PAUSE_MS / 60_000} min (for other payment apps)"
            setOnClickListener {
                val svc = ReelGuardService.instance
                if (svc == null) {
                    Toast.makeText(this@MainActivity, "ReelGuard is already off", Toast.LENGTH_SHORT).show()
                } else {
                    svc.pauseFor(PaymentPause.MANUAL_PAUSE_MS, "pause button")
                    refresh()
                }
            }
        }
        paymentStatus = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 32, 0, 0)
            setTextIsSelectable(true)
        }
        debug = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9AD0FF"))
            setPadding(0, 48, 0, 0)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
            addView(status)
            addView(help)
            addView(settingsBtn)
            addView(testBtn)
            addView(pauseBtn)
            addView(paymentStatus)
            addView(debug)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#121212"))
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun refresh() {
        paymentStatus.text = if (PaymentPause.canTurnBackOn(this)) {
            "💳 Paytm / UPI apps: ReelGuard switches off when they open and comes back on by itself."
        } else {
            "💳 Paytm / UPI apps: ReelGuard switches off when they open, but you'll have to turn it " +
                "back on yourself. To make that automatic, run once from a laptop:\n\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        }
        val on = isServiceOn()
        status.text = if (on) "✅ ReelGuard is ON" else "❌ ReelGuard is OFF"
        debug.text = if (on) {
            "Last activity:\n" + (prefs.getString("debug", null) ?: "Nothing yet. Open Instagram Reels and swipe.")
        } else {
            "Turn it on: Accessibility → Downloaded/Installed apps → ReelGuard.\n" +
                "Toggle greyed out? App info → ⋮ → Allow restricted settings."
        }
    }

    private fun isServiceOn(): Boolean {
        val me = ComponentName(this, ReelGuardService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}

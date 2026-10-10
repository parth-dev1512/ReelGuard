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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var debug: TextView
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
        debug = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9AD0FF"))
            setPadding(0, 48, 0, 0)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
            addView(status)
            addView(help)
            addView(settingsBtn)
            addView(testBtn)
            addView(debug)
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

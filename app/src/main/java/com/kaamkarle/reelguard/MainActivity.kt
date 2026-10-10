package com.kaamkarle.reelguard

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val help = TextView(this).apply {
            text = "Turn on ReelGuard in Accessibility settings.\n\n" +
                "After ${ReelGuardService.REEL_LIMIT} reels, Instagram gets closed. " +
                "Open Reels again within 5 minutes and you get kicked out instantly."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 32, 0, 32)
        }
        val button = Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
            addView(status)
            addView(help)
            addView(button)
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (isServiceOn()) "✅ ReelGuard is ON" else "❌ ReelGuard is OFF"
    }

    private fun isServiceOn(): Boolean {
        val me = ComponentName(this, ReelGuardService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}

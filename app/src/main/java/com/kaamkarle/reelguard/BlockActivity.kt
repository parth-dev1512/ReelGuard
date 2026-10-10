package com.kaamkarle.reelguard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val big = TextView(this).apply {
            text = "Kaam karle"
            textSize = 44f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val small = TextView(this).apply {
            text = "${ReelGuardService.REEL_LIMIT} reels ho gaye. Phone rakh."
            textSize = 18f
            setTextColor(Color.parseColor("#FFB3B3"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B00020"))
            setPadding(48, 48, 48, 48)
            addView(big)
            addView(small)
        })

        // Close itself after a few seconds, leaving you on the home screen.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3500)
    }
}

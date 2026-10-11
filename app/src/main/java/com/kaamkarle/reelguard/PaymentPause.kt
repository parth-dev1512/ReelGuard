package com.kaamkarle.reelguard

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings

/**
 * Paytm and most UPI / banking apps refuse to work while any third-party
 * accessibility service is on. So when one of them opens, ReelGuard switches
 * itself off and schedules itself to come back on a few minutes later.
 *
 * Switching off needs nothing special. Switching back on needs the one-time
 * WRITE_SECURE_SETTINGS grant over adb (see README). Without it, ReelGuard
 * still gets out of the way, but you have to turn it back on yourself.
 */
object PaymentPause {

    /** Apps that block payments while an accessibility service is on. Add your bank's app here. */
    val PAYMENT_APPS = setOf(
        "net.one97.paytm",                       // Paytm
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                        // PhonePe
        "in.org.npci.upiapp",                     // BHIM
        "in.amazon.mShop.android.shopping",       // Amazon (Amazon Pay)
        "com.sbi.lotusintouch",                   // SBI YONO
        "com.csam.icici.bank.imobile",            // ICICI iMobile
        "com.snapwork.hdfc",                      // HDFC
        "com.axis.mobile",                        // Axis
        "com.msf.kbank.mobile",                   // Kotak
    )

    /** How long ReelGuard stays off after a payment app opens. Checked again when it comes back. */
    const val PAYMENT_PAUSE_MS = 3 * 60 * 1000L

    /** The "Pause" button on the main screen. */
    const val MANUAL_PAUSE_MS = 10 * 60 * 1000L

    fun canTurnBackOn(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Puts ReelGuard back in the enabled accessibility services list. */
    fun turnOn(ctx: Context): Boolean {
        if (!canTurnBackOn(ctx)) return false
        val me = ComponentName(ctx, ReelGuardService::class.java).flattenToString()
        val cr = ctx.contentResolver
        return try {
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val services = current.split(':').filter { it.isNotBlank() }.toMutableList()
            if (services.none { it.equals(me, ignoreCase = true) }) services.add(me)
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun scheduleTurnOn(ctx: Context, delayMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            turnOnIntent(ctx)
        )
    }

    fun cancelScheduledTurnOn(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(turnOnIntent(ctx))
    }

    private fun turnOnIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, TurnBackOnReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

class TurnBackOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PaymentPause.turnOn(context)
    }
}

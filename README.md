# ReelGuard

Swipe 4 Instagram Reels → Instagram gets sent to the background and a full-screen
"Kaam karle" ("get to work") shows up. Close it (button or back) and Google Tasks opens.
Open Reels again within 5 minutes and you get kicked out instantly.

## Checking it works

Open ReelGuard after turning it on:
- **Test block screen** shows the screen right away, so you know the screen part works.
- **Last activity** updates live as you swipe Reels. If it never changes, Reels aren't being
  detected — send the developer your Instagram version.

## Build

**Android Studio:** File → Open → this folder → Run (or Build → Build APK).

**No PC?** Push this folder to a GitHub repo. The included workflow builds the APK —
go to the repo's Actions tab → latest run → download `ReelGuard-apk`.

## Turn it on

1. Install the APK (allow "install unknown apps" when asked).
2. Open ReelGuard → **Open Accessibility settings** → Downloaded apps → ReelGuard → On.
   - Android 13+: if the toggle is greyed out, go to App info → ReelGuard → ⋮ → **Allow restricted settings**, then try again.
3. Some phones (Xiaomi, Oppo, Vivo, Realme) kill background services. Set ReelGuard's battery to **No restrictions**.

## Paytm, Google Pay, PhonePe and bank apps

These apps refuse payments while any non-system accessibility service is on, as protection
against remote-control scams. So ReelGuard switches itself **off** the moment one of them opens,
then turns itself back **on** about 3 minutes later. If the payment app is still open when it comes
back, it switches off again.

Turning itself back on needs a one-time permission. Connect the phone to a laptop with USB
debugging on (Xiaomi/Redmi/Poco: also turn on "USB debugging (Security settings)") and run:

```
adb shell pm grant com.kaamkarle.reelguard android.permission.WRITE_SECURE_SETTINGS
```

You only do this once per install. Without it, ReelGuard still gets out of Paytm's way, but you
have to turn it back on yourself in Accessibility settings.

The list of apps is `PAYMENT_APPS` in `PaymentPause.kt`. Add your bank's app there **and** to
`packageNames` in `res/xml/reel_guard_service.xml`. For a one-off, use the **Pause for 10 min**
button in ReelGuard.

## Tweaks

All in `ReelGuardService.kt`:

- `REEL_LIMIT` — reels before kick-out (4)
- `COOLDOWN_MS` — instant kick-out window after a block (5 min)
- `RESET_AFTER_MS` — counter starts over after this long away from Reels (30 min)

The message and the Google Tasks redirect are in `showOverlay()` / `openGoogleTasks()` in the same file.

## If it stops detecting Reels

Instagram sometimes renames its internal views. The service looks for view IDs containing
`clips_viewer`. If an update breaks it, check the IDs with a layout inspector and update
`REELS_ID_HINTS`.

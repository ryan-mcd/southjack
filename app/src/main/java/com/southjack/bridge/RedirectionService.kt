package com.southjack.bridge

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * The core of SouthJack. Android invokes [onPlaceCall] on every outgoing call
 * while the app holds the CALL_REDIRECTION role.
 *
 * When Cell2Jack dials from the landline, Android places the call on the cellular
 * account (no SIM → "No mobile network"). We intercept it, cancel that dead
 * attempt, and hand the number to Google Voice (ACTION_CALL) so it places the call
 * over Wi-Fi. Audio follows to Cell2Jack because it's the active BT headset.
 *
 * Launch path depends on screen state:
 *  - unlocked & interactive → direct startActivity (fast, hands-free).
 *  - locked or screen-off   → full-screen-intent notification, the only way the
 *    system lets a background app show call UI over the lockscreen (BAL rules).
 */
class RedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean
    ) {
        Log.i(TAG, "Outgoing call received; destination and account redacted.")

        if (Settings.routeViaGoogleVoice(this)) {
            routeThroughGoogleVoice(handle)
            return
        }

        val target = Settings.getTarget(this)
        if (target != null && target != initialPhoneAccount) {
            Log.i(TAG, "Redirecting to the configured account.")
            redirectCall(handle, target, false)
        } else {
            Log.i(TAG, "No routing configured -> placing call unmodified.")
            placeCallUnmodified()
        }
    }

    private fun routeThroughGoogleVoice(handle: Uri) {
        val gvIntent = Intent(Intent.ACTION_CALL, handle).apply {
            setPackage(Settings.GOOGLE_VOICE_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val locked = km.isKeyguardLocked || !pm.isInteractive

        try {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Call permission is required")
            }
            if (locked) {
                Log.i(TAG, "Screen locked/off -> launching GV via full-screen intent.")
                launchViaFullScreenIntent(gvIntent)
            } else {
                Log.i(TAG, "Screen unlocked -> launching GV directly.")
                startActivity(gvIntent)
            }
            cancelCall()   // kill the dead cellular attempt (no "No mobile network")
        } catch (_: Exception) {
            // Exception messages can include the destination URI; keep logs redacted.
            Log.e(TAG, "Could not hand call to Google Voice; placing unmodified.")
            placeCallUnmodified()
        }
    }

    /**
     * Posts a high-importance notification whose full-screen intent is the
     * ACTION_CALL to Google Voice. When the device is locked or the screen is off,
     * the system launches this immediately over the keyguard — which a plain
     * startActivity from the background is not permitted to do.
     */
    private fun launchViaFullScreenIntent(gvIntent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Notification permission is required")
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Landline call bridge", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Places landline calls through Google Voice" }
        )

        val pi = PendingIntent.getActivity(
            this, 0, gvIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Calling via Google Voice")
            .setContentText("Connecting your landline call")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    override fun onRedirectionTimeout() {
        Log.w(TAG, "onRedirectionTimeout — Telecom will place the call unmodified.")
    }

    companion object {
        const val TAG = "SouthJack"
        private const val CHANNEL_ID = "southjack_call_bridge"
        private const val NOTIF_ID = 42
    }
}

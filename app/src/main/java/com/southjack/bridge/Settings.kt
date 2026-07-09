package com.southjack.bridge

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle

/**
 * Persists SouthJack's routing config.
 *
 * Two mechanisms:
 *  - [routeViaGoogleVoice] (the working path): cancel the cellular call and fire an
 *    ACTION_CALL intent at Google Voice, which places the VoIP call. GV registers
 *    only a *self-managed* ConnectionService, so it has no PhoneAccount we could
 *    target directly — the intent is how we reach it.
 *  - [getTarget]/[setTarget]: redirect to a real callable PhoneAccount, for future
 *    providers (OpenPhone/SIP/etc.) that DO register a targetable calling account.
 */
object Settings {
    const val GOOGLE_VOICE_PKG = "com.google.android.apps.googlevoice"

    private const val PREFS = "southjack"
    private const val KEY_CMP = "target_component"
    private const val KEY_ID = "target_id"
    private const val KEY_ROUTE_GV = "route_via_gv"

    // --- Google Voice intent routing (default ON) --------------------------------

    fun routeViaGoogleVoice(ctx: Context): Boolean =
        ctx.prefs().getBoolean(KEY_ROUTE_GV, true)

    fun setRouteViaGoogleVoice(ctx: Context, on: Boolean) {
        ctx.prefs().edit().putBoolean(KEY_ROUTE_GV, on).apply()
    }

    // --- PhoneAccount redirect target (future providers) -------------------------

    fun setTarget(ctx: Context, handle: PhoneAccountHandle) {
        ctx.prefs().edit()
            .putString(KEY_CMP, handle.componentName.flattenToString())
            .putString(KEY_ID, handle.id)
            .apply()
    }

    fun getTarget(ctx: Context): PhoneAccountHandle? {
        val p = ctx.prefs()
        val cmp = p.getString(KEY_CMP, null) ?: return null
        val id = p.getString(KEY_ID, null) ?: return null
        val component = ComponentName.unflattenFromString(cmp) ?: return null
        return PhoneAccountHandle(component, id)
    }

    fun clearTarget(ctx: Context) {
        ctx.prefs().edit().remove(KEY_CMP).remove(KEY_ID).apply()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

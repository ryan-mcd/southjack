package com.southjack.bridge

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * SouthJack control + diagnostic screen. Programmatic UI (no resource wiring) to
 * keep the build surface minimal.
 */
class DiagnosticActivity : Activity() {

    private lateinit var container: LinearLayout
    private val tm: TelecomManager by lazy { getSystemService(TelecomManager::class.java) }
    private val rm: RoleManager by lazy { getSystemService(RoleManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(container)
        setContentView(scroll)

        ensurePermissions()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) render()
    }

    private fun render() {
        container.removeAllViews()

        header("SouthJack — Landline → Google Voice bridge")

        // 1. Call-redirection role -------------------------------------------------
        header("1. Call redirection app")
        val holdsRole = rm.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)
        body(if (holdsRole) "✓ SouthJack IS the call-redirection app." else "✗ Not yet the call-redirection app.")
        button(if (holdsRole) "Re-request redirection role" else "Make SouthJack the Call Redirection app") {
            requestRedirectionRole()
        }

        // 2. Routing ---------------------------------------------------------------
        header("2. Google Voice routing")
        val gvInstalled = isInstalled(Settings.GOOGLE_VOICE_PKG)
        val canCall = has(Manifest.permission.CALL_PHONE)
        val canNotify = has(Manifest.permission.POST_NOTIFICATIONS)
        body("Google Voice installed: ${check(gvInstalled)}    Call permission: ${check(canCall)}")
        body("Lock-screen calling (notifications): ${check(canNotify)}")
        if (!canNotify) {
            button("Grant notification permission") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_PERM)
            }
        }

        val routing = Settings.routeViaGoogleVoice(this)
        body(
            if (routing) "✓ Landline dials are routed to Google Voice (cancel cellular → GV places the VoIP call)."
            else "✗ Routing OFF — calls placed unmodified."
        )
        button(if (routing) "Turn OFF Google Voice routing" else "Turn ON Google Voice routing") {
            Settings.setRouteViaGoogleVoice(this, !routing); render()
        }
        if (!canCall) {
            button("Grant Call permission") {
                requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_PERM)
            }
        }

        val ready = holdsRole && gvInstalled && canCall && canNotify && routing
        body(if (ready) "→ Ready. Dial from the landline to test." else "→ Fix the ✗ items above, then dial.")

        // 3. Accounts (diagnostic) -------------------------------------------------
        header("3. Callable phone accounts (diagnostic)")
        listPhoneAccounts()

        // 4. Test ------------------------------------------------------------------
        header("4. Run the test")
        body(
            "In Android Studio Logcat, filter tag:SouthJack. Dial a number from the " +
                "landline keypad. Expect:\n" +
                "  • onPlaceCall FIRED  number=tel:…\n" +
                "  • Launched Google Voice … cancelling the cellular attempt\n\n" +
                "Then watch the phone: Google Voice should come up and place the call. " +
                "Paste the log + say what the phone did."
        )
    }

    // --- roles / permissions ------------------------------------------------------

    private fun ensurePermissions() {
        val need = mutableListOf<String>()
        if (!has(Manifest.permission.READ_PHONE_STATE)) need += Manifest.permission.READ_PHONE_STATE
        if (!has(Manifest.permission.CALL_PHONE)) need += Manifest.permission.CALL_PHONE
        if (!has(Manifest.permission.POST_NOTIFICATIONS)) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERM)
    }

    private fun requestRedirectionRole() {
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION)) {
            body("This device does not offer the call-redirection role.")
            return
        }
        startActivityForResult(
            rm.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION),
            REQ_ROLE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        render()
    }

    // --- account listing ----------------------------------------------------------

    private fun listPhoneAccounts() {
        val handles: List<PhoneAccountHandle> = try {
            tm.callCapablePhoneAccounts
        } catch (e: SecurityException) {
            body("Need phone permission to list accounts.")
            emptyList()
        }
        if (handles.isEmpty()) {
            body("No callable accounts (Google Voice registers a self-managed service, so it won't appear here — expected).")
            return
        }
        for (h in handles) {
            val acct: PhoneAccount? = try { tm.getPhoneAccount(h) } catch (e: SecurityException) { null }
            val label = acct?.label?.toString().orEmpty()
            body("• ${if (label.isNotBlank()) label else h.id}\n   pkg: ${h.componentName.packageName}   id: ${h.id}")
        }
    }

    // --- helpers ------------------------------------------------------------------

    private fun has(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun check(b: Boolean) = if (b) "✓" else "✗"

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    private fun header(t: String) = container.addView(TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(Color.BLACK)
        setPadding(0, dp(18), 0, dp(6))
    })

    private fun body(t: String) = container.addView(TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(2), 0, dp(2))
    })

    private fun button(label: String, onClick: () -> Unit) = container.addView(Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(6)
        layoutParams = lp
    })

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private companion object {
        const val REQ_PERM = 1001
        const val REQ_ROLE = 1002
    }
}

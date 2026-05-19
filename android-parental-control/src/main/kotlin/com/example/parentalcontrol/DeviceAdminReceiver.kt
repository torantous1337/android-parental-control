package com.example.parentalcontrol

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar

/**
 * AdminReceiver  —  v4  (non-DO / "civilian" deployment)
 *
 * Changes from v3:
 * • All Device Owner-exclusive calls REMOVED:
 *     setUninstallBlocked(), setLockTaskPackages(), addUserRestriction(),
 *     setAlwaysOnVpnPackage(), setApplicationExemptions(),
 *     setPackagesSuspended(), DISALLOW_CONFIG_VPN.
 *
 * • What remains are standard DeviceAdminReceiver APIs available to any
 *   app granted Device Administration (not DO):
 *     - setPasswordQuality / setMaximumFailedPasswordsForWipe (optional)
 *     - onDisableRequested() warning string + PinLockActivity redirect
 *
 * • Supervised-hours Settings-suspension logic also removed — without DO
 *   rights setPackagesSuspended() throws a SecurityException.
 *
 * Persistence in this model comes from:
 *   • ForegroundMonitorService (UsageStats-based app blocking)
 *   • The VPN always-on setting, which the parent configures manually in
 *     Settings once — the VpnService itself stays alive via START_STICKY
 *     and the WatchdogService Binder DeathRecipient loop.
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, AdminReceiver::class.java)
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "onEnabled: Device Admin rights granted (non-DO mode)")
        applyBaselinePolicy(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "onDisabled: Device Admin rights revoked — controls inactive")
        context.stopService(Intent(context, ParentalControlVpnService::class.java))
        context.stopService(Intent(context, ForegroundMonitorService::class.java))
    }

    /**
     * Called by the framework when the user taps "Deactivate" in
     * Settings → Security → Device Admin Apps.
     *
     * This is the only hook available without DO rights to intercept a
     * deactivation attempt.  The return value is displayed as a warning
     * dialog before the framework proceeds.  We also launch PinLockActivity
     * to interrupt the user's flow — they must authenticate before the
     * framework's own confirmation dialog is shown.
     *
     * Note: the framework WILL eventually deactivate the admin if the user
     * presses through.  This is friction, not a hard block — DO rights are
     * required for a hard block.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "onDisableRequested: deactivation attempt intercepted")

        // Launch PIN lock to interrupt the user's flow.
        // PinLockActivity is a placeholder — implement PIN verification there.
        val pinIntent = Intent(context, PinLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(PinLockActivity.EXTRA_REASON, PinLockActivity.REASON_ADMIN_DISABLE)
        }
        context.startActivity(pinIntent)

        return "Parental controls are active on this device. " +
               "A parent PIN is required to disable them."
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        Log.d(TAG, "onPasswordChanged")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        Log.w(TAG, "onPasswordFailed")
    }

    // =========================================================================
    // Baseline policy  (non-DO safe calls only)
    // =========================================================================

    private fun applyBaselinePolicy(context: Context) {
        val dpm   = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        val admin = getComponentName(context)

        // Require at minimum a PIN/pattern to unlock the device.
        // Available to any Device Admin, not DO-exclusive.
        dpm.setPasswordQuality(
            admin,
            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
        )

        // Wipe after 10 consecutive wrong PIN attempts.
        // Remove if the deployment policy does not call for remote wipe.
        dpm.setMaximumFailedPasswordsForWipe(admin, 10)

        Log.i(TAG, "applyBaselinePolicy: non-DO baseline applied")

        // Start the usage-stats foreground monitor.
        context.startForegroundService(
            Intent(context, ForegroundMonitorService::class.java)
        )
    }
}

// =============================================================================
// SupervisionAlarmReceiver — kept for future use but no-op without DO rights.
// setPackagesSuspended() requires DO; remove if civilian mode is permanent.
// =============================================================================

class SupervisionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("SupervisionAlarm",
            "Received ${intent.action} but setPackagesSuspended requires DO rights — no-op")
    }
}

package com.example.parentalcontrol

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * ForegroundMonitorService
 *
 * Polls UsageStatsManager every POLL_INTERVAL_MS to detect which package is
 * in the foreground.  If the foreground package is in the active blocklist,
 * BlockerActivity is launched over it immediately.
 *
 * Requires:
 *   • android.permission.PACKAGE_USAGE_STATS (user must grant in
 *     Settings → Apps → Special app access → Usage access)
 *   • Declared as a foreground service with a persistent notification so the
 *     OS does not kill the polling loop on memory pressure.
 *
 * The blocklist is intentionally a simple in-memory set here.  In production,
 * source it from a Room DB table or a remote policy endpoint so the parent
 * can change rules without a new APK.
 */
class ForegroundMonitorService : Service() {

    companion object {
        private const val TAG              = "ForegroundMonitor"
        private const val POLL_INTERVAL_MS = 1_500L   // 1.5 s — balance latency vs battery
        private const val CHANNEL_ID       = "monitor_channel"
        private const val NOTIF_ID         = 1002

        // ── Mock blocklist ────────────────────────────────────────────────
        // Replace / extend with a Room-backed or server-pushed list.
        val BLOCKED_PACKAGES: Set<String> = setOf(
            "com.zhiliaoapp.musically",   // TikTok
            "com.instagram.android",
            "com.snapchat.android",
            "com.discord",
            "com.reddit.frontpage"
        )
    }

    private lateinit var usageStats:  UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedPackage: String? = null

    // ── Polling runnable ─────────────────────────────────────────────────────

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate() {
        super.onCreate()
        usageStats = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        startForeground(NOTIF_ID, NotificationHelper.buildSilentNotification(
            this, CHANNEL_ID,
            "Parental Control",
            "Monitoring app usage"
        ))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        Log.i(TAG, "polling started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "polling stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =========================================================================
    // Foreground detection
    // =========================================================================

    /**
     * Query UsageEvents for the last two seconds and return the most recent
     * MOVE_TO_FOREGROUND event's package.
     *
     * UsageStatsManager.queryUsageStats() aggregates by time bucket and can
     * lag by up to a minute on some OEMs; querying raw UsageEvents is more
     * reliable for real-time detection.
     */
    private fun foregroundPackage(): String? {
            val now = System.currentTimeMillis()
            val start = now - 10_000L   // 10-second lookback for robust state reconstruction
            val events = usageStats.queryEvents(start, now) ?: return null
    
            val event = UsageEvents.Event()
            var currentForeground: String? = null
    
            // Replay the event stream. The last package to throw a FOREGROUND event 
            // WITHOUT throwing a subsequent BACKGROUND event is our active target.
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        currentForeground = event.packageName
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (currentForeground == event.packageName) {
                            currentForeground = null
                        }
                    }
                }
            }
            return currentForeground
        }

    private fun checkForeground() {
        val pkg = foregroundPackage() ?: return

        // Skip our own package — BlockerActivity launching itself would loop.
        if (pkg == packageName) return

        if (pkg in BLOCKED_PACKAGES) {
            if (pkg != lastBlockedPackage) {
                // Only log on transition, not every 1.5 s tick, to avoid spam.
                Log.w(TAG, "BLOCKED: $pkg — launching BlockerActivity")
                lastBlockedPackage = pkg
            }
            launchBlocker(pkg)
        } else {
            lastBlockedPackage = null
        }
    }

    private fun launchBlocker(blockedPackage: String) {
        val intent = Intent(this, BlockerActivity::class.java).apply {
            putExtra(BlockerActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            // FLAG_ACTIVITY_NEW_TASK   — required when starting from a Service
            // FLAG_ACTIVITY_CLEAR_TASK — ensures no back-stack route into blocked app
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}

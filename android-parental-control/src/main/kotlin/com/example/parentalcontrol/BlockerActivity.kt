package com.example.parentalcontrol

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * BlockerActivity
 *
 * Full-screen activity launched over a blocked app by ForegroundMonitorService.
 * Draws a plain lock screen so the child sees immediate feedback rather than
 * a confusing black screen.
 *
 * Design notes:
 *   • Back is suppressed — pressing Back cannot return to the blocked app.
 *   • The child navigates away via the Home button or by switching to an
 *     allowed app.  ForegroundMonitorService will not re-launch the blocker
 *     unless the blocked app comes back to the foreground.
 *   • FLAG_KEEP_SCREEN_ON keeps the display alive; the activity is not
 *     finishing itself, so the blocked app stays buried in the back stack.
 *
 * TODO: Replace the programmatic layout with an XML layout once the parent
 *       dashboard theme is finalised.  Add a PIN-gated "Unlock" button that
 *       calls finish() after the parent authenticates.
 */
class BlockerActivity : Activity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure this activity stays on top and the screen stays on.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "that app"

        // ── Programmatic layout ──────────────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        val icon = TextView(this).apply {
            text     = "🔒"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            gravity  = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text      = "App Blocked"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }

        val body = TextView(this).apply {
            text      = "Access to $blockedPkg\nhas been restricted by a parent."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#AAAACC"))
            gravity   = Gravity.CENTER
            setPadding(48, 0, 48, 0)
        }

        root.addView(icon)
        root.addView(title)
        root.addView(body)
        setContentView(root)
    }

    /** Suppress Back so the child cannot pop back into the blocked app. */
    @Deprecated("Required override for API < 33")
    override fun onBackPressed() {
        // Intentional no-op.
    }
}

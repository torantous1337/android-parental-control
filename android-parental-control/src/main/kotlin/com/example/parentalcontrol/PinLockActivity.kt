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
 * PinLockActivity
 *
 * Placeholder full-screen PIN gate.  Launched from:
 *   • AdminReceiver.onDisableRequested() — intercepts deactivation attempts
 *   • BlockerActivity "Unlock" button (once implemented)
 *
 * TODO: Implement PIN entry UI, hash comparison against a stored BCrypt hash,
 *       and call finish() on success.  On failure, record the attempt and
 *       notify the parent via FCM push.
 */
class PinLockActivity : Activity() {

    companion object {
        const val EXTRA_REASON       = "reason"
        const val REASON_ADMIN_DISABLE = "admin_disable"
        const val REASON_UNBLOCK       = "unblock"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_ADMIN_DISABLE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F3460"))
        }

        val icon = TextView(this).apply {
            text    = "🔑"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = when (reason) {
                REASON_ADMIN_DISABLE -> "Parent PIN Required"
                else                 -> "Enter PIN to Unlock"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }

        val sub = TextView(this).apply {
            text = "Contact a parent to enter the PIN."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#AAAACC"))
            gravity = Gravity.CENTER
        }

        root.addView(icon)
        root.addView(title)
        root.addView(sub)
        setContentView(root)

        // TODO: add PIN entry EditText + numpad + verify logic here.
    }

    @Deprecated("Required override for API < 33")
    override fun onBackPressed() {
        // Suppress — cannot back out of PIN screen.
    }
}

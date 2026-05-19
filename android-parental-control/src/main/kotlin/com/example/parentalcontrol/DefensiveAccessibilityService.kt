package com.example.parentalcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class DefensiveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DefensiveAccess"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // 1. Defend the Settings App & Package Installer
        if (packageName == "com.android.settings" || packageName == "com.google.android.packageinstaller") {
            val rootNode = rootInActiveWindow ?: return
            
            // Hunt for UI elements that threaten persistence
            if (findText(rootNode, "Force stop") || 
                findText(rootNode, "Uninstall") || 
                findText(rootNode, "Usage access") ||
                findText(rootNode, "VPN")) {
                
                Log.w(TAG, "Hostile UI interaction detected. Ejecting user.")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }

            // Intercept the Device Admin deactivation screen here, since Android 10+ 
            // blocks our DeviceAdminReceiver from launching activities in the background.
            // AccessibilityServices are exempt from background launch restrictions.
            if (findText(rootNode, "Deactivate this device admin app") || 
                findText(rootNode, "Remove work profile")) {
                
                Log.w(TAG, "Device Admin deactivation attempted. Launching PIN lock.")
                val pinIntent = Intent(this, PinLockActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(PinLockActivity.EXTRA_REASON, PinLockActivity.REASON_ADMIN_DISABLE)
                }
                startActivity(pinIntent)
                return
            }
        }

        // 2. Auto-Accept VPN Permission Dialogs
        if (packageName == "com.android.vpndialogs") {
            val rootNode = rootInActiveWindow ?: return
            if (clickNodeByText(rootNode, "OK") || clickNodeByText(rootNode, "Allow")) {
                Log.i(TAG, "VPN Dialog automatically accepted.")
            }
        }
    }

    // Recursively scans the view tree for target danger strings
    private fun findText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.contains(text, ignoreCase = true) == true) return true
        if (node.contentDescription?.contains(text, ignoreCase = true) == true) return true
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findText(child, text)) {
                child.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    // Recursively hunts for the target button and simulates a tap
    private fun clickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.equals(text, ignoreCase = true) == true && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && clickNodeByText(child, text)) {
                child.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.")
    }
}

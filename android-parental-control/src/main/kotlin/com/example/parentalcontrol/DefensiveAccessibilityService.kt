package com.example.parentalcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DefensiveAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        // 1. Defend the Settings App
        if (packageName == "com.android.settings" || packageName == "com.google.android.packageinstaller") {
            val rootNode = rootInActiveWindow ?: return
            
            // Hunt for danger strings. This kills attempts to Force Stop, Uninstall, or clear Usage Access.
            if (findText(rootNode, "Force stop") || 
                findText(rootNode, "Uninstall") || 
                findText(rootNode, "Usage access") ||
                findText(rootNode, "VPN")) {
                
                // Nuke the attempt by simulating the HOME button
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        // 2. Auto-Accept VPN Dialog (VpnService.prepare)
        if (packageName == "com.android.vpndialogs") {
            val rootNode = rootInActiveWindow ?: return
            if (clickNodeByText(rootNode, "OK") || clickNodeByText(rootNode, "Allow")) {
                // VPN accepted automatically
            }
        }
    }

    private fun findText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.contains(text, ignoreCase = true) == true) return true
        if (node.contentDescription?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findText(child, text)) return true
        }
        return false
    }

    private fun clickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.equals(text, ignoreCase = true) == true && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && clickNodeByText(child, text)) return true
        }
        return false
    }

    override fun onInterrupt() {}
}

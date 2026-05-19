package com.example.parentalcontrol

import android.app.Application
import android.util.Log

/**
 * Application entry point  —  v3
 *
 * Changes from v2:
 * • NativeBridge.verifyDexIntegrity() call REMOVED.
 *   APK Signature Scheme v2/v3 is enforced by the OS at install time and
 *   re-verified by the classloader on every attach.  The custom C++ SHA-256
 *   check was redundant on non-rooted devices and burned CPU/battery on every
 *   cold start without additional security benefit.
 *   anti_tamper.cpp and its JNI bridge are also removed from the build.
 */
class ParentalControlApp : Application() {

    companion object {
        private const val TAG = "PCApp"
    }

    override fun onCreate() {
        super.onCreate()

        AppContextHolder.init(this)
        NativeBridge.load()

        Log.i(TAG, "onCreate: native library loaded")
    }
}

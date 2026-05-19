package com.example.parentalcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * NotificationHelper
 *
 * Central factory for foreground-service notifications so every service
 * uses a consistent channel configuration and icon.
 */
object NotificationHelper {

    /**
     * Build a low-priority, silent notification suitable for a persistent
     * foreground service that should not annoy the user.
     *
     * Creates the [NotificationChannel] if it does not already exist.
     *
     * @param context     Application or service context.
     * @param channelId   Unique channel ID string.
     * @param title       Short notification title.
     * @param text        Secondary text line.
     */
    fun buildSilentNotification(
        context:   Context,
        channelId: String,
        title:     String,
        text:      String
    ): Notification {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(channelId)
            if (existing == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        title,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description  = text
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }
                )
            }
        }

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

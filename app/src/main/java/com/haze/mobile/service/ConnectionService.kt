package com.haze.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.haze.mobile.MainActivity
import com.haze.mobile.R

/**
 * Foreground service that keeps the app's process (and therefore its live Tor
 * chat connections) alive while Haze is in the background, and surfaces an
 * ongoing notification showing the session the user is connected to.
 *
 * The sockets themselves live in [com.haze.mobile.ui.ChatViewModel]; this
 * service exists purely to raise the process to foreground priority so Android
 * does not kill it (which is what was silently dropping sessions before).
 */
class ConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Connected"
        startForegroundCompat(buildNotification(text))
        // START_STICKY → the OS re-creates the service if it ever gets killed.
        return START_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPI = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Deliberately minimal: small icon + title + one short line, and NO
        // large icon / subtext / big style. With no expandable content the
        // system can't auto-expand it, so it stays a compact single card.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_haze)
            .setContentTitle("Haze Protocol")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentPI)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the Haze session you are connected to."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "haze_connection"
        private const val NOTIF_ID = 4201
        private const val EXTRA_TEXT = "text"

        /** Start or update the ongoing "connected" notification. */
        fun start(context: Context, text: String) {
            val i = Intent(context, ConnectionService::class.java)
                .putExtra(EXTRA_TEXT, text)
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        /** Tear the foreground service (and its notification) down. */
        fun stop(context: Context) {
            // stopService (not startForegroundService) avoids the "did not call
            // startForeground in time" crash; onDestroy removes the notification.
            runCatching { context.stopService(Intent(context, ConnectionService::class.java)) }
        }
    }
}

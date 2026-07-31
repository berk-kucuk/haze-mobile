package com.haze.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.haze.mobile.MainActivity
import com.haze.mobile.R

/**
 * Heads-up notifications for messages that arrive while Haze is backgrounded.
 * Separate from the ongoing [ConnectionService] notification so the two can be
 * toggled independently in Settings.
 */
object Notifier {

    private const val CHANNEL_ID = "haze_messages"
    private const val GROUP = "haze_message_group"
    private const val MSG_ID_BASE = 5000

    // Notification ids we've posted, so we can clear exactly those later.
    private val posted = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "New messages received in the background."
                    enableVibration(true)
                    setShowBadge(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Post a message notification. [sessionKey] keeps notifications from
     * different sessions separate; [sender] is the nick and [preview] the body.
     * [showContent] mirrors desktop's notifications_show_content setting —
     * when false, the sender/text never reach the notification at all (a
     * generic "New message" is shown instead), for anyone who doesn't want
     * chat content appearing in the heads-up/expanded notification while the
     * phone is unlocked (VISIBILITY_PRIVATE below only hides it on the lock
     * screen, not while the device is in use).
     */
    fun notifyMessage(
        context: Context,
        sessionKey: String,
        sender: String,
        preview: String,
        showContent: Boolean = true,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPI = PendingIntent.getActivity(
            context, sessionKey.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val logo = runCatching {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.haze_logo)
        }.getOrNull()

        val title = if (showContent) sender else "Haze"
        val body = if (showContent) preview else "New message"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_haze)
            .apply { if (logo != null) setLargeIcon(logo) }
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(GROUP)
            // Hide the content on the lock screen — only "new message" is shown there.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentPI)
            .build()

        val id = MSG_ID_BASE + (sessionKey.hashCode() and 0x3FF)
        posted.add(id)
        runCatching { NotificationManagerCompat.from(context).notify(id, notif) }
    }

    /** Clear all message notifications (e.g. when the user returns to the app). */
    fun cancelAll(context: Context) {
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        synchronized(posted) {
            posted.forEach { mgr.cancel(it) }
            posted.clear()
        }
    }
}

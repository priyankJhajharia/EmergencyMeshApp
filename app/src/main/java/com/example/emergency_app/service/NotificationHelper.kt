package com.example.emergency_app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.emergency_app.MainActivity

class NotificationHelper(private val context: Context) {

    private val MESSAGE_CHANNEL_ID = "MessageChannel"
    private val SOS_CHANNEL_ID = "SosChannel"
    private val SOS_RECEIVED_CHANNEL_ID = "SosReceivedChannel"
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // normal message channel
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New mesh messages"
            }

            // SOS sent channel — highest priority
            val sosChannel = NotificationChannel(
                SOS_CHANNEL_ID,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency SOS alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }

            // SOS RECEIVED channel — max priority with alarm
            val sosReceivedChannel = NotificationChannel(
                SOS_RECEIVED_CHANNEL_ID,
                "SOS Received",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SOS received from emergency contacts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setBypassDnd(true) // bypass Do Not Disturb
            }

            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(sosChannel)
            notificationManager.createNotificationChannel(sosReceivedChannel)
        }
    }

    // show normal message notification
    fun showMessageNotification(senderName: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Message from $senderName")
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // show SOS mesh notification
    fun showSosNotification(senderName: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🆘 SOS ALERT from $senderName")
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setLights(android.graphics.Color.RED, 500, 500)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // show SOS RECEIVED notification with Open Maps button
    fun showSosReceivedNotification(
        senderName: String,
        content: String,
        mapsLink: String?
    ) {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context, 2, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, SOS_RECEIVED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🆘 SOS from $senderName!")
            .setContentText("Emergency contact needs help! Tap Open Maps for location.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setLights(android.graphics.Color.RED, 500, 500)
            .setColor(android.graphics.Color.RED)
            .setColorized(true)

        // add Open Maps button if location available
        if (mapsLink != null) {
            val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val mapsPendingIntent = PendingIntent.getActivity(
                context, 3, mapsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_dialog_map,
                "📍 Open Maps",
                mapsPendingIntent
            )
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
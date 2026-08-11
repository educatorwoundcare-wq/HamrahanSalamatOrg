package com.example.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationGenerator {

    private const val CHANNEL_ID = "hamrahan_salamat_alerts"
    private const val CHANNEL_NAME = "هشدارهای هوشمند سامانه"
    private const val CHANNEL_DESC = "کانال ارسال هشدارهای هوشمند یکپارچگی مالی، نقدینگی و هماهنگی پزشکان و پرستاران"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission")
    fun triggerNotification(context: Context, id: Int, title: String, text: String, screen: String? = null) {
        try {
            createNotificationChannel(context)

            // Dynamic Intent to return back to MainActivity
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (screen != null) {
                    putExtra("ROUTE_TARGET", screen)
                }
            } ?: Intent()

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(context, id, intent, pendingIntentFlags)

            // Let's check post notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.w("NotificationGenerator", "POST_NOTIFICATIONS permission not granted. Skipping system tray notification.")
                    return
                }
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat) // Standard safe platform fallback icon
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(id, builder.build())
            android.util.Log.i("NotificationGenerator", "Triggered system notification [$id] '$title' successfully.")
        } catch (e: Exception) {
            android.util.Log.e("NotificationGenerator", "Failed to trigger system notification: ${e.localizedMessage}", e)
        }
    }
}

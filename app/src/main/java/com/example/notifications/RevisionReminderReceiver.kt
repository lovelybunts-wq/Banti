package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class RevisionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val summary = intent?.getStringExtra(EXTRA_YESTERDAY_SUMMARY)
            ?.takeIf { it.isNotBlank() }
            ?: "अपने कल पढ़े हुए टॉपिक्स का रिवीजन करें!"
        showRevisionNotification(context, summary)
    }

    companion object {
        const val CHANNEL_ID = "learn_talent_revision_channel"
        const val EXTRA_YESTERDAY_SUMMARY = "extra_yesterday_summary"
        private const val NOTIFICATION_ID = 2026

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Learn Talent Daily Revision",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminds you what you studied yesterday and today's revision plan."
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }

        fun showRevisionNotification(context: Context, yesterdaySummary: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            ensureChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Learn Talent • कल तुमने यह पढ़ा था")
                .setContentText(yesterdaySummary)
                .setStyle(NotificationCompat.BigTextStyle().bigText("कल तुमने यह पढ़ा था:\n$yesterdaySummary"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

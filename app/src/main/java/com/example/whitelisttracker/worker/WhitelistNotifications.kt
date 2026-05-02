package me.nonick.whitelisttracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.nonick.whitelisttracker.MainActivity
import me.nonick.whitelisttracker.R
import me.nonick.whitelisttracker.data.formatDurationMillis
import me.nonick.whitelisttracker.data.formatTimestamp

object WhitelistNotifications {
  private const val CHANNEL_ID = "whitelist_tracker_status_silent"
  private const val PROGRESS_CHANNEL_ID = "whitelist_tracker_progress_silent"
  private const val ACTIVE_NOTIFICATION_ID = 1001
  private const val ENDED_NOTIFICATION_ID = 1002
  private const val CHECK_PROGRESS_NOTIFICATION_ID = 1003

  fun showCheckProgress(context: Context, checkedCount: Int, totalCount: Int, label: String) {
    if (!canPostNotifications(context)) return
    ensureProgressChannel(context)

    val notification =
      NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Проверка доступности URL")
        .setContentText(label)
        .setContentIntent(contentIntent(context))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(totalCount, checkedCount, totalCount <= 0)
        .build()

    NotificationManagerCompat.from(context).notify(CHECK_PROGRESS_NOTIFICATION_ID, notification)
  }

  fun showActive(context: Context, startedAtMillis: Long) {
    if (!canPostNotifications(context)) return
    ensureChannel(context)

    val durationMillis = System.currentTimeMillis() - startedAtMillis
    val description = whitelistDescription(startedAtMillis, durationMillis)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Обнаружен режим белых списков")
        .setContentText(description)
        .setStyle(NotificationCompat.BigTextStyle().bigText(description))
        .setContentIntent(contentIntent(context))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    NotificationManagerCompat.from(context).notify(ACTIVE_NOTIFICATION_ID, notification)
  }

  fun showEnded(context: Context, startedAtMillis: Long, durationMillis: Long) {
    NotificationManagerCompat.from(context).cancel(ACTIVE_NOTIFICATION_ID)
    if (!canPostNotifications(context)) return
    ensureChannel(context)

    val description = whitelistDescription(startedAtMillis, durationMillis)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Режим белых списков был завершен")
        .setContentText(description)
        .setStyle(NotificationCompat.BigTextStyle().bigText(description))
        .setContentIntent(contentIntent(context))
        .setOngoing(false)
        .setAutoCancel(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(ENDED_NOTIFICATION_ID, notification)
  }

  fun cancelActive(context: Context) {
    NotificationManagerCompat.from(context).cancel(ACTIVE_NOTIFICATION_ID)
  }

  fun cancelCheckProgress(context: Context) {
    NotificationManagerCompat.from(context).cancel(CHECK_PROGRESS_NOTIFICATION_ID)
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Whitelist Tracker",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Статус режима белых списков"
        setSound(null, null)
      }

    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun ensureProgressChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel =
      NotificationChannel(
        PROGRESS_CHANNEL_ID,
        "Whitelist Tracker progress",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Ход текущей проверки URL"
        setSound(null, null)
      }

    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun contentIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun whitelistDescription(startedAtMillis: Long, durationMillis: Long): String =
    "Время начала: ${formatTimestamp(startedAtMillis)}\nДлительность: ${formatDurationMillis(durationMillis)}"

  private fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

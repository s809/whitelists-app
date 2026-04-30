package me.nonick.whitelisttracker.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.nonick.whitelisttracker.data.TrackerSettings
import me.nonick.whitelisttracker.data.sanitizeIntervalMinutes
import java.util.concurrent.TimeUnit

object WhitelistWorkScheduler {
  const val CHECK_WORK_TAG = "whitelist_tracker_check"

  private const val PERIODIC_WORK_NAME = "whitelist_tracker_periodic_check"

  fun schedule(context: Context, settings: TrackerSettings) {
    val workManager = WorkManager.getInstance(context)
    if (!settings.monitoringEnabled) {
      workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
      WhitelistNotifications.cancelCheckProgress(context)
      WhitelistNotifications.cancelActive(context)
      return
    }

    val request =
      PeriodicWorkRequestBuilder<WhitelistCheckWorker>(
          sanitizeIntervalMinutes(settings.intervalMinutes).toLong(),
          TimeUnit.MINUTES,
        )
        .addTag(CHECK_WORK_TAG)
        .setConstraints(networkConstraints())
        .build()

    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
  }

  fun runNow(context: Context) {
    val request =
      OneTimeWorkRequestBuilder<WhitelistCheckWorker>()
        .addTag(CHECK_WORK_TAG)
        .setConstraints(networkConstraints())
        .build()
    WorkManager.getInstance(context).enqueue(request)
  }

  private fun networkConstraints(): Constraints =
    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
